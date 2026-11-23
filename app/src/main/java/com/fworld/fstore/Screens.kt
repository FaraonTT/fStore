@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Варианты сортировки каталога (настройка «Сортировка» в «Главная»). */
private val SORTS = listOf("Сначала новые", "По названию", "По размеру", "По рейтингу")

/** Вкладки каталога. Выбор живёт в плавающей панели над доком (см. MainActivity). */
val HOME_TABS = listOf("#fworld", "Проверенные", "Все", "Непроверенные")

@Composable
fun HomeScreen(vm: StoreVM, sub: Int, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var egg by remember { mutableStateOf(false) }
    val tap = remember { TapCounter() }

    val ratings = remember(vm.reviews) {
        vm.reviews.groupBy { it.appId }.mapValues { e -> e.value.sumOf { it.stars }.toDouble() / e.value.size }
    }

    val list = remember(vm.apps, sub, query, favOnly, Favorites.ids, AppPrefs.sort, ratings) {
        val q = query.trim().lowercase()
        val base = vm.apps.filter {
            // Приложения с бейджем #fworld живут только во вкладке #fworld.
            val inTab = when (sub) {
                0 -> it.fworld && it.status != "rejected"
                1 -> !it.fworld && it.status == "approved"
                2 -> !it.fworld && (it.status == "approved" || it.status == "pending")
                else -> !it.fworld && it.status == "pending"
            }
            val okFav = !favOnly || it.id in Favorites.ids
            val okQuery = q.isBlank() ||
                it.name.lowercase().contains(q) ||
                it.shortDesc.lowercase().contains(q) ||
                it.desc.lowercase().contains(q) ||
                it.owner.lowercase().contains(q)
            inTab && okFav && okQuery
        }
        when (AppPrefs.sort) {
            1 -> base.sortedBy { it.name.lowercase() }
            2 -> base.sortedByDescending { it.size }
            3 -> base.sortedByDescending { ratings[it.id] ?: 0.0 }
            else -> base.sortedWith(
                compareByDescending<AppItem> { it.fworld }.thenByDescending { it.updated },
            )
        }
    }

    val recent = remember(vm.apps, Recents.ids) {
        Recents.ids.mapNotNull { id -> vm.apps.find { it.id == id } }.take(8)
    }

    // Пасхалка: семь быстрых тапов по шапке.
    if (egg) EasterEgg { egg = false }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            // справа оставляем место под кнопку профиля
            Modifier
                .padding(start = 20.dp, end = 76.dp, top = 12.dp, bottom = 8.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (tap.tap()) egg = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("fStore", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "by #fworld",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Поиск, избранное и сортировка.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Поиск", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Очистить") }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            IconToggleButton(checked = favOnly, onCheckedChange = { favOnly = it }) {
                Icon(
                    if (favOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Только избранное",
                    tint = if (favOnly) Color(0xFFFF4D8D) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { sortOpen = true }) { Icon(Icons.Default.Sort, "Сортировка") }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    SORTS.forEachIndexed { i, n ->
                        DropdownMenuItem(
                            text = { Text(n) },
                            onClick = { AppPrefs.setSort(i); sortOpen = false },
                            leadingIcon = {
                                if (AppPrefs.sort == i) Icon(Icons.Default.Check, null)
                                else Spacer(Modifier.size(24.dp))
                            },
                        )
                    }
                }
            }
        }

        if (recent.isNotEmpty() && query.isBlank() && !favOnly) {
            RecentRow(recent, onOpen)
        }

        if (!Gh.ready) {
            Card(
                Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    "Сервер не настроен: в сборке нет токена FSTORE_TOKEN (см. README в репозитории).",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            if (list.isEmpty()) {
                Text(
                    if (vm.loading) "Загружаю…"
                    else if (favOnly) "В избранном пусто. Нажми сердечко на карточке приложения."
                    else "Здесь пока пусто. Потяни вниз, чтобы обновить.",
                    Modifier.fillMaxWidth().padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (UiPrefs.grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = list.size, key = { list[it].id }) { i ->
                        Box(if (LocalAnim.current) Modifier.appear(i) else Modifier) {
                            AppTile(list[i]) { onOpen(list[i].id) }
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    // нижний отступ учитывает плавающий док
                    contentPadding = listPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
                        Box(if (LocalAnim.current) Modifier.animateItem().appear(i) else Modifier) {
                            AppCard(a) { onOpen(a.id) }
                        }
                    }
                }
            }
        }
    }
}

/** Полка «недавно открытые» — иконки приложений, которые смотрели последними. */
@Composable
private fun RecentRow(list: List<AppItem>, onOpen: (String) -> Unit) {
    Column(Modifier.padding(top = 2.dp, bottom = 6.dp)) {
        Text(
            "Недавно открытые",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(count = list.size, key = { list[it].id }) { i ->
                val a = list[i]
                Column(
                    Modifier
                        .width(62.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpen(a.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(a, 52.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        a.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun AppDetail(vm: StoreVM, id: String, onBack: () -> Unit, onUpdate: (String) -> Unit) {
    val a = vm.apps.find { it.id == id }
    if (a == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    LaunchedEffect(a.id) { Recents.add(a.id) }
    val me = vm.me
    val isOwner = me != null && me.username == a.owner
    val owner = vm.users.find { it.username == a.owner }
    val revs = vm.reviews.filter { it.appId == a.id }.sortedByDescending { it.time }
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var replyFor by remember { mutableStateOf<Review?>(null) }
    var stars by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = glassBarColors(),
            title = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a, 96.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.headlineSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("от ", style = MaterialTheme.typography.bodyMedium)
                            NameLine(owner, a.owner, MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "Версия ${a.version} · ${fmtSize(a.size)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Сколько аккаунтов скачали: одно скачивание засчитывается один раз на аккаунт.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Download, null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Скачали: ${DownloadStats.count(a.id)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (DownloadStats.counted(a.id)) {
                                Text(
                                    "· засчитано вам",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (a.fworld) FworldTag()
                            StatusTag(a.status)
                        }
                    }
                }
            }
            if (owner != null) {
                val ub = userBadges(owner, vm.badges)
                if (ub.isNotEmpty()) item { BadgeRow(ub) }
            }
            if (a.status != "approved") {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Text(
                            if (a.status == "rejected") "Приложение отклонено администрацией."
                            else "Приложение ещё не проверено. Устанавливай на свой страх и риск.",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val act = vm.active[a.apk]
                    val saved = vm.downloads.lastOrNull { it.appId == a.id && it.version == a.version }
                    if (act != null) {
                        // Полоска загрузки: пока файл качается, видно процент и сколько уже скачано.
                        val shown by animateFloatAsState(act.progress, label = "dlProgress")
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (act.progress <= 0f) "Подключаюсь…" else "Скачивание… ${(act.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${fmtSize((act.progress * a.size).toLong())} из ${fmtSize(a.size)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (act.progress <= 0f) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                } else {
                                    LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
                                }
                                TextButton(onClick = { vm.cancelDownload(a.apk) }) { Text("Отменить") }
                            }
                        }
                    } else {
                        if (saved != null) {
                            Button(
                                onClick = { installApk(ctx, vm.downloadFile(saved)) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.InstallMobile, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Установить")
                            }
                            FilledTonalButton(onClick = { vm.download(a) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Скачать заново")
                            }
                        } else {
                            Button(onClick = { vm.download(a) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Скачать APK · ${fmtSize(a.size)}")
                            }
                        }
                    }
                    if (isOwner) {
                        FilledTonalButton(onClick = { onUpdate(a.id) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Update, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Обновить приложение")
                        }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Удалить приложение")
                        }
                    }
                }
            }
            val p = a.pending
            if (isOwner && p != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            "Обновление ${p.version} ожидает проверки.",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            if (a.whatsNew.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Что нового?", style = MaterialTheme.typography.titleMedium)
                            Text(a.whatsNew, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Описание", style = MaterialTheme.typography.titleMedium)
                    Text(a.desc.ifBlank { a.shortDesc.ifBlank { "Описания нет" } })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Отзывы", style = MaterialTheme.typography.titleLarge)
                    if (revs.isNotEmpty()) {
                        val avg = revs.sumOf { it.stars }.toDouble() / revs.size
                        StarsRow(avg.toInt())
                        Text(String.format("%.1f (%d)", avg, revs.size), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (me != null && !isOwner) {
                item {
                    Card(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Ваш отзыв", style = MaterialTheme.typography.titleMedium)
                            StarsRow(stars, 36.dp) { stars = it }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text("Текст отзыва") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { vm.submitReview(a.id, stars, text) { stars = 0; text = "" } },
                                enabled = !vm.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Отправить отзыв") }
                        }
                    }
                }
            } else if (me == null) {
                item {
                    Text(
                        "Войдите в профиль, чтобы оставить отзыв.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(revs, key = { it.id }) { r ->
                ReviewItem(r, vm.users.find { it.username == r.user }, vm.users.find { it.username == a.owner }, isOwner) {
                    replyFor = r
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить приложение?") },
            text = { Text("Приложение, его APK и отзывы будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteApp(a.id) { onBack() } }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
    replyFor?.let { r ->
        var t by remember(r.id) { mutableStateOf(r.reply) }
        AlertDialog(
            onDismissRequest = { replyFor = null },
            title = { Text("Ответ на отзыв") },
            text = {
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.reply(r.id, t) { replyFor = null } }) { Text("Отправить") }
            },
            dismissButton = { TextButton(onClick = { replyFor = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ReviewItem(r: Review, author: User?, appOwner: User?, canReply: Boolean, onReply: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (UiPrefs.reviewAvatars) {
                    Avatar(author, 36.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    NameLine(author, r.user)
                    Text(
                        fmtDate(r.time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StarsRow(r.stars, 16.dp)
            }
            if (r.text.isNotBlank()) Text(r.text)
            if (r.reply.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ответ разработчика · ", style = MaterialTheme.typography.labelMedium)
                            NameLine(appOwner, "", MaterialTheme.typography.labelMedium)
                        }
                        Text(r.reply)
                    }
                }
            }
            if (canReply) {
                TextButton(onClick = onReply) { Text(if (r.reply.isBlank()) "Ответить" else "Изменить ответ") }
            }
        }
    }
}
