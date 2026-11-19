@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private suspend fun copyApk(ctx: Context, uri: Uri): Result<Pair<File, String>> = withContext(Dispatchers.IO) {
    runCatching {
        var name = "app.apk"
        var size = -1L
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        require(name.lowercase().endsWith(".apk")) { "Выберите файл .apk" }
        require(size <= MAX_APK) { "APK больше 100 МБ" }
        val f = File(ctx.cacheDir, "upload.apk")
        var total = 0L
        ctx.contentResolver.openInputStream(uri)!!.use { i ->
            f.outputStream().use { o ->
                val b = ByteArray(65536)
                while (true) {
                    val n = i.read(b)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_APK) { "APK больше 100 МБ" }
                    o.write(b, 0, n)
                }
            }
        }
        f to name
    }
}

@Composable
fun UploadScreen(vm: StoreVM, target: String?, onTarget: (String?) -> Unit, onGoProfile: () -> Unit) {
    val me = vm.me
    if (me == null) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                "Загрузка доступна только для залогиненных пользователей",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onGoProfile) { Text("Войти или зарегистрироваться") }
        }
        return
    }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mine = vm.apps.filter { it.owner == me.username }
    val update = target != null

    var name by rememberSaveable(target) { mutableStateOf("") }
    var version by rememberSaveable(target) { mutableStateOf("") }
    var shortDesc by rememberSaveable(target) { mutableStateOf("") }
    var desc by rememberSaveable(target) { mutableStateOf("") }
    var whatsNew by rememberSaveable(target) { mutableStateOf("") }
    var icon by remember(target) { mutableStateOf<ByteArray?>(null) }
    var apk by remember(target) { mutableStateOf<Pair<File, String>?>(null) }
    var apkError by remember(target) { mutableStateOf<String?>(null) }

    LaunchedEffect(target) {
        mine.find { it.id == target }?.let { name = it.name }
    }

    val apkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            copyApk(ctx, uri)
                .onSuccess { apk = it; apkError = null }
                .onFailure { apk = null; apkError = it.message ?: "Не удалось прочитать файл" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Загрузить", style = MaterialTheme.typography.headlineMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !update,
                onClick = { onTarget(null) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Новое приложение") }
            SegmentedButton(
                selected = update,
                onClick = { onTarget(mine.firstOrNull()?.id) },
                enabled = mine.isNotEmpty(),
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Обновление") }
        }

        if (update) {
            Text("Какое приложение обновляем?", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mine.forEach { a ->
                    FilterChip(
                        selected = a.id == target,
                        onClick = { onTarget(a.id) },
                        label = { Text(a.name) },
                    )
                }
            }
        }

        OutlinedButton(onClick = { apkLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Android, null)
            Spacer(Modifier.width(8.dp))
            Text(apk?.let { "${it.second} · ${fmtSize(it.first.length())}" } ?: "Выбрать APK (до 100 МБ)")
        }
        apkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Имя приложения") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = version, onValueChange = { version = it }, label = { Text("Версия приложения") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (update) {
            OutlinedTextField(
                value = whatsNew, onValueChange = { whatsNew = it }, label = { Text("Что нового?") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = shortDesc, onValueChange = { shortDesc = it.take(100) },
                label = { Text("Краткое описание") },
                supportingText = { Text("${shortDesc.length}/100") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = desc, onValueChange = { desc = it }, label = { Text("Описание") },
                minLines = 4, modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ImagePicker(icon, { icon = it }, 88.dp, round = false, label = "Иконка")
            Text(
                if (update) "Иконка (необязательно — оставь пустой, чтобы не менять)" else "Иконка приложения",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        val ready = apk != null && name.isNotBlank() && version.isNotBlank() &&
            (update || icon != null) && !vm.busy
        if (vm.busy) {
            Text(vm.stage, style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
        } else {
            Button(
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val f = apk!!.first
                    if (update) {
                        vm.submitUpdate(target!!, name, version, whatsNew, icon, f) {
                            version = ""; whatsNew = ""; icon = null; apk = null
                            vm.message = "Обновление отправлено на проверку"
                        }
                    } else {
                        vm.submitNew(name, version, shortDesc, desc, icon!!, f) {
                            name = ""; version = ""; shortDesc = ""; desc = ""; icon = null; apk = null
                            vm.message = "Приложение отправлено на проверку"
                        }
                    }
                },
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text(if (update) "Отправить обновление на проверку" else "Отправить на проверку")
            }
        }
    }
}
