package com.fworld.fstore

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.sin
import kotlin.random.Random

/** Избранные приложения (хранятся на устройстве). */
object Favorites {
    private var sp: SharedPreferences? = null
    var ids by mutableStateOf(setOf<String>()); private set

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("fstore_features", 0)
        sp = p
        ids = p.getStringSet("fav", emptySet())?.toSet() ?: emptySet()
    }

    /** Возвращает true, если приложение теперь в избранном. */
    fun toggle(id: String): Boolean {
        val on = id !in ids
        ids = if (on) ids + id else ids - id
        sp?.edit()?.putStringSet("fav", ids)?.apply()
        return on
    }
}

/** Недавно открытые приложения. */
object Recents {
    private var sp: SharedPreferences? = null
    var ids by mutableStateOf(listOf<String>()); private set

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("fstore_features", 0)
        sp = p
        ids = (p.getString("recent", "") ?: "").split(",").filter { it.isNotBlank() }
    }

    fun add(id: String) {
        if (ids.firstOrNull() == id) return
        ids = (listOf(id) + ids.filter { it != id }).take(12)
        sp?.edit()?.putString("recent", ids.joinToString(","))?.apply()
    }
}

/** Системное окно «Поделиться» с названием, версией и разработчиком приложения. */
fun shareApp(ctx: Context, a: AppItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, a.name)
        putExtra(Intent.EXTRA_TEXT, "${a.name} v${a.version} — в fStore\nРазработчик: @${a.owner}\nНайди его в магазине fStore by #fworld")
    }
    ctx.startActivity(Intent.createChooser(send, "Поделиться приложением").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Считает частые тапы подряд: на N-м срабатывает пасхалка. */
class TapCounter(private val need: Int = 7, private val gapMs: Long = 1500L) {
    private var n = 0
    private var last = 0L

    fun tap(): Boolean {
        val now = System.currentTimeMillis()
        n = if (now - last > gapMs) 1 else n + 1
        last = now
        if (n >= need) {
            n = 0
            return true
        }
        return false
    }
}

val LocalEgg = staticCompositionLocalOf<() -> Unit> { {} }

private class Bubble(
    val x: Float, val size: Int, val speed: Int, val phase: Float,
    val wob: Float, val spin: Float, val icon: ImageVector,
)

private val EGG_ICONS = listOf(
    Icons.Default.Storefront, Icons.Default.CloudDownload, Icons.Default.Apps,
    Icons.Default.AutoAwesome, Icons.Default.Android, Icons.Default.InstallMobile,
)

/** Пасхалка: плавающие иконки стора и информация о создателях fStore. */
@Composable
fun EasterEgg(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF06000F), Color(0xFF1B0638), Color(0xFF06000F))))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() },
        ) {
            FloatingStoreIcons()
            EggCenter()
        }
    }
}

@Composable
private fun FloatingStoreIcons() {
    val bubbles = remember {
        val r = Random(42)
        List(22) { i ->
            Bubble(
                x = r.nextFloat(),
                size = 28 + r.nextInt(40),
                speed = 1 + r.nextInt(3),
                phase = r.nextFloat(),
                wob = 1f + r.nextInt(3),
                spin = (if (r.nextBoolean()) 1f else -1f) * (0.5f + r.nextFloat()),
                icon = EGG_ICONS[i % EGG_ICONS.size],
            )
        }
    }
    val t by rememberInfiniteTransition(label = "egg").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        bubbles.forEach { b ->
            // speed — целое число, поэтому петля бесшовная
            val prog = (t * b.speed + b.phase) % 1f
            Box(
                Modifier
                    .size(b.size.dp)
                    .graphicsLayer {
                        translationX = b.x * (w - b.size.dp.toPx()) + sin(prog * 6.2832f * b.wob) * 28f
                        translationY = h * (1.1f - prog * 1.3f)
                        rotationZ = prog * 360f * b.spin
                        alpha = (0.18f + 0.45f * sin(prog * 3.1416f)).coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(30))
                    .background(Brush.linearGradient(listOf(Color(0xFF3B0A7A), Color(0xFF9B30FF)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(b.icon, null, tint = Color.White, modifier = Modifier.size((b.size * 0.58f).dp))
            }
        }
    }
}

@Composable
private fun EggCenter() {
    val pop by animateFloatAsState(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow), label = "pop")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "p",
    )
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(32.dp)
            .graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop.coerceIn(0f, 1f) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(RoundedCornerShape(34.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3B0A7A), Color(0xFF9B30FF), Color(0xFF3B0A7A)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Storefront, null, tint = Color.White, modifier = Modifier.size(64.dp))
        }
        Text("fStore", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text("Создатель fStore", style = MaterialTheme.typography.labelLarge, color = Color(0xFFD1B3FF))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
        ) {
            Text(
                "void_fworld & #fworld",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFB388FF), Color.White, Color(0xFF9B30FF))),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
        Text("Нажми, чтобы закрыть", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
    }
}
