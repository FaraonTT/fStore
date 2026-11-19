@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fworld.fstore

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FStoreTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8),
        )
        else -> lightColorScheme(
            primary = Color(0xFF6750A4), onPrimary = Color.White,
            primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260),
        )
    }
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
        content = content,
    )
}
