@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.fworld.fstore

import android.Manifest
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Настройки читаем до первой композиции: от них зависят тема, «жидкость» и подсветка.
        UiPrefs.init(applicationContext)
        AppPrefs.init(applicationContext)
        Notifier.init(applicationContext)
        Favorites.init(applicationContext)
        Recents.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val vm: StoreVM = viewModel()
            val dark = isDarkTheme(vm.themeMode)
            // Иконки статус-бара и навигации должны следовать теме приложения, а не системной.
            androidx.compose.runtime.DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            FStoreTheme(vm.themeMode, vm.amoled, vm.dynamicColor, vm.uiAlpha) {
                FStoreApp(vm, dark)
            }
        }
    }
}

private val DOCK_ITEMS: List<Triple<String, ImageVector, Int>> = listOf(
    Triple("Главная", Icons.Default.Home, 0),
    Triple("Загрузить", Icons.Default.CloudUpload, 1),
    Triple("Настройки", Icons.Default.Settings, 2),
)

private fun tabIndex(key: String): Int? = if (key.startsWith("tab:")) key.removePrefix("tab:").toIntOrNull() else null

@Composable
fun FStoreApp(vm: StoreVM, dark: Boolean) {
    var tab by rememberSaveable { mutableStateOf(0) }
    // Вкладка каталога (#fworld / Проверенные / Все / Непроверенные) живёт в плавающей панели над доком.
    var homeSub by rememberSaveable { mutableStateOf(vm.homeTab + 1) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var admin by rememberSaveable { mutableStateOf(false) }
    var profile by rememberSaveable { mutableStateOf(false) }
    var downloads by rememberSaveable { mutableStateOf(false) }
    var moderator by rememberSaveable { mutableStateOf(false) }
    var egg by rememberSaveable { mutableStateOf(false) }
    var viewUser by rememberSaveable { mutableStateOf<String?>(null) }
    // Куда вернуться из страницы пользователя: на приложение, с которого пришли.
    var userBackDetail by rememberSaveable { mutableStateOf<String?>(null) }
    var uploadTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val anim = vm.animations
    val ctx = LocalContext.current

    // Уведомления: разрешение спрашиваем сразу при запуске, а не когда понадобится.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.message = "Уведомления выключены: их можно включить в настройках системы"
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.permissionGranted(ctx) && !AppPrefs.asked()) {
            AppPrefs.markAsked()
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Фоновая проверка новых релизов (если включена в настройках).
        ReleaseWatch.schedule(ctx)
    }

    // Счётчик скачиваний: читаем при загрузке каталога и при смене аккаунта.
    LaunchedEffect(vm.apps, vm.me) { DownloadStats.load(vm.me?.username) }

    // Новый файл в «Загрузках» = скачивание: +1 приложению, но не больше раза на аккаунт.
    var counted by remember { mutableStateOf(vm.downloads.map { it.file }.toSet()) }
    LaunchedEffect(vm.downloads) {
        val fresh = vm.downloads.filter { it.file !in counted }
        if (fresh.isEmpty()) return@LaunchedEffect
        counted = counted + fresh.map { it.file }.toSet()
        val me = vm.me ?: return@LaunchedEffect
        fresh.forEach { if (it.appId.isNotBlank()) DownloadStats.tick(it.appId, me.username) }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snack.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler(enabled = egg || admin || moderator || viewUser != null || detail != null || profile || downloads) {
        when {
            egg -> egg = false
            admin -> admin = false
            moderator -> moderator = false
            viewUser != null -> { viewUser = null; detail = userBackDetail; userBackDetail = null }
            detail != null -> detail = null
            profile -> profile = false
            downloads -> downloads = false
        }
    }

    // Док виден только на основных вкладках, на вложенных экранах он уезжает вниз.
    val onTab = !egg && !admin && !moderator && viewUser == null && detail == null && !profile && !downloads
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockPad = if (onTab) (if (tab == 0) 144.dp else 88.dp) + navBottom else 0.dp

    val cs = MaterialTheme.colorScheme
    val glow = cs.primary.copy(alpha = if (dark && vm.amoled) 0.10f else 0.16f).compositeOver(cs.background)
    val bg = Brush.verticalGradient(0f to glow, 0.5f to cs.background, 1f to cs.background)

    // Ссылки на APK из каталога скачиваются внутри приложения (в «Загрузки»), а не открываются в браузере.
    val baseUri = LocalUriHandler.current
    val uriHandler = remember(baseUri, vm) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (!vm.downloadByUrl(uri)) baseUri.openUri(uri)
            }
        }
    }

    CompositionLocalProvider(
        LocalAnim provides anim,
        LocalBottomPad provides dockPad,
        LocalUriHandler provides uriHandler,
        LocalOpenUser provides { u: String ->
            if (vm.me?.username == u) {
                profile = true
            } else {
                userBackDetail = detail
                detail = null
                viewUser = u
            }
        },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snack, Modifier.padding(bottom = dockPad)) },
            modifier = Modifier.background(bg),
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                val key = when {
                    egg -> "egg"
                    admin -> "admin"
                    moderator -> "moderator"
                    profile -> "profile"
                    viewUser != null -> "user:$viewUser"
                    downloads -> "downloads"
                    detail != null -> "detail:$detail"
                    else -> "tab:$tab"
                }
                AnimatedContent(
                    targetState = key,
                    transitionSpec = {
                        val from = tabIndex(initialState)
                        val to = tabIndex(targetState)
                        when {
                            !anim -> EnterTransition.None togetherWith ExitTransition.None
                            from != null && to != null -> {
                                val dir = if (to > from) 1 else -1
                                (slideInHorizontally(tween(320)) { it / 7 * dir } + fadeIn(tween(320))) togetherWith
                                    (slideOutHorizontally(tween(220)) { -it / 7 * dir } + fadeOut(tween(160)))
                            }
                            else -> (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.94f)) togetherWith
                                fadeOut(tween(160))
                        }
                    },
                    label = "nav",
                ) { k ->
                    when {
                        k == "egg" -> Egg3DScreen { egg = false }
                        k == "admin" -> CompositionLocalProvider(LocalOpenUser provides null) {
                            AdminScreen(vm) { admin = false }
                        }
                        k == "moderator" -> ModeratorScreen(vm) { moderator = false }
                        k.startsWith("user:") -> UserProfileScreen(
                            vm = vm,
                            username = k.removePrefix("user:"),
                            onBack = { viewUser = null; detail = userBackDetail; userBackDetail = null },
                            onOpenApp = { viewUser = null; userBackDetail = null; detail = it },
                        )
                        k == "profile" -> ProfileScreen(
                            vm = vm,
                            onOpenApp = { profile = false; detail = it },
                            onUpload = { uploadTarget = null; profile = false; tab = 1 },
                            onAdmin = { admin = true },
                            onModerator = { moderator = true },
                        )
                        k == "downloads" -> DownloadsScreen(vm) { downloads = false }
                        k.startsWith("detail:") -> AppDetail(
                            vm = vm,
                            id = k.removePrefix("detail:"),
                            onBack = { detail = null },
                            onUpdate = { id ->
                                uploadTarget = id
                                detail = null
                                tab = 1
                            },
                        )
                        k == "tab:0" -> HomeScreen(vm, homeSub) { detail = it }
                        k == "tab:1" -> UploadScreen(
                            vm = vm,
                            target = uploadTarget,
                            onTarget = { uploadTarget = it },
                            onGoProfile = { profile = true },
                        )
                        else -> SettingsScreen(
                            vm = vm,
                            onProfile = { profile = true },
                            onDownloads = { downloads = true },
                            onEgg = { egg = true },
                        )
                    }
                }

                // Профиль — кнопка в правом верхнем углу.
                AnimatedVisibility(
                    visible = onTab,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = if (anim) fadeIn() + scaleIn(initialScale = 0.6f) else EnterTransition.None,
                    exit = if (anim) fadeOut() + scaleOut(targetScale = 0.6f) else ExitTransition.None,
                ) {
                    ProfileButton(vm.me) { profile = true }
                }

                // В профиле на том же месте — кнопка «закрыть» (у остальных вложенных экранов своя стрелка назад).
                AnimatedVisibility(
                    visible = profile && !admin && !moderator,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = if (anim) fadeIn() + scaleIn(initialScale = 0.6f) else EnterTransition.None,
                    exit = if (anim) fadeOut() + scaleOut(targetScale = 0.6f) else ExitTransition.None,
                ) {
                    CloseButton { profile = false }
                }

                // Плавающий док.
                AnimatedVisibility(
                    visible = onTab,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = if (anim) slideInVertically(tween(320)) { it } + fadeIn(tween(320)) else EnterTransition.None,
                    exit = if (anim) slideOutVertically(tween(220)) { it } + fadeOut(tween(160)) else ExitTransition.None,
                ) {
                    val haptic = LocalHapticFeedback.current
                    // Вкладки каталога и док — одна плавающая группа: панель вкладок сидит прямо над доком.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedVisibility(
                            visible = tab == 0,
                            enter = if (anim) fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.9f) else EnterTransition.None,
                            exit = if (anim) fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.9f) else ExitTransition.None,
                        ) {
                            HomeTabsPill(homeSub, anim) { i ->
                                if (UiPrefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                homeSub = i
                            }
                        }
                        FloatingDock(
                            selected = tab,
                            anim = anim,
                            onSelect = { i ->
                                if (UiPrefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                tab = i
                                detail = null
                                admin = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileButton(me: User?, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 10.dp, end = 14.dp)
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (me != null) {
                Avatar(me, 42.dp)
            } else {
                Icon(Icons.Default.Person, "Профиль", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 10.dp, end = 14.dp)
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "Закрыть профиль", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Док из «жидкого стекла»: зажми — он плывёт только по горизонтали, вкладка под пальцем подсвечивается,
 * а после отпускания открывается выбранная вкладка.
 */
@Composable
private fun FloatingDock(selected: Int, anim: Boolean, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var dragX by remember { mutableStateOf(0f) }
    var preview by remember(selected) { mutableStateOf(selected) }
    val step = with(density) { 76.dp.toPx() }

    Surface(
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        tonalElevation = 6.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 14.dp)
            .softGlow(RoundedCornerShape(34.dp), MaterialTheme.colorScheme.primary, 12.dp, 0.25f)
            .pointerInput(selected) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragX = 0f
                        preview = selected
                        if (UiPrefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = { onSelect(preview); dragX = 0f },
                    onDragCancel = { dragX = 0f; preview = selected },
                    onDrag = { change, delta ->
                        change.consume()
                        dragX += delta.x
                        // Док ощущается упругим и выбирает вкладку только по горизонтальной оси.
                        preview = (selected + (dragX / step).toInt()).coerceIn(0, DOCK_ITEMS.lastIndex)
                    },
                )
            },
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DOCK_ITEMS.forEachIndexed { index, (label, icon, _) ->
                DockItem(label, icon, index == preview, anim) { onSelect(index) }
            }
        }
    }
}


/** Панель вкладок каталога: плавает над доком в том же стеклянном стиле. */
@Composable
private fun HomeTabsPill(selected: Int, anim: Boolean, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    Surface(
        shape = shape,
        color = cs.surfaceContainerHigh.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .softGlow(shape, cs.primary, 10.dp, 0.2f),
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HOME_TABS.forEachIndexed { i, title ->
                val on = i == selected
                val bg by animateColorAsState(
                    if (on) cs.primaryContainer else Color.Transparent,
                    animationSpec = tween(if (anim) 220 else 0),
                    label = "homeTabBg",
                )
                val fg by animateColorAsState(
                    if (on) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    animationSpec = tween(if (anim) 220 else 0),
                    label = "homeTabFg",
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(bg)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DockItem(label: String, icon: ImageVector, selected: Boolean, anim: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) cs.primaryContainer else Color.Transparent,
        animationSpec = tween(if (anim) 260 else 0),
        label = "dockBg",
    )
    val fg by animateColorAsState(
        if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
        animationSpec = tween(if (anim) 260 else 0),
        label = "dockFg",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .then(if (anim) Modifier.animateContentSize(tween(280)) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, tint = fg)
        AnimatedVisibility(
            visible = selected,
            enter = if (anim) expandHorizontally(tween(280)) + fadeIn(tween(280)) else EnterTransition.None,
            exit = if (anim) shrinkHorizontally(tween(220)) + fadeOut(tween(160)) else ExitTransition.None,
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
