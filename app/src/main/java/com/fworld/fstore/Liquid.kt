package com.fworld.fstore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.tanh

/**
 * «Жидкое стекло»: зажми элемент и потяни. Он тянется за пальцем с сопротивлением, растягивается
 * в сторону движения, подсвечивается в точке касания, а при отпускании пружинит назад, как желе.
 * Обычные тапы и прокрутка списков не затрагиваются — жест включается только после долгого нажатия.
 */
@Composable
fun Modifier.liquid(strength: Float = 1f): Modifier {
    if (!LocalAnim.current) return this
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val off = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val press = remember { Animatable(0f) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val maxPull = with(density) { 64.dp.toPx() } * strength

    fun release() {
        scope.launch { press.animateTo(0f, tween(350)) }
        scope.launch { off.animateTo(Offset.Zero, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessLow)) }
    }

    return this
        .onSizeChanged { box = it }
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { p ->
                    touch = p
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) }
                },
                onDragEnd = { release() },
                onDragCancel = { release() },
                onDrag = { change, delta ->
                    change.consume()
                    touch = change.position
                    val raw = off.value + delta
                    val d = raw.getDistance()
                    // резиновое сопротивление: чем дальше, тем тяжелее тянуть
                    val k = if (d <= 0f) 1f else (maxPull * tanh(d / maxPull)) / d
                    scope.launch { off.snapTo(raw * k) }
                },
            )
        }
        .graphicsLayer {
            val o = off.value
            val w = box.width.coerceAtLeast(1).toFloat()
            val h = box.height.coerceAtLeast(1).toFloat()
            val sx = abs(o.x) / w
            val sy = abs(o.y) / h
            val p = press.value
            translationX = o.x
            translationY = o.y
            // растяжение вдоль движения и сжатие поперёк, как у капли
            scaleX = (1f + sx * 0.55f - sy * 0.25f) * (1f + 0.03f * p)
            scaleY = (1f + sy * 0.55f - sx * 0.25f) * (1f + 0.03f * p)
            rotationZ = o.x / maxPull * 3f
        }
        .drawWithContent {
            drawContent()
            val p = press.value
            if (p > 0.01f) {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.30f * p), Color.Transparent),
                        center = touch,
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            }
        }
}

/** Пульсирующее свечение и рамка: проверенным приложениям — белые, непроверенным — красные. */
@Composable
fun Modifier.statusGlow(shape: Shape, color: Color): Modifier {
    val anim = LocalAnim.current
    val pulse = if (anim) {
        val t = rememberInfiniteTransition(label = "statusGlow")
        val v by t.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "v",
        )
        v
    } else 0.7f
    return this
        .shadow((4 + 10 * pulse).dp, shape, ambientColor = color, spotColor = color)
        .border(1.2.dp, color.copy(alpha = 0.5f + 0.3f * pulse), shape)
}
