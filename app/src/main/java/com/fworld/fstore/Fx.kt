@file:OptIn(ExperimentalMaterial3Api::class)

package com.fworld.fstore

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File

/** Включены ли анимации (настройка «Анимации»). */
val LocalAnim = compositionLocalOf { true }

/** Нижний отступ под плавающий док (0, когда док скрыт). */
val LocalBottomPad = compositionLocalOf { 0.dp }

@Composable
fun glassBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)

@Composable
fun listPadding(h: Dp = 16.dp, top: Dp = 16.dp): PaddingValues =
    PaddingValues(start = h, end = h, top = top, bottom = 16.dp + LocalBottomPad.current)

/** Плавное появление элемента списка (первые несколько — лесенкой). */
@Composable
fun Modifier.appear(index: Int): Modifier {
    if (!LocalAnim.current || index > 9) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        p.animateTo(1f, tween(380))
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 36f
    }
}

fun installApk(ctx: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не удалось открыть установщик: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
