@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fworld.fstore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Публичная страница другого пользователя: аватарка, имя, бейджи, описание и его приложения. */
@Composable
fun UserProfileScreen(vm: StoreVM, username: String, onBack: () -> Unit, onOpenApp: (String) -> Unit) {
    val u = vm.users.find { it.username == username }
    if (u == null) {
        // Пользователь пропал (удалён) — возвращаемся, но только когда список уже загружен.
        LaunchedEffect(vm.loading, vm.users.size) { if (!vm.loading && vm.users.isNotEmpty()) onBack() }
        return
    }
    var showAvatar by remember { mutableStateOf(false) }
    if (showAvatar) AvatarViewer(u) { showAvatar = false }

    val apps = vm.apps
        .filter { it.owner == u.username && (it.status == "approved" || it.status == "pending") }
        .sortedByDescending { it.updated }
    val reviewsCount = vm.reviews.count { it.user == u.username }
    val ub = userBadges(u, vm.badges)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            colors = glassBarColors(),
            title = { Text("@${u.username}", maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(u, 112.dp, Modifier.clickable { showAvatar = true })
                    Spacer(Modifier.height(12.dp))
                    // Здесь имя не кликабельно — мы уже на странице пользователя.
                    CompositionLocalProvider(LocalOpenUser provides null) {
                        NameLine(u, style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(
                        "@${u.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val cs = MaterialTheme.colorScheme
                    if (u.isOwner || u.isAdmin || u.isModerator || u.friend || u.banned) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            when {
                                u.isOwner -> Tag("Владелец fStore", cs.primary, cs.onPrimary)
                                u.isAdmin -> Tag("Администратор", cs.primaryContainer, cs.onPrimaryContainer)
                                u.isModerator -> Tag("Модератор", cs.tertiaryContainer, cs.onTertiaryContainer)
                            }
                            if (u.friend) Tag("Друг #fworld", cs.secondaryContainer, cs.onSecondaryContainer)
                            if (u.banned) Tag("Заблокирован", cs.errorContainer, cs.onErrorContainer)
                        }
                    }
                    if (ub.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        BadgeRow(ub)
                    }
                    if (u.desc.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(u.desc, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Приложений", apps.size, Modifier.weight(1f))
                    StatCard("Отзывов", reviewsCount, Modifier.weight(1f))
                }
            }
            item { Text("Приложения", style = MaterialTheme.typography.titleLarge) }
            if (apps.isEmpty()) {
                item { Text("Приложений пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(apps, key = { it.id }) { a -> AppCard(a) { onOpenApp(a.id) } }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
