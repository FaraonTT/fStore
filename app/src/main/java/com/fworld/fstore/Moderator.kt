@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

/** Панель модератора: только проверка приложений — одобрить, отклонить, скачать APK. */
@Composable
fun ModeratorScreen(vm: StoreVM, onBack: () -> Unit) {
    val me = vm.me
    if (me == null || !me.isModerator) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val list = vm.apps
        .filter { it.status == "pending" || it.pending != null }
        .sortedByDescending { it.updated }
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            colors = glassBarColors(),
            title = { Text("Панель модератора") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
        )
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (list.isEmpty()) {
                item {
                    Text(
                        "Приложений на проверке нет.",
                        Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { a ->
                val upd = a.pending
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(a, 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(upd?.name ?: a.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@${a.owner} · v${upd?.version ?: a.version} · ${fmtSize(upd?.size ?: a.size)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Tag(
                                if (upd != null) "Обновление" else "Новое",
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        if (upd != null) {
                            Text(
                                "Обновление v${a.version} → v${upd.version}" +
                                    if (upd.whatsNew.isNotBlank()) ": ${upd.whatsNew}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else if (a.shortDesc.isNotBlank()) {
                            Text(a.shortDesc, style = MaterialTheme.typography.bodyMedium)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.approve(a.id) }, enabled = !vm.busy) {
                                Text(if (upd != null) "Одобрить обновление" else "Одобрить")
                            }
                            OutlinedButton(onClick = { vm.reject(a.id) }, enabled = !vm.busy) {
                                Text(if (upd != null) "Отклонить обновление" else "Отклонить")
                            }
                            TextButton(onClick = { uri.openUri(Gh.rawUrl(upd?.apk ?: a.apk)) }) { Text("Скачать APK") }
                        }
                    }
                }
            }
        }
    }
}
