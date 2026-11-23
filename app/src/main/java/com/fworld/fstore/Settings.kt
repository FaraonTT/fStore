@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

@Composable
private fun RowBase(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowBase(icon, title, sub, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    RowBase(icon, title, sub, onClick = onClick) {
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    sub: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun SettingsScreen(vm: StoreVM, onProfile: () -> Unit, onDownloads: () -> Unit, onEgg: () -> Unit) {
    val ctx = LocalContext.current
    val me = vm.me
    val dlSize = vm.downloads.sumOf { it.size }
    var settingsTab by rememberSaveable { mutableStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomPad.current),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        PrimaryScrollableTabRow(
            selectedTabIndex = settingsTab,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
        ) {
            listOf("Вид", "Каталог", "Уведомления", "Аккаунт").forEachIndexed { index, title ->
                Tab(selected = settingsTab == index, onClick = { settingsTab = index }, text = { Text(title) })
            }
        }

        if (settingsTab == 0) {
        Section("Внешний вид") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text("Тема", style = MaterialTheme.typography.titleMedium)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Системная", "Светлая", "Тёмная").forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = vm.themeMode == i,
                            onClick = { vm.setThemeMode(i) },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text(t, maxLines = 1) }
                    }
                }
            }
            SwitchRow(Icons.Default.Contrast, "AMOLED", "Чисто чёрный фон в тёмной теме", vm.amoled, vm::setAmoled)
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow(
                    Icons.Default.Palette, "Цвета из обоев",
                    "Material You — подстраивать палитру под систему", vm.dynamicColor, vm::setDynamicColor,
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Opacity, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Прозрачность", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Плотность карточек и панелей",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("${(vm.uiAlpha * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = vm.uiAlpha,
                    onValueChange = { vm.setUiAlpha(it) },
                    valueRange = 0.7f..1f,
                )
            }
            SwitchRow(Icons.Default.Animation, "Анимации", "Плавные переходы и появление списков", vm.animations, vm::setAnimations)
            SwitchRow(Icons.Default.Vibration, "Вибрация", "Отклик при переключении вкладок", UiPrefs.haptics, UiPrefs::setHaptics)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Акцентный цвет", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (vm.dynamicColor && Build.VERSION.SDK_INT >= 31) "Выключите «Цвета из обоев», чтобы выбрать"
                            else ACCENTS[UiPrefs.accent].name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ACCENTS.forEachIndexed { i, ac ->
                        val sel = UiPrefs.accent == i
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ac.lp)
                                .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { UiPrefs.setAccent(i) },
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Default.Check, ac.name, tint = Color.White) }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatSize, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Размер текста", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Поверх системного масштаба",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("${(UiPrefs.fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                Slider(
                    value = UiPrefs.fontScale,
                    onValueChange = { UiPrefs.setFontScale((it * 20).toInt() / 20f) },
                    valueRange = 0.85f..1.3f,
                )
            }
        }

        // ---------- новое: жидкое стекло и подсветка статуса ----------
        Section("Жидкое стекло и подсветка") {
            SliderRow(
                Icons.Default.WaterDrop, "Сила жидкого стекла",
                "Зажми карточку или док и потяни",
                UiPrefs.liquid, 0f..1.8f,
                if (UiPrefs.liquid <= 0.01f) "выкл." else "${(UiPrefs.liquid * 100).toInt()}%",
                UiPrefs::setLiquid,
            )
            SwitchRow(
                Icons.Default.AutoAwesome, "Подсветка статуса",
                "Белая рамка — проверено, красная — не проверено",
                UiPrefs.glow, UiPrefs::setGlow,
            )
            SliderRow(
                Icons.Default.Tune, "Сила свечения",
                "Насколько ярко светится рамка",
                UiPrefs.glowPower, 0.4f..1.8f,
                "${(UiPrefs.glowPower * 100).toInt()}%",
                UiPrefs::setGlowPower,
            )
            SliderRow(
                Icons.Default.ViewAgenda, "Скругление карточек",
                "От строгих прямоугольников до «таблеток»",
                UiPrefs.corner.toFloat(), 12f..34f,
                "${UiPrefs.corner} dp",
                { UiPrefs.setCorner(it.toInt()) },
            )
        }

        }

        if (settingsTab == 1) {
        Section("Файлы") {
            NavRow(
                Icons.Default.Download, "Загрузки",
                if (vm.downloads.isEmpty() && vm.active.isEmpty()) "Скачанные приложения появятся здесь"
                else "${vm.downloads.size} шт. · ${fmtSize(dlSize)}" + if (vm.active.isNotEmpty()) " · идёт скачивание" else "",
                onDownloads,
            )
            NavRow(Icons.Default.DeleteSweep, "Очистить кэш картинок", "Иконки и аватарки загрузятся заново") {
                ctx.imageLoader.memoryCache?.clear()
                ctx.imageLoader.diskCache?.clear()
                vm.message = "Кэш картинок очищен"
            }
        }
        Section("Каталог") {
            SwitchRow(Icons.Default.GridView, "Две колонки", "Каталог сеткой, а не списком", UiPrefs.grid, UiPrefs::setGrid)
            SwitchRow(
                Icons.Default.Download, "Счётчик скачиваний",
                "Значок «сколько скачали» на карточке, +1 за аккаунт",
                UiPrefs.showDownloads, UiPrefs::setShowDownloads,
            )
            SwitchRow(
                Icons.Default.TouchApp, "Отклик на нажатие",
                "Карточка слегка сжимается под пальцем",
                UiPrefs.pressFx, UiPrefs::setPressFx,
            )
            SwitchRow(
                Icons.Default.Person, "Аватарки в отзывах",
                "Показывать фото автора отзыва",
                UiPrefs.reviewAvatars, UiPrefs::setReviewAvatars,
            )
            SwitchRow(
                Icons.Default.Description, "Краткое описание",
                "Показывать в списке", UiPrefs.showDesc, UiPrefs::setShowDesc,
            )
            SwitchRow(Icons.Default.Storage, "Размер файла", "Показывать рядом с версией", UiPrefs.showSize, UiPrefs::setShowSize)
            SwitchRow(Icons.Default.ViewAgenda, "Компактные карточки", "Меньше иконка и отступы", UiPrefs.compactCards, UiPrefs::setCompactCards)
            SwitchRow(
                Icons.Default.Star, "Оценка на карточке",
                "Средняя оценка по отзывам рядом со значком скачиваний",
                UiPrefs.showRating, UiPrefs::setShowRating,
            )
            SwitchRow(
                Icons.Default.History, "Недавно открытые",
                "Полка с последними приложениями над списком",
                UiPrefs.showRecent, UiPrefs::setShowRecent,
            )
            SwitchRow(
                Icons.Default.VisibilityOff, "Скрывать непроверенные во «Все»",
                "Во вкладке «Все» останутся только проверенные; непроверенные — в своей вкладке",
                AppPrefs.hideUnverified, AppPrefs::setHideUnverified,
            )
        }
        Section("Скачивание") {
            SwitchRow(
                Icons.Default.TaskAlt, "Подтверждать скачивание",
                "Спрашивать перед загрузкой: версия и размер файла",
                AppPrefs.confirmDownload, AppPrefs::setConfirmDownload,
            )
            SwitchRow(
                Icons.Default.InstallMobile, "Сразу открывать установщик",
                "Когда файл скачан, сразу предложить установку",
                AppPrefs.autoInstall, AppPrefs::setAutoInstall,
            )
        }


        }

        if (settingsTab == 2) {
        Section("Уведомления") {
            SwitchRow(
                Icons.Default.Notifications, "Новые релизы",
                "Фоновая проверка каталога и уведомление о новых версиях",
                AppPrefs.notifyRelease,
            ) {
                AppPrefs.setNotifyRelease(it)
                ReleaseWatch.schedule(ctx)
            }
            SwitchRow(
                Icons.Default.RateReview, "Новые отзывы",
                "Кто-то оставил отзыв на ваше приложение",
                AppPrefs.notifyReview,
            ) {
                AppPrefs.setNotifyReview(it)
                ReleaseWatch.schedule(ctx)
            }
            SwitchRow(
                Icons.Default.Reply, "Ответы на мои отзывы",
                "Разработчик ответил на ваш отзыв",
                AppPrefs.notifyReply,
            ) {
                AppPrefs.setNotifyReply(it)
                ReleaseWatch.schedule(ctx)
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Как часто проверять", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Релизы и отзывы в фоне. Чаще — быстрее уведомление, но больше расход батареи",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val steps = listOf(15 to "15 мин", 30 to "30 мин", 60 to "1 ч", 180 to "3 ч")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    steps.forEachIndexed { i, (m, t) ->
                        SegmentedButton(
                            selected = AppPrefs.watchMinutes == m,
                            onClick = { AppPrefs.setWatchMinutes(m); ReleaseWatch.schedule(ctx) },
                            shape = SegmentedButtonDefaults.itemShape(i, steps.size),
                        ) { Text(t, maxLines = 1) }
                    }
                }
            }
            SwitchRow(
                Icons.Default.NotificationsActive, "Скачивание началось",
                "Уведомление в момент старта загрузки",
                AppPrefs.notifyStart, AppPrefs::setNotifyStart,
            )
            SwitchRow(
                Icons.Default.Download, "Скачивание завершено",
                "Уведомление, когда файл уже на телефоне",
                AppPrefs.notifyDone, AppPrefs::setNotifyDone,
            )
            SwitchRow(
                Icons.Default.Wifi, "Только Wi-Fi",
                "Не скачивать APK через мобильную сеть",
                AppPrefs.wifiOnly, AppPrefs::setWifiOnly,
            )
            NavRow(
                Icons.Default.Notifications,
                "Проверить уведомления",
                if (!Notifier.allowed(ctx)) "Уведомления выключены в системе — открыть настройки"
                else "Отправить тестовое уведомление",
            ) {
                if (Notifier.allowed(ctx)) Notifier.test(ctx) else Notifier.openSystemSettings(ctx)
            }
        }

        }

        if (settingsTab == 3) {
        Section("Главная") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text("Какую вкладку открывать", style = MaterialTheme.typography.titleMedium)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Проверенные", "Все", "Непроверенные").forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = vm.homeTab == i,
                            onClick = { vm.setHomeTab(i) },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text(t, maxLines = 1) }
                    }
                }
                Text(
                    "Применится при следующем запуске приложения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NavRow(Icons.Default.Refresh, "Обновить данные", "Загрузить список приложений заново") { vm.refresh() }
        }

        Section("Аккаунт") {
            if (me != null) {
                NavRow(Icons.Default.Person, "Профиль", "@${me.username}", onProfile)
                NavRow(Icons.AutoMirrored.Filled.Logout, "Выйти", null) { vm.logout() }
            } else {
                NavRow(Icons.Default.Person, "Войти или зарегистрироваться", null, onProfile)
            }
        }

        Section("О приложении") {
            RowBase(
                Icons.Default.Info, "fStore",
                "Версия ${BuildConfig.VERSION_NAME} · by @fworld_team · нажми",
                onClick = onEgg,
            )
            NavRow(Icons.Default.RestartAlt, "Сбросить внешний вид", "Акцент, размер текста и карточки по умолчанию") {
                UiPrefs.reset()
                vm.message = "Настройки внешнего вида сброшены"
            }
        }
        }
    }
}
