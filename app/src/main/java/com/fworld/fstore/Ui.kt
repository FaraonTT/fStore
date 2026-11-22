@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

const val MAX_APK = 100L * 1024 * 1024

fun fmtSize(b: Long): String =
    if (b >= 1048576) String.format("%.1f МБ", b / 1048576.0) else String.format("%d КБ", b / 1024)

fun fmtDate(t: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(t))

fun hexColor(s: String): Color? {
    if (s.isBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(if (s.startsWith("#")) s else "#$s"))
    } catch (e: Exception) {
        null
    }
}

/** Center-crops a picked image to a square PNG of [max] px. */
fun loadImage(ctx: Context, uri: Uri, max: Int = 256): ByteArray? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var s = 1
        while (opts.outWidth / s > max * 2 || opts.outHeight / s > max * 2) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            ?: return null
        val side = minOf(bmp.width, bmp.height)
        val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(sq, max, max, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun Avatar(u: User?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (u != null && u.avatar.isNotBlank()) {
            AsyncImage(
                model = Gh.rawUrl(u.avatar, u.updated),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                (u?.name?.firstOrNull() ?: '?').uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun AppIcon(a: AppItem, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (a.icon.isNotBlank()) {
            AsyncImage(
                model = Gh.rawUrl(a.icon, a.updated),
                contentDescription = a.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Переливающийся ник: градиент из двух цветов бежит по тексту, подсветка плавно меняет цвет. */
@Composable
private fun ShimmerText(text: String, style: TextStyle, a: Color, b: Color) {
    val moving = LocalAnim.current
    val tr = rememberInfiniteTransition(label = "shimmer")
    val shift by tr.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "shift",
    )
    val pulse by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val x = if (moving) shift else 0f
    val brush = Brush.linearGradient(
        colors = listOf(a, b),
        start = Offset(x, 0f),
        end = Offset(x + 180f, 0f),
        tileMode = TileMode.Mirror,
    )
    val glow = lerp(a, b, if (moving) pulse else 0.5f)
    Text(
        text,
        style = style.copy(brush = brush, shadow = Shadow(glow, Offset.Zero, 24f)),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun NameLine(
    u: User?,
    fallback: String = "",
    style: TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier,
) {
    // Клик по имени открывает страницу пользователя (если экран это разрешает).
    val open = LocalOpenUser.current
    val un = u?.username ?: ""
    val link = if (open != null && un.isNotBlank()) Modifier.clickable { open(un) } else Modifier
    if (u == null) {
        Text(fallback, style = style, modifier = modifier)
        return
    }
    val base = hexColor(u.nameColor) ?: MaterialTheme.colorScheme.onSurface
    val glow = hexColor(u.glowColor) ?: base
    val sa = if (u.shimmer) hexColor(u.shimmerA) else null
    val sb = if (u.shimmer) hexColor(u.shimmerB) else null
    Row(modifier.then(link), verticalAlignment = Alignment.CenterVertically) {
        if (sa != null && sb != null) {
            ShimmerText(u.name, style, sa, sb)
        } else {
            Text(
                u.name,
                color = base,
                style = if (u.glow) style.copy(shadow = Shadow(glow, Offset.Zero, 24f)) else style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (u.verified) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Verified, "Верифицирован", tint = Color(0xFF1D9BF0), modifier = Modifier.size(18.dp))
        }
    }
}

// ---------- Векторные иконки бейджей (вместо эмодзи) ----------

val BADGE_ICONS: Map<String, ImageVector> = mapOf(
    "premium" to Icons.Default.WorkspacePremium,
    "shield" to Icons.Default.Shield,
    "search" to Icons.Default.Search,
    "heart" to Icons.Default.Favorite,
    "bug" to Icons.Default.BugReport,
    "spark" to Icons.Default.AutoAwesome,
    "verified" to Icons.Default.Verified,
    "star" to Icons.Default.Star,
    "bolt" to Icons.Default.Bolt,
    "fire" to Icons.Default.LocalFireDepartment,
    "diamond" to Icons.Default.Diamond,
    "rocket" to Icons.Default.RocketLaunch,
    "music" to Icons.Default.MusicNote,
    "game" to Icons.Default.SportsEsports,
    "code" to Icons.Default.Code,
    "trophy" to Icons.Default.EmojiEvents,
    "flag" to Icons.Default.Flag,
    "pets" to Icons.Default.Pets,
    "eco" to Icons.Default.Eco,
)

/** Иконки, которые можно выбрать при создании бейджа. */
val PICKABLE_ICONS = listOf(
    "star", "bolt", "fire", "diamond", "rocket", "music", "game", "code",
    "trophy", "flag", "pets", "eco", "heart", "spark", "shield",
)

/** Неизвестный ключ (в том числе старый эмодзи-бейдж) показывается звездой. */
fun badgeIcon(key: String): ImageVector = BADGE_ICONS[key] ?: Icons.Default.Star

@Composable
private fun badgeTint(key: String): Color = when (key) {
    "premium" -> Color(0xFFFFB300)
    "shield" -> Color(0xFF2196F3)
    "search" -> Color(0xFF00BCD4)
    "heart" -> Color(0xFFE91E63)
    "bug" -> Color(0xFFE53935)
    "verified" -> Color(0xFF1D9BF0)
    "fire" -> Color(0xFFFF6D00)
    "bolt" -> Color(0xFFFFC107)
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun BadgeGlyph(b: BadgeDef, size: Dp) {
    when (b.kind) {
        "color" -> Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(hexColor(b.value) ?: MaterialTheme.colorScheme.primary),
        )
        "image" -> AsyncImage(
            model = Gh.rawUrl(b.value),
            contentDescription = b.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
        else -> {
            val key = if (b.kind == "icon") b.value else "star"
            Icon(badgeIcon(key), b.name, Modifier.size(size), tint = badgeTint(key))
        }
    }
}

@Composable
fun BadgeChip(b: BadgeDef, onClick: () -> Unit) {
    val tint = if (b.kind == "color") hexColor(b.value) else null
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tint?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BadgeGlyph(b, 16.dp)
            Text(b.name, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun BadgeRow(list: List<BadgeDef>) {
    if (list.isEmpty()) return
    var open by remember { mutableStateOf<BadgeDef?>(null) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        list.forEach { b -> BadgeChip(b) { open = b } }
    }
    open?.let { b ->
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Ок") } },
            icon = { BadgeGlyph(b, 28.dp) },
            title = { Text(b.name) },
            text = { Text(b.desc.ifBlank { "Без описания" }) },
        )
    }
}

@Composable
fun Tag(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Плашка «#fworld» с переливающимся чёрно-фиолетовым градиентом. */
@Composable
fun FworldTag() {
    Row(
        Modifier
            .clip(CircleShape)
            .background(fworldBrush(260f, 80f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(
            "#fworld",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

@Composable
fun StatusTag(status: String) {
    val cs = MaterialTheme.colorScheme
    when (status) {
        "approved" -> Tag("Проверено", cs.primaryContainer, cs.onPrimaryContainer)
        "rejected" -> Tag("Отклонено", cs.errorContainer, cs.onErrorContainer)
        else -> Tag("Не проверено", cs.tertiaryContainer, cs.onTertiaryContainer)
    }
}

@Composable
fun AppCard(a: AppItem, onClick: () -> Unit) {
    val compact = UiPrefs.compactCards
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && LocalAnim.current) 0.97f else 1f, label = "press")
    val openUser = LocalOpenUser.current
    val cardShape = RoundedCornerShape(if (compact) 20.dp else 28.dp)
    Card(
        onClick = onClick,
        interactionSource = src,
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (a.fworld) Modifier.fworldGlow(cardShape) else Modifier),
    ) {
        Row(Modifier.padding(if (compact) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a, if (compact) 48.dp else 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    Text(
                        "v${a.version}" + (if (UiPrefs.showSize) " · ${fmtSize(a.size)}" else "") + " · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Клик по @нику открывает профиль разработчика.
                    Text(
                        "@${a.owner}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (openUser != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (openUser != null) Modifier.clickable { openUser(a.owner) } else Modifier,
                    )
                }
                if (!compact && UiPrefs.showDesc && a.shortDesc.isNotBlank()) {
                    Text(
                        a.shortDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (a.fworld) FworldTag()
                    StatusTag(a.status)
                }
            }
        }
    }
}

@Composable
fun StarsRow(value: Int, size: Dp = 20.dp, onSet: ((Int) -> Unit)? = null) {
    Row {
        for (i in 1..5) {
            val ic = if (i <= value) Icons.Default.Star else Icons.Default.StarBorder
            val m = if (onSet != null) Modifier.size(size).clickable { onSet(i) } else Modifier.size(size)
            Icon(ic, null, m, tint = Color(0xFFFFB300))
        }
    }
}

@Composable
fun ImagePicker(
    bytes: ByteArray?,
    onPick: (ByteArray) -> Unit,
    size: Dp = 96.dp,
    round: Boolean = true,
    label: String = "Фото",
    placeholder: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(ctx, uri)?.let(onPick)
    }
    val bmp = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(
        Modifier
            .size(size)
            .clip(if (round) CircleShape else RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { launcher.launch("image/*") },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (placeholder != null) {
            placeholder()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddAPhoto, null)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ---------- Палитра с цветовым кругом ----------

@Composable
private fun ColorWheel(hue: Float, sat: Float, v: Float, onPick: (Float, Float) -> Unit) {
    var sz by remember { mutableStateOf(IntSize.Zero) }
    val cb by rememberUpdatedState(onPick)
    val hues = remember { (0..360 step 30).map { Color.hsv((it % 360).toFloat(), 1f, 1f) } }

    fun pick(p: Offset) {
        val r = minOf(sz.width, sz.height) / 2f
        if (r <= 0f) return
        val dx = p.x - sz.width / 2f
        val dy = p.y - sz.height / 2f
        val h = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
        cb(h, (hypot(dx, dy) / r).coerceIn(0f, 1f))
    }

    Canvas(
        Modifier
            .size(220.dp)
            .onSizeChanged { sz = it }
            .pointerInput(Unit) { detectTapGestures { pick(it) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pick(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        pick(change.position)
                    },
                )
            },
    ) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(brush = Brush.sweepGradient(hues, center = c), radius = r, center = c)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = c, radius = r),
            radius = r,
            center = c,
        )
        drawCircle(color = Color.Black.copy(alpha = 1f - v), radius = r, center = c)
        val ang = Math.toRadians(hue.toDouble())
        val d = sat * r
        val pt = Offset(c.x + (cos(ang) * d).toFloat(), c.y + (sin(ang) * d).toFloat())
        drawCircle(color = Color.White, radius = 12.dp.toPx(), center = pt, style = Stroke(3.dp.toPx()))
        drawCircle(color = Color.hsv(hue, sat, v), radius = 9.dp.toPx(), center = pt)
    }
}

/** Выбор цвета кругом (оттенок + насыщенность) и ползунком яркости. Пустая строка — «цвет не выбран». */
@Composable
fun ColorPicker(label: String, value: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cur = hexColor(value)
    var open by remember { mutableStateOf(false) }
    val seed = remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV((cur ?: Color(0xFF9C27B0)).toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableStateOf(seed[0]) }
    var sat by remember { mutableStateOf(seed[1]) }
    var v by remember { mutableStateOf(seed[2]) }
    val emit by rememberUpdatedState(onChange)

    fun push() {
        emit("#%06X".format(Color.hsv(hue, sat, v).toArgb() and 0xFFFFFF))
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { open = !open }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(cur ?: Color.Transparent)
                    .border(2.dp, if (cur != null) cs.primary else cs.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (cur == null) Icon(Icons.Default.Close, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (cur == null) "Не выбран" else "Выбран",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            if (cur != null) TextButton(onClick = { onChange("") }) { Text("Сбросить") }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        AnimatedVisibility(open) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorWheel(hue, sat, v) { h, s ->
                    hue = h
                    sat = s
                    push()
                }
                Text("Яркость", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Slider(
                    value = v,
                    onValueChange = {
                        v = it
                        push()
                    },
                    valueRange = 0.15f..1f,
                )
            }
        }
    }
}
