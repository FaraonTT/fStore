package com.fworld.fstore

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
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
