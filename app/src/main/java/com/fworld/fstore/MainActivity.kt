@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fworld.fstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FStoreTheme {
                val vm: StoreVM = viewModel()
                FStoreApp(vm)
            }
        }
    }
}

@Composable
fun FStoreApp(vm: StoreVM) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var admin by rememberSaveable { mutableStateOf(false) }
    var uploadTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snack.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler(enabled = admin || detail != null) {
        if (admin) admin = false else detail = null
    }

    val items = listOf(
        "Главная" to Icons.Default.Home,
        "Профиль" to Icons.Default.Person,
        "Загрузить" to Icons.Default.CloudUpload,
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            detail = null
                            admin = false
                        },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val key = when {
                admin -> "admin"
                detail != null -> "detail:$detail"
                else -> "tab:$tab"
            }
            Crossfade(targetState = key, label = "nav") { k ->
                when {
                    k == "admin" -> AdminScreen(vm) { admin = false }
                    k.startsWith("detail:") -> AppDetail(
                        vm = vm,
                        id = k.removePrefix("detail:"),
                        onBack = { detail = null },
                        onUpdate = { id ->
                            uploadTarget = id
                            detail = null
                            tab = 2
                        },
                    )
                    k == "tab:0" -> HomeScreen(vm) { detail = it }
                    k == "tab:1" -> ProfileScreen(
                        vm = vm,
                        onOpenApp = { detail = it },
                        onUpload = { uploadTarget = null; tab = 2 },
                        onAdmin = { admin = true },
                    )
                    else -> UploadScreen(
                        vm = vm,
                        target = uploadTarget,
                        onTarget = { uploadTarget = it },
                        onGoProfile = { tab = 1 },
                    )
                }
            }
        }
    }
}
