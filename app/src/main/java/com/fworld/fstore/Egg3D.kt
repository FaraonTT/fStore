@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fworld.fstore

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Пасхалка из «О приложении»: объёмный фон из форм Material 3, который можно зажать и потянуть —
 * слои едут с разной скоростью (параллакс), вся сцена наклоняется, а отпустишь — плавно возвращается.
 */
@Composable
fun Egg3DScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scene = remember { buildEggScene() }
    var backgroundIndex by remember { mutableStateOf(0) }
    val backgrounds = remember(cs) {
        listOf(
            listOf(cs.surfaceContainerHighest, cs.surface, cs.surfaceContainerLowest),
            listOf(Color(0xFF101A3A), Color(0xFF40246C), Color(0xFF13152D)),
            listOf(Color(0xFF062F38), Color(0xFF146B70), Color(0xFF071D32)),
            listOf(Color(0xFF451D36), Color(0xFF8D3E54), Color(0xFF241632)),
            listOf(Color(0xFF36220D), Color(0xFF93612C), Color(0xFF201831)),
            listOf(Color(0xFF152B23), Color(0xFF2D6551), Color(0xFF16283F)),
        )
    }
    val palette = remember(cs) {
        listOf(cs.primary, cs.tertiary, cs.secondary, cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer)
    }
    val clock by rememberInfiniteTransition(label = "egg3d").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
        label = "clock",
    )

    fun release() {
        scope.launch { drag.animateTo(Offset.Zero, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(backgrounds[backgroundIndex])),
    ) {
        // Сцена: наклоняется целиком, а внутри каждый слой едет по-своему.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { release() },
                        onDragCancel = { release() },
                        onDrag = { change, delta ->
                            change.consume()
                            scope.launch {
                                drag.snapTo(
                                    Offset(
                                        (drag.value.x + delta.x).coerceIn(-size.width * 0.32f, size.width * 0.32f),
                                        (drag.value.y + delta.y).coerceIn(-size.height * 0.28f, size.height * 0.28f),
                                    ),
                                )
                            }
                        },
                    )
                }
                .graphicsLayer {
                    rotationX = (-drag.value.y / size.height * 26f).coerceIn(-15f, 15f)
                    rotationY = (drag.value.x / size.width * 26f).coerceIn(-15f, 15f)
                    cameraDistance = 16f * density
                    scaleX = 1.2f
                    scaleY = 1.2f
                },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth
                val h = maxHeight
                scene.forEachIndexed { i, e ->
                    val c = palette[i % palette.size]
                    val side = e.dp.dp
                    Box(
                        Modifier
                            .offset(x = w * e.fx - side / 2, y = h * e.fy - side / 2)
                            .size(side)
                            .graphicsLayer {
                                val ring = (clock + e.phase) % 1f
                                translationX = drag.value.x * e.depth * 0.55f + sin(ring * 6.2832f) * 26f * e.depth
                                translationY = drag.value.y * e.depth * 0.55f + cos(ring * 6.2832f) * 18f * e.depth
                                rotationZ = clock * 360f * e.spin * 0.35f + e.phase * 90f
                                val s = 0.6f + 0.7f * e.depth
                                scaleX = s
                                scaleY = s
                                alpha = 0.28f + 0.55f * e.depth
                            }
                            .shadow((4 + 16 * e.depth).dp, e.shape, ambientColor = c, spotColor = c)
                            .clip(e.shape)
                            .background(
                                Brush.linearGradient(
                                    listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.55f)),
                                ),
                            ),
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "fStore 1.5",
                style = MaterialTheme.typography.displayMedium.copy(
                    brush = Brush.linearGradient(listOf(cs.primary, cs.tertiary, cs.primary)),
                    fontWeight = FontWeight.Black,
                ),
            )
            Text(
                "by @fworld_team",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(140.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, cs.primary.copy(alpha = 0.8f), Color.Transparent),
                        ),
                    ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Все права защищены.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Зажми и потяни фон — он объёмный",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        IconButton(
            onClick = { backgroundIndex = (backgroundIndex + 1) % backgrounds.size },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(Icons.Default.Palette, "Сменить фон", tint = cs.onSurface)
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(Icons.Default.Close, "Закрыть", tint = cs.onSurface)
        }
    }
}

private class EggShape(
    /** Базовая позиция в долях экрана. */
    val fx: Float,
    val fy: Float,
    /** Размер в dp. */
    val dp: Float,
    /** Глубина: чем больше, тем крупнее, быстрее едет и сильнее светится. */
    val depth: Float,
    val spin: Float,
    val phase: Float,
    val shape: Shape,
)

/** Формы Material 3: круг, скруглённый квадрат, «печенька» и мягкая звезда. */
private val EGG_SHAPES: List<Shape> = listOf(
    CircleShape,
    RoundedCornerShape(26),
    RoundedCornerShape(percent = 42),
    GenericShape { s, _ -> softStar(s) },
    GenericShape { s, _ -> cookie(s) },
)

private fun Path.softStar(s: Size) {
    val cx = s.width / 2f
    val cy = s.height / 2f
    val r = minOf(cx, cy)
    val k = r * 0.30f
    moveTo(cx, cy - r)
    quadraticTo(cx + k, cy - k, cx + r, cy)
    quadraticTo(cx + k, cy + k, cx, cy + r)
    quadraticTo(cx - k, cy + k, cx - r, cy)
    quadraticTo(cx - k, cy - k, cx, cy - r)
    close()
}

private fun Path.cookie(s: Size) {
    val cx = s.width / 2f
    val cy = s.height / 2f
    val r = minOf(cx, cy)
    val inner = r * 0.52f
    for (i in 0 until 10) {
        val ang = Math.toRadians(i * 36.0 - 90.0)
        val d = if (i % 2 == 0) r else inner
        val x = cx + (cos(ang) * d).toFloat()
        val y = cy + (sin(ang) * d).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun buildEggScene(): List<EggShape> {
    val r = java.util.Random(1701)
    return List(26) { i ->
        EggShape(
            fx = 0.04f + r.nextFloat() * 0.92f,
            fy = 0.04f + r.nextFloat() * 0.92f,
            dp = 34f + r.nextFloat() * 74f,
            depth = 0.25f + r.nextFloat() * 0.75f,
            spin = if (r.nextBoolean()) 1f else -1f,
            phase = r.nextFloat(),
            shape = EGG_SHAPES[i % EGG_SHAPES.size],
        )
    }
}
