@file:OptIn(ExperimentalMaterial3Api::class)

package com.fworld.fstore

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

/** Включены ли анимации (настройка «Анимации»). */
val LocalAnim = compositionLocalOf { true }

/** Нижний отступ под плавающий док (0, когда док скрыт). */
val LocalBottomPad = compositionLocalOf { 0.dp }

/** Открыть страницу пользователя по его username. null — имена не кликабельны (например, в админ-панели). */
val LocalOpenUser = compositionLocalOf<((String) -> Unit)?> { null }

@Composable
fun glassBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)

@Composable
fun listPadding(h: Dp = 16.dp, top: Dp = 16.dp): PaddingValues =
    PaddingValues(start = h, end = h, top = top, bottom = 16.dp + LocalBottomPad.current)

/** Чёрно-фиолетовая палитра бейджа #fworld. */
val FW_COLORS = listOf(
    Color(0xFF06000F), Color(0xFF3B0A7A), Color(0xFF9B30FF), Color(0xFF3B0A7A), Color(0xFF06000F),
)

/** Кнопка/плашка #fworld: переливающийся чёрно-фиолетовый градиент. */
@Composable
fun fworldBrush(w: Float, h: Float): Brush {
    val phase = fworldPhase()
    val shift = w * 2f * phase
    return Brush.linearGradient(FW_COLORS, start = Offset(-w + shift, 0f), end = Offset(shift, h), tileMode = TileMode.Repeated)
}

@Composable
private fun fworldPhase(): Float {
    if (!LocalAnim.current) return 0.35f
    val t = rememberInfiniteTransition(label = "fwPhase")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    return p
}

/**
 * Мягкое свечение СНАРУЖИ фигуры. Раньше здесь был Modifier.shadow: у прозрачной карточки
 * системная тень просвечивает внутрь и рисует тёмный прямоугольник под содержимым.
 * Здесь свечение рисуется слоями-обводками и вырезается по контуру карточки, внутрь не попадает.
 */
fun Modifier.softGlow(shape: Shape, color: Color, radius: Dp, strength: Float): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val hole = Path()
    when (outline) {
        is Outline.Rounded -> hole.addRoundRect(outline.roundRect)
        is Outline.Rectangle -> hole.addRect(outline.rect)
        is Outline.Generic -> hole.addPath(outline.path)
    }
    val r = radius.toPx()
    clipPath(hole, ClipOp.Difference) {
        val steps = 8
        for (i in steps downTo 1) {
            val f = i / steps.toFloat()
            val a = (strength * (1f - f) * 0.5f + 0.01f).coerceIn(0f, 1f)
            drawOutline(outline, color.copy(alpha = a), style = Stroke(width = r * 2f * f))
        }
    }
}

/** Свечение и переливающаяся рамка для приложений с бейджем #fworld. */
@Composable
fun Modifier.fworldGlow(shape: Shape): Modifier {
    val anim = LocalAnim.current
    val phase = fworldPhase()
    val pulse = if (anim) {
        val t = rememberInfiniteTransition(label = "fwPulse")
        val v by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "v")
        v
    } else 0.7f
    val glow = Color(0xFF9B30FF)
    return this
        .softGlow(shape, glow, (8 + 10 * pulse).dp, pulse)
        .drawWithContent {
            drawContent()
            val w = size.width
            val h = size.height
            val shift = w * 2f * phase
            val brush = Brush.linearGradient(
                FW_COLORS, start = Offset(-w + shift, 0f), end = Offset(shift, h), tileMode = TileMode.Repeated,
            )
            val o = shape.createOutline(size, layoutDirection, this)
            drawOutline(o, brush, alpha = 0.16f)
            drawOutline(o, brush, style = Stroke(2.5.dp.toPx()))
        }
}

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

/** Аватарка на весь экран: щипок — масштаб, двойной тап — приблизить/сбросить, крестик — закрыть. */
@Composable
fun AvatarViewer(u: User?, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (u != null && u.avatar.isNotBlank()) {
                    AsyncImage(
                        model = Gh.rawUrl(u.avatar, u.updated),
                        contentDescription = u.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (u?.name?.firstOrNull() ?: '?').uppercase(),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (u != null) {
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(u.name, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
                    Text("@${u.username}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(24.dp))
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 14.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "Закрыть") }
            }
        }
    }
}
