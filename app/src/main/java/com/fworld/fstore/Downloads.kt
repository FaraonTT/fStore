@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fworld.fstore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DownloadsScreen(vm: StoreVM, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<DownloadItem?>(null) }
    val anim = LocalAnim.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = glassBarColors(),
            title = { Text("Загрузки") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            actions = {
                if (vm.downloads.isNotEmpty()) {
                    IconButton(onClick = { confirmAll = true }) { Icon(Icons.Default.DeleteSweep, "Удалить все") }
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.downloads.isEmpty() && vm.active.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Download, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Пока ничего не скачано", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Нажми «Скачать APK» на странице приложения — файл сохранится здесь, без браузера.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(vm.active.entries.toList(), key = { "active:" + it.key }) { (key, a) ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = if (anim) Modifier.animateItem() else Modifier,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${a.name} · v${a.version}", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { a.progress }, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Скачивание… ${(a.progress * 100).toInt()}%", Modifier.weight(1f))
                            TextButton(onClick = { vm.cancelDownload(key) }) { Text("Отменить") }
                        }
                    }
                }
            }
            items(vm.downloads, key = { it.file }) { d ->
                val app = vm.apps.find { it.id == d.appId }
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = if (anim) Modifier.animateItem() else Modifier,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (app != null) AppIcon(app, 52.dp)
                            else Icon(Icons.Default.Android, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "v${d.version} · ${fmtSize(d.size)} · ${fmtDate(d.time)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { confirmOne = d }) {
                                Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Button(onClick = { installApk(ctx, vm.downloadFile(d)) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.InstallMobile, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Установить")
                        }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Удалить все загрузки?") },
            text = { Text("Скачанные APK-файлы будут удалены с устройства.") },
            confirmButton = { TextButton(onClick = { confirmAll = false; vm.clearDownloads() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Отмена") } },
        )
    }
    confirmOne?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmOne = null },
            title = { Text("Удалить файл?") },
            text = { Text("${d.name} v${d.version} будет удалён с устройства.") },
            confirmButton = { TextButton(onClick = { confirmOne = null; vm.deleteDownload(d) }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmOne = null }) { Text("Отмена") } },
        )
    }
}
