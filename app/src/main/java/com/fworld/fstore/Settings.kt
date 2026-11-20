@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun SettingsScreen(vm: StoreVM, onProfile: () -> Unit, onDownloads: () -> Unit) {
    val ctx = LocalContext.current
    val me = vm.me
    val dlSize = vm.downloads.sumOf { it.size }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + LocalBottomPad.current),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        Section("Файлы") {
            NavRow(
                Icons.Default.Download, "Загрузки",
                if (vm.downloads.isEmpty() && vm.active.isEmpty()) "Скачанные приложения появятся здесь"
                else "${vm.downloads.size} шт. · ${fmtSize(dlSize)}" +
                    if (vm.active.isNotEmpty()) " · идёт скачивание" else "",
                onDownloads,
            )
            NavRow(Icons.Default.DeleteSweep, "Очистить кэш картинок", "Иконки и аватарки загрузятся заново") {
                ctx.imageLoader.memoryCache?.clear()
                ctx.imageLoader.diskCache?.clear()
                vm.message = "Кэш картинок очищен"
            }
        }

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

        Section("Карточки приложений") {
            SwitchRow(Icons.Default.ViewAgenda, "Компактные карточки", "Меньше иконка и отступы", UiPrefs.compactCards, UiPrefs::setCompactCards)
            SwitchRow(Icons.Default.Description, "Краткое описание", "Показывать в списке", UiPrefs.showDesc, UiPrefs::setShowDesc)
            SwitchRow(Icons.Default.Storage, "Размер файла", "Показывать рядом с версией", UiPrefs.showSize, UiPrefs::setShowSize)
        }

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
            RowBase(Icons.Default.Info, "fStore", "Версия ${BuildConfig.VERSION_NAME} · by #fworld")
            NavRow(Icons.Default.RestartAlt, "Сбросить внешний вид", "Акцент, размер текста и карточки по умолчанию") {
                UiPrefs.reset()
                vm.message = "Настройки внешнего вида сброшены"
            }
        }
    }
}
