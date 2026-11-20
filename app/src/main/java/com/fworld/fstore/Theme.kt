@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fworld.fstore

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Акцентная палитра: название, светлая (primary, container, onContainer), тёмная (primary, container, onContainer). */
class Accent(val name: String, val lp: Color, val lc: Color, val loc: Color, val dp: Color, val dc: Color, val doc: Color)

val ACCENTS = listOf(
    Accent("Фиолетовый", Color(0xFF6750A4), Color(0xFFEADDFF), Color(0xFF21005D), Color(0xFFD0BCFF), Color(0xFF4F378B), Color(0xFFEADDFF)),
    Accent("Синий", Color(0xFF1B5FB5), Color(0xFFD6E3FF), Color(0xFF001B3E), Color(0xFFAAC7FF), Color(0xFF004690), Color(0xFFD6E3FF)),
    Accent("Зелёный", Color(0xFF2E6A3F), Color(0xFFB3F1BB), Color(0xFF00210C), Color(0xFF98D5A0), Color(0xFF145229), Color(0xFFB3F1BB)),
    Accent("Оранжевый", Color(0xFF9A4600), Color(0xFFFFDCC6), Color(0xFF321300), Color(0xFFFFB787), Color(0xFF743400), Color(0xFFFFDCC6)),
    Accent("Розовый", Color(0xFFA33F6E), Color(0xFFFFD8E6), Color(0xFF3E0021), Color(0xFFFFB0CF), Color(0xFF82255A), Color(0xFFFFD8E6)),
    Accent("Красный", Color(0xFFB3261E), Color(0xFFF9DEDC), Color(0xFF410E0B), Color(0xFFF2B8B5), Color(0xFF8C1D18), Color(0xFFF9DEDC)),
)

/** Тёмная ли тема с учётом выбора пользователя (0 системная, 1 светлая, 2 тёмная). */
@Composable
fun isDarkTheme(mode: Int): Boolean = when (mode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF171717),
    surfaceContainerHighest = Color(0xFF1E1E1E),
)

/** Лёгкая прозрачность поверхностей: сквозь карточки просвечивает фон приложения. */
private fun ColorScheme.glass(a: Float): ColorScheme = copy(
    surfaceContainerLow = surfaceContainerLow.copy(alpha = a),
    surfaceContainer = surfaceContainer.copy(alpha = a),
    surfaceContainerHigh = surfaceContainerHigh.copy(alpha = a),
    surfaceContainerHighest = surfaceContainerHighest.copy(alpha = a),
)

@Composable
fun FStoreTheme(
    mode: Int = 0,
    amoled: Boolean = false,
    dynamic: Boolean = true,
    alpha: Float = 0.88f,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(mode)
    val ctx = LocalContext.current
    var scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> {
            val ac = ACCENTS[UiPrefs.accent.coerceIn(0, ACCENTS.lastIndex)]
            darkColorScheme(
                primary = ac.dp, onPrimary = Color(0xFF1B1B1F),
                primaryContainer = ac.dc, onPrimaryContainer = ac.doc,
                secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8),
            )
        }
        else -> {
            val ac = ACCENTS[UiPrefs.accent.coerceIn(0, ACCENTS.lastIndex)]
            lightColorScheme(
                primary = ac.lp, onPrimary = Color.White,
                primaryContainer = ac.lc, onPrimaryContainer = ac.loc,
                secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260),
            )
        }
    }
    if (dark && amoled) scheme = scheme.amoled()
    scheme = scheme.glass(alpha.coerceIn(0.6f, 1f))

    val base = Typography()
    val typography = base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(32.dp),
        extraLarge = RoundedCornerShape(40.dp),
    )
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = shapes,
        typography = typography,
    ) {
        // Scaffold с прозрачным фоном не задаёт цвет текста — без этого заголовки на тёмном фоне чёрные.
        val d = LocalDensity.current
        CompositionLocalProvider(
            LocalContentColor provides scheme.onBackground,
            LocalDensity provides Density(d.density, d.fontScale * UiPrefs.fontScale),
            content = content,
        )
    }
}
