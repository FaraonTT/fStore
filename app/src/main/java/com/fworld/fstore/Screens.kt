@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(vm: StoreVM, onOpen: (String) -> Unit) {
    var sub by rememberSaveable { mutableStateOf(vm.homeTab) }
    val titles = listOf("Проверенные", "Все", "Непроверенные")
    val list = remember(vm.apps, sub) {
        vm.apps
            .filter {
                when (sub) {
                    0 -> it.status == "approved"
                    1 -> it.status == "approved" || it.status == "pending"
                    else -> it.status == "pending"
                }
            }
            .sortedWith(compareByDescending<AppItem> { it.fworld }.thenByDescending { it.updated })
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            // справа оставляем место под кнопку профиля
            Modifier.padding(start = 20.dp, end = 76.dp, top = 12.dp, bottom = 8.dp),
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
        PrimaryTabRow(selectedTabIndex = sub, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            titles.forEachIndexed { i, t ->
                Tab(selected = sub == i, onClick = { sub = i }, text = { Text(t) })
            }
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
            LazyColumn(
                Modifier.fillMaxSize(),
                // нижний отступ учитывает плавающий док
                contentPadding = listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (list.isEmpty()) {
                    item {
                        Text(
                            if (vm.loading) "Загружаю…" else "Здесь пока пусто. Потяни вниз, чтобы обновить.",
                            Modifier.fillMaxWidth().padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
                    Box(if (LocalAnim.current) Modifier.animateItem().appear(i) else Modifier) {
                        AppCard(a) { onOpen(a.id) }
                    }
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
    val me = vm.me
    val isOwner = me != null && me.username == a.owner
    val owner = vm.users.find { it.username == a.owner }
    val revs = vm.reviews.filter { it.appId == a.id }.sortedByDescending { it.time }
    // Ссылки на APK перехватываются в MainActivity и скачиваются внутри приложения.
    val uri = LocalUriHandler.current
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
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (a.fworld) Tag("✨ #fworld", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
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
                    Button(
                        onClick = { uri.openUri(Gh.rawUrl(a.apk)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Скачать APK")
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
                Avatar(author, 36.dp)
                Spacer(Modifier.width(10.dp))
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
