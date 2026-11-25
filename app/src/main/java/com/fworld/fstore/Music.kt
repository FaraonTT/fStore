@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fworld.fstore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Форматы, которые можно загрузить. */
val MUSIC_EXT = listOf("mp3", "flac", "wav", "aac", "m4a", "ogg")

/** Максимальный размер песни (хранится частями по 25 МБ, как и APK). */
const val MAX_MUSIC = 100L * 1024 * 1024

data class Track(
    val id: String,
    val owner: String,
    val title: String,
    val artist: String,
    val ext: String,
    val file: String,
    val cover: String,
    val size: Long,
    val parts: Int,
    val created: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("owner", owner).put("title", title).put("artist", artist)
        .put("ext", ext).put("file", file).put("cover", cover)
        .put("size", size).put("parts", parts).put("created", created)

    companion object {
        fun from(o: JSONObject) = Track(
            o.optString("id"), o.optString("owner"), o.optString("title"), o.optString("artist"),
            o.optString("ext", "mp3"), o.optString("file"), o.optString("cover"),
            o.optLong("size"), o.optInt("parts", 1).coerceAtLeast(1), o.optLong("created"),
        )
    }
}

/** Каталог песен (data/music.json) и загрузка новых. Живёт отдельно от StoreVM. */
object MusicStore {
    var tracks by mutableStateOf(listOf<Track>()); private set
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var stage by mutableStateOf(""); private set
    var progress by mutableStateOf(0f); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun load() {
        if (loading) return
        scope.launch {
            loading = true
            try {
                tracks = Gh.readArray("data/music.json").objs().map { Track.from(it) }
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    private suspend fun cleanup(vararg paths: String) {
        for (p in paths) {
            if (p.isBlank()) continue
            try { Gh.delete(p, "cleanup") } catch (_: Exception) { }
        }
    }

    fun submit(
        me: User, title: String, artist: String, cover: ByteArray?, file: File, ext: String,
        onMsg: (String) -> Unit, onOk: () -> Unit,
    ) {
        if (!Gh.ready) { onMsg("Сервер не настроен: в сборке нет токена FSTORE_TOKEN"); return }
        scope.launch {
            busy = true
            progress = 0f
            stage = "Загружаю обложку…"
            var coverPath = ""
            val done = mutableListOf<String>()
            try {
                require(title.isNotBlank()) { "Введите имя песни" }
                val total = file.length()
                require(total in 1..MAX_MUSIC) { "Файл больше 100 МБ" }
                val id = slug(title) + "-" + System.currentTimeMillis().toString(36)
                val dir = "music/${me.username}"
                val path = "$dir/$id.$ext"
                if (cover != null) {
                    coverPath = "$dir/$id.png"
                    Gh.overwrite(coverPath, cover, "cover $id")
                }
                val parts = apkPartCount(total)
                val names = apkParts(path, parts)
                names.forEachIndexed { i, p ->
                    stage = if (parts > 1) "Загружаю песню… часть ${i + 1} из $parts" else "Загружаю песню…"
                    val off = i * APK_PART
                    val len = minOf(APK_PART, total - off)
                    Gh.putFile(p, file, "music $id part ${i + 1}/$parts", off, len) { progress = (i + it) / parts }
                    done += p
                }
                val t = Track(id, me.username, title.trim(), artist.trim(), ext, path, coverPath, total, parts, System.currentTimeMillis())
                Gh.updateJson("data/music.json", "new track $id") { arr -> arr.put(t.toJson()); arr }
                file.delete()
                tracks = Gh.readArray("data/music.json").objs().map { Track.from(it) }
                onOk()
            } catch (e: Exception) {
                cleanup(coverPath, *done.toTypedArray())
                onMsg(e.message ?: "Ошибка загрузки")
            } finally {
                busy = false
            }
        }
    }

    fun delete(me: User, t: Track, onMsg: (String) -> Unit) {
        scope.launch {
            try {
                require(me.username == t.owner || me.isAdmin) { "Нет прав" }
                Gh.updateJson("data/music.json", "delete track ${t.id}") { arr ->
                    arr.objs().filter { it.optString("id") != t.id }.toJsonArray()
                }
                if (MusicPlayer.current?.id == t.id) MusicPlayer.stop()
                cleanup(*apkParts(t.file, t.parts).toTypedArray(), t.cover)
                tracks = tracks.filter { it.id != t.id }
            } catch (e: Exception) {
                onMsg(e.message ?: "Ошибка")
            }
        }
    }
}

/** Плеер: песня сначала докачивается в кэш (по частям), потом играет MediaPlayer. */
object MusicPlayer {
    var current by mutableStateOf<Track?>(null); private set
    var playing by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var loadProgress by mutableStateOf(0f); private set
    var duration by mutableStateOf(0); private set
    var position by mutableStateOf(0); private set
    var error by mutableStateOf<String?>(null)

    private var mp: MediaPlayer? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun toggle(ctx: Context, t: Track) {
        val p = mp
        if (current?.id == t.id && p != null) {
            if (playing) { p.pause(); playing = false } else { p.start(); playing = true }
            return
        }
        if (current?.id == t.id && loading) return
        play(ctx.applicationContext, t)
    }

    private fun play(ctx: Context, t: Track) {
        job?.cancel()
        release()
        current = t; loading = true; loadProgress = 0f; error = null; position = 0; duration = 0
        job = scope.launch {
            try {
                val dir = File(ctx.cacheDir, "music").apply { mkdirs() }
                val f = File(dir, "${t.id}.${t.ext}")
                if (!f.exists() || f.length() != t.size) {
                    f.delete()
                    val tmp = File(dir, "${t.id}.part")
                    Gh.download(apkParts(t.file, t.parts), tmp, t.size) { loadProgress = it }
                    tmp.renameTo(f)
                }
                val p = MediaPlayer()
                p.setDataSource(f.path)
                withContext(Dispatchers.IO) { p.prepare() }
                p.setOnCompletionListener { playing = false; position = 0; it.seekTo(0) }
                p.start()
                mp = p
                duration = p.duration
                playing = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Не удалось воспроизвести: ${e.message}"
                current = null
            } finally {
                loading = false
            }
        }
    }

    fun tick() {
        val p = mp ?: return
        if (playing) position = p.currentPosition
    }

    fun seek(ms: Int) {
        mp?.seekTo(ms)
        position = ms
    }

    fun stop() {
        job?.cancel()
        release()
        current = null; loading = false
    }

    private fun release() {
        runCatching { mp?.release() }
        mp = null; playing = false; position = 0; duration = 0
    }
}

// ---------- метаданные файла ----------

class TrackMeta(val title: String?, val artist: String?, val cover: ByteArray?)

/** Обложка из тегов файла → PNG не больше 600 px. */
fun toCoverPng(raw: ByteArray, max: Int = 600): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= max && bounds.outHeight / (sample * 2) >= max) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val scale = max.toFloat() / maxOf(bmp.width, bmp.height)
    val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    val bos = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.PNG, 100, bos)
    return bos.toByteArray()
}

fun readTrackMeta(f: File): TrackMeta {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(f.path)
        TrackMeta(
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
            r.embeddedPicture?.let { toCoverPng(it) },
        )
    } catch (_: Exception) {
        TrackMeta(null, null, null)
    } finally {
        runCatching { r.release() }
    }
}

private class PickedMusic(val file: File, val ext: String, val displayName: String)

private suspend fun copyMusic(ctx: Context, uri: Uri): Result<PickedMusic> = withContext(Dispatchers.IO) {
    runCatching {
        var name = "song"
        var size = -1L
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        require(ext in MUSIC_EXT) { "Нужен файл: ${MUSIC_EXT.joinToString(", ")}" }
        require(size <= MAX_MUSIC) { "Файл больше 100 МБ" }
        if (size > 0) require(ctx.cacheDir.usableSpace > size + 50L * 1024 * 1024) { "Недостаточно свободного места на устройстве" }
        val f = File(ctx.cacheDir, "upload-music.$ext")
        var total = 0L
        ctx.contentResolver.openInputStream(uri)!!.use { i ->
            f.outputStream().use { o ->
                val b = ByteArray(65536)
                while (true) {
                    val n = i.read(b)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_MUSIC) { "Файл больше 100 МБ" }
                    o.write(b, 0, n)
                }
            }
        }
        PickedMusic(f, ext, name.substringBeforeLast('.'))
    }
}

// ---------- интерфейс ----------

private fun fmtTime(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Обложка: картинка из репозитория, а если её нет — векторный значок ноты. */
@Composable
fun TrackCover(t: Track, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (t.cover.isNotBlank()) {
            AsyncImage(
                model = Gh.rawUrl(t.cover, t.created),
                contentDescription = t.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.MusicNote, null,
                Modifier.size(size * 0.5f),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun MusicScreen(vm: StoreVM) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(Unit) { MusicStore.load() }
    LaunchedEffect(MusicPlayer.error) {
        MusicPlayer.error?.let { vm.message = it; MusicPlayer.error = null }
    }
    val me = vm.me
    val list = remember(MusicStore.tracks, query) {
        val q = query.trim().lowercase()
        MusicStore.tracks
            .filter { q.isBlank() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.owner.lowercase().contains(q) }
            .sortedByDescending { it.created }
    }
    val cur = MusicPlayer.current

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.padding(start = 16.dp, end = 72.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Музыка", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Песни от пользователей fStore",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Поиск песен", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Очистить") }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Box(Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = MusicStore.loading,
                onRefresh = { MusicStore.load() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(
                            if (MusicStore.loading) "Загружаю…"
                            else "Песен пока нет. Загрузить свою можно во вкладке «Загрузить» → «Музыка».",
                            Modifier.fillMaxWidth().padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp,
                            bottom = 16.dp + LocalBottomPad.current + if (cur != null) 112.dp else 0.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(list, key = { it.id }) { t ->
                            val isCur = cur?.id == t.id
                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCur) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { MusicPlayer.toggle(ctx, t) },
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TrackCover(t, 56.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(t.artist.takeIf { it.isNotBlank() }, "@${t.owner}", t.ext.uppercase(), fmtSize(t.size)).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (isCur && MusicPlayer.loading) {
                                        CircularProgressIndicator(progress = { MusicPlayer.loadProgress }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                                        Spacer(Modifier.width(8.dp))
                                    } else {
                                        IconButton(onClick = { MusicPlayer.toggle(ctx, t) }) {
                                            Icon(
                                                if (isCur && MusicPlayer.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                if (isCur && MusicPlayer.playing) "Пауза" else "Играть",
                                            )
                                        }
                                    }
                                    if (me != null && (me.username == t.owner || me.isAdmin)) {
                                        IconButton(onClick = { confirm = t }) {
                                            Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (cur != null) {
                MiniPlayer(
                    cur,
                    Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = LocalBottomPad.current + 8.dp),
                )
            }
        }
    }

    confirm?.let { t ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Удалить песню?") },
            text = { Text("«${t.title}» исчезнет из каталога.") },
            confirmButton = {
                TextButton(onClick = { confirm = null; me?.let { MusicStore.delete(it, t) { m -> vm.message = m } } }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun MiniPlayer(t: Track, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(MusicPlayer.playing) {
        while (MusicPlayer.playing) {
            MusicPlayer.tick()
            delay(400)
        }
    }
    val ctx = LocalContext.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cs.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackCover(t, 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (MusicPlayer.loading) "Загружаю… ${(MusicPlayer.loadProgress * 100).toInt()}%"
                        else t.artist.ifBlank { "@${t.owner}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { MusicPlayer.toggle(ctx, t) }, enabled = !MusicPlayer.loading) {
                    Icon(if (MusicPlayer.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Пауза/играть")
                }
                IconButton(onClick = { MusicPlayer.stop() }) { Icon(Icons.Default.Close, "Остановить") }
            }
            if (MusicPlayer.loading) {
                LinearProgressIndicator(progress = { MusicPlayer.loadProgress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            } else {
                val dur = MusicPlayer.duration.coerceAtLeast(1)
                Slider(
                    value = drag ?: (MusicPlayer.position.toFloat() / dur),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { MusicPlayer.seek((it * dur).toInt()) }; drag = null },
                    modifier = Modifier.height(28.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtTime(drag?.let { (it * dur).toInt() } ?: MusicPlayer.position), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Text(fmtTime(MusicPlayer.duration), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** Переключатель «Приложение / Музыка» вверху экрана загрузки. */
@Composable
fun KindSwitch(kind: Int, onKind: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = kind == 0, onClick = { onKind(0) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Приложение") }
        SegmentedButton(selected = kind == 1, onClick = { onKind(1) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Музыка") }
    }
}

/** Форма загрузки песни: файл, имя, обложка (своя → из файла → значок). */
@Composable
fun MusicUploadScreen(vm: StoreVM, kind: Int, onKind: (Int) -> Unit) {
    val me = vm.me ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var cover by remember { mutableStateOf<ByteArray?>(null) }
    var embedded by remember { mutableStateOf<ByteArray?>(null) }
    var picked by remember { mutableStateOf<PickedMusic?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            copyMusic(ctx, uri)
                .onSuccess { p ->
                    picked = p; error = null
                    val meta = withContext(Dispatchers.IO) { readTrackMeta(p.file) }
                    embedded = meta.cover
                    title = meta.title ?: p.displayName
                    if (artist.isBlank()) artist = meta.artist ?: ""
                }
                .onFailure { picked = null; error = it.message ?: "Не удалось прочитать файл" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomPad.current),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Загрузить", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.heightIn(min = 64.dp))
        KindSwitch(kind, onKind)

        OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.MusicNote, null)
            Spacer(Modifier.width(8.dp))
            Text(picked?.let { "${it.displayName}.${it.ext} · ${fmtSize(it.file.length())}" } ?: "Выбрать файл (${MUSIC_EXT.joinToString(", ")})")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedTextField(
            value = title, onValueChange = { title = it }, label = { Text("Имя песни") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = artist, onValueChange = { artist = it }, label = { Text("Исполнитель (необязательно)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ImagePicker(cover ?: embedded, { cover = it }, 88.dp, round = false, label = "Иконка")
            Text(
                when {
                    cover != null -> "Иконка песни: твоя картинка"
                    embedded != null -> "Иконка песни: взята из файла. Нажми, чтобы заменить"
                    else -> "Иконка песни (необязательно). Если не выбрать и в файле её нет — будет значок ноты"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (MusicStore.busy) {
            Text(MusicStore.stage, style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { MusicStore.progress }, modifier = Modifier.fillMaxWidth())
        } else {
            Button(
                enabled = picked != null && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val p = picked!!
                    MusicStore.submit(me, title, artist, cover ?: embedded, p.file, p.ext, { vm.message = it }) {
                        title = ""; artist = ""; cover = null; embedded = null; picked = null
                        vm.message = "Песня загружена"
                    }
                },
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Загрузить песню")
            }
        }
    }
}
