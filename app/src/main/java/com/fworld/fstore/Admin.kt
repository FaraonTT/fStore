@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun AdminScreen(vm: StoreVM, onBack: () -> Unit) {
    val me = vm.me
    if (me == null || !me.isAdmin) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val tabs = if (me.isOwner) listOf("Приложения", "Кастомизация", "Управление") else listOf("Приложения", "Управление")
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Админ-панель") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
        )
        PrimaryTabRow(selectedTabIndex = tab.coerceAtMost(tabs.lastIndex)) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (tabs.getOrElse(tab) { tabs[0] }) {
            "Приложения" -> AdminApps(vm)
            "Кастомизация" -> AdminCustom(vm)
            else -> AdminManage(vm, me)
        }
    }
}

@Composable
private fun AdminApps(vm: StoreVM) {
    val list = vm.apps.sortedWith(
        compareByDescending<AppItem> { it.status == "pending" || it.pending != null }.thenByDescending { it.updated },
    )
    // Ссылки на APK перехватываются в MainActivity и скачиваются внутри приложения.
    val uri = LocalUriHandler.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (list.isEmpty()) item { Text("Приложений пока нет") }
        items(list, key = { it.id }) { a ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a, 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "@${a.owner} · v${a.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusTag(a.status)
                    }
                    a.pending?.let { p ->
                        Text(
                            "Обновление → v${p.version}" + if (p.whatsNew.isNotBlank()) ": ${p.whatsNew}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = a.fworld, onCheckedChange = { vm.setFworld(a.id, it) })
                        Spacer(Modifier.width(10.dp))
                        Text("Бейдж #fworld (отдельная вкладка, свечение)", Modifier.weight(1f))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (a.status != "approved" || a.pending != null) {
                            Button(onClick = { vm.approve(a.id) }) {
                                Text(if (a.pending != null) "Одобрить обновление" else "Одобрить")
                            }
                        }
                        if (a.status != "rejected" || a.pending != null) {
                            OutlinedButton(onClick = { vm.reject(a.id) }) {
                                Text(if (a.pending != null) "Отклонить обновление" else "Отклонить")
                            }
                        }
                        TextButton(onClick = { uri.openUri(Gh.rawUrl(a.pending?.apk ?: a.apk)) }) { Text("Скачать APK") }
                        TextButton(
                            onClick = { vm.deleteApp(a.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Удалить") }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSearch(q: String, onQ: (String) -> Unit) {
    OutlinedTextField(
        value = q,
        onValueChange = onQ,
        label = { Text("Поиск пользователя") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AdminCustom(vm: StoreVM) {
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<User?>(null) }
    val users = vm.users.filter {
        q.isBlank() || it.username.contains(q.trim(), true) || it.name.contains(q.trim(), true)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BadgeCreator(vm) }
        item { Text("Созданные бейджи", style = MaterialTheme.typography.titleMedium) }
        if (vm.badges.isEmpty()) item { Text("Бейджей пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(vm.badges, key = { it.id }) { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeChip(b) {}
                Spacer(Modifier.width(8.dp))
                Text(b.desc, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                IconButton(onClick = { vm.deleteBadge(b.id) }) { Icon(Icons.Default.Delete, "Удалить бейдж") }
            }
        }
        item { Text("Выдача пользователям", style = MaterialTheme.typography.titleMedium) }
        item { UserSearch(q) { q = it } }
        items(users, key = { it.username }) { u ->
            Card(
                onClick = { editing = u },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        NameLine(u)
                        Text(
                            "@${u.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    editing?.let { u ->
        // always edit the freshest copy
        val fresh = vm.users.find { it.username == u.username } ?: u
        UserStyleSheet(vm, fresh) { editing = null }
    }
}

@Composable
private fun BadgeCreator(vm: StoreVM) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(0) }
    var value by remember { mutableStateOf("") }
    var img by remember { mutableStateOf<ByteArray?>(null) }
    val kinds = listOf("icon", "color", "image")
    val kindNames = listOf("Иконка", "Цвет", "Фото")
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Новый бейдж", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Название") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = desc, onValueChange = { desc = it }, label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kindNames.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = kind == i,
                        onClick = { kind = i; value = "" },
                        shape = SegmentedButtonDefaults.itemShape(i, kindNames.size),
                    ) { Text(t) }
                }
            }
            when (kind) {
                0 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // векторные иконки вместо эмодзи
                    PICKABLE_ICONS.forEach { key ->
                        FilledTonalIconToggleButton(checked = value == key, onCheckedChange = { value = key }) {
                            Icon(badgeIcon(key), key)
                        }
                    }
                }
                1 -> ColorPicker("Цвет бейджа", value) { value = it }
                else -> ImagePicker(img, { img = it }, 80.dp, round = false, label = "Фото")
            }
            Button(
                onClick = {
                    vm.createBadge(name, desc, kinds[kind], value, img) {
                        name = ""; desc = ""; value = ""; img = null
                    }
                },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Создать бейдж") }
        }
    }
}

@Composable
private fun UserStyleSheet(vm: StoreVM, u: User, onClose: () -> Unit) {
    var glow by remember(u.username) { mutableStateOf(u.glow) }
    var verified by remember(u.username) { mutableStateOf(u.verified) }
    var nameColor by remember(u.username) { mutableStateOf(u.nameColor) }
    var glowColor by remember(u.username) { mutableStateOf(u.glowColor) }
    var shimmer by remember(u.username) { mutableStateOf(u.shimmer) }
    var shimmerA by remember(u.username) { mutableStateOf(u.shimmerA) }
    var shimmerB by remember(u.username) { mutableStateOf(u.shimmerB) }
    var picked by remember(u.username) { mutableStateOf(u.badges.toSet()) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(u, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    NameLine(
                        u.copy(
                            glow = glow, verified = verified, nameColor = nameColor, glowColor = glowColor,
                            shimmer = shimmer, shimmerA = shimmerA, shimmerB = shimmerB,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("@${u.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = glow, onCheckedChange = { glow = it })
                Spacer(Modifier.width(10.dp))
                Text("Светящийся ник")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = verified, onCheckedChange = { verified = it })
                Spacer(Modifier.width(10.dp))
                Text("Галочка верификации")
            }
            ColorPicker("Цвет ника", nameColor) { nameColor = it }
            ColorPicker("Цвет свечения", glowColor) { glowColor = it }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = shimmer, onCheckedChange = { shimmer = it })
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Переливающийся ник и подсветка")
                    Text(
                        "Два цвета плавно перетекают друг в друга как градиент",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (shimmer) {
                ColorPicker("Первый цвет перелива", shimmerA) { shimmerA = it }
                ColorPicker("Второй цвет перелива", shimmerB) { shimmerB = it }
                if (shimmerA.isBlank() || shimmerB.isBlank()) {
                    Text(
                        "Выбери оба цвета, иначе перелив не включится",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text("Бейджи", style = MaterialTheme.typography.titleMedium)
            if (vm.badges.isEmpty()) {
                Text("Сначала создай бейдж выше", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.badges.forEach { b ->
                    FilterChip(
                        selected = b.id in picked,
                        onClick = { picked = if (b.id in picked) picked - b.id else picked + b.id },
                        label = { Text(b.name) },
                        leadingIcon = { BadgeGlyph(b, 18.dp) },
                    )
                }
            }
            Button(
                onClick = {
                    vm.adminEditUser(u.username, ownerOnly = true) {
                        it.copy(
                            glow = glow,
                            verified = verified,
                            nameColor = nameColor.trim(),
                            glowColor = glowColor.trim(),
                            shimmer = shimmer,
                            shimmerA = shimmerA.trim(),
                            shimmerB = shimmerB.trim(),
                            badges = picked.toList(),
                        )
                    }
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }
}

@Composable
private fun AdminManage(vm: StoreVM, me: User) {
    var q by remember { mutableStateOf("") }
    val users = vm.users.filter {
        q.isBlank() || it.username.contains(q.trim(), true) || it.name.contains(q.trim(), true)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { UserSearch(q) { q = it } }
        if (users.isEmpty()) item { Text("Пользователей нет", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(users, key = { it.username }) { u ->
            val locked = u.isOwner || (u.isAdmin && !me.isOwner)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            NameLine(u)
                            Text(
                                "@${u.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    BadgeRow(userBadges(u, vm.badges))
                    if (locked) {
                        Text(
                            if (u.isOwner) "Владелец fStore" else "Администратор",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (me.isOwner) {
                                FilterChip(
                                    selected = u.role == "admin",
                                    onClick = {
                                        vm.adminEditUser(u.username, ownerOnly = true) {
                                            it.copy(role = if (it.role == "admin") "user" else "admin")
                                        }
                                    },
                                    label = { Text("Админ") },
                                )
                                FilterChip(
                                    selected = u.role == "moderator",
                                    onClick = {
                                        vm.adminEditUser(u.username, ownerOnly = true) {
                                            it.copy(role = if (it.role == "moderator") "user" else "moderator")
                                        }
                                    },
                                    label = { Text("Модератор") },
                                )
                            }
                            FilterChip(
                                selected = u.banned,
                                onClick = { vm.adminEditUser(u.username) { it.copy(banned = !it.banned) } },
                                label = { Text("Бан") },
                            )
                            FilterChip(
                                selected = u.virus,
                                onClick = { vm.adminEditUser(u.username) { it.copy(virus = !it.virus) } },
                                label = { Text("Вирусня...") },
                            )
                            // «Друг #fworld» выдаёт только владелец (is_owner); у админов этой кнопки нет.
                            if (me.isOwner) {
                                FilterChip(
                                    selected = u.friend,
                                    onClick = { vm.adminEditUser(u.username, ownerOnly = true) { it.copy(friend = !it.friend) } },
                                    label = { Text("Друг #fworld") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
