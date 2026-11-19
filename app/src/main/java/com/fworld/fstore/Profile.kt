@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(vm: StoreVM, onOpenApp: (String) -> Unit, onUpload: () -> Unit, onAdmin: () -> Unit) {
    val me = vm.me
    if (me == null) {
        AuthScreen(vm)
        return
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) {
        BackHandler { editing = false }
        EditProfile(vm, me) { editing = false }
        return
    }
    val mine = vm.apps.filter { it.owner == me.username }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(me, 112.dp)
                Spacer(Modifier.height(12.dp))
                NameLine(me, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "@${me.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val ub = userBadges(me, vm.badges)
                if (ub.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    BadgeRow(ub)
                }
                if (me.desc.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(me.desc, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Редактировать профиль")
                }
                Button(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Загрузить приложение")
                }
                if (me.isAdmin) {
                    OutlinedButton(onClick = onAdmin, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AdminPanelSettings, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Админ-панель")
                    }
                }
            }
        }
        item { Text("Мои приложения", style = MaterialTheme.typography.titleLarge) }
        if (mine.isEmpty()) {
            item {
                Text(
                    "Пока нет приложений.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(mine, key = { it.id }) { a -> AppCard(a) { onOpenApp(a.id) } }
        item {
            TextButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Выйти")
            }
        }
    }
}

@Composable
fun AuthScreen(vm: StoreVM) {
    var reg by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var pw by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var avatar by remember { mutableStateOf<ByteArray?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Профиль", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Вход", "Регистрация").forEachIndexed { i, t ->
                SegmentedButton(
                    selected = (i == 1) == reg,
                    onClick = { reg = i == 1 },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(t) }
            }
        }
        if (reg) {
            ImagePicker(avatar, { avatar = it }, 112.dp, label = "Аватарка")
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Имя") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = username, onValueChange = { username = it }, label = { Text("Имя пользователя") },
            prefix = { Text("@") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (reg) {
            OutlinedTextField(
                value = desc, onValueChange = { desc = it }, label = { Text("Описание") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (vm.busy) {
            LoadingIndicator()
            Text(vm.stage)
        } else {
            Button(
                onClick = {
                    if (reg) vm.register(username, name, desc, pw, avatar) { pw = "" }
                    else vm.login(username, pw) { pw = "" }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (reg) "Создать аккаунт" else "Войти") }
        }
    }
}

@Composable
fun EditProfile(vm: StoreVM, me: User, onClose: () -> Unit) {
    var name by remember { mutableStateOf(me.name) }
    var desc by remember { mutableStateOf(me.desc) }
    var avatar by remember { mutableStateOf<ByteArray?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            title = { Text("Редактировать профиль") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ImagePicker(avatar, { avatar = it }, 112.dp, placeholder = { Avatar(me, 112.dp) })
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Имя") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = me.username, onValueChange = {}, label = { Text("Имя пользователя") },
                enabled = false, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = desc, onValueChange = { desc = it }, label = { Text("Описание") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            if (vm.busy) {
                LoadingIndicator()
            } else {
                Button(
                    onClick = { vm.updateProfile(name, desc, avatar) { onClose() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить") }
            }
        }
    }
}
