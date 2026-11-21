package com.fworld.fstore

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Дополнительные настройки интерфейса (отдельно от сессии: выход из аккаунта их не стирает). */
object UiPrefs {
    private var sp: SharedPreferences? = null

    /** Акцентный цвет (индекс в [ACCENTS]); работает, когда выключены «Цвета из обоев». */
    var accent by mutableStateOf(0); private set

    /** Множитель размера текста: 0.85 … 1.3. */
    var fontScale by mutableStateOf(1f); private set

    /** Компактные карточки приложений в списках. */
    var compactCards by mutableStateOf(false); private set

    /** Показывать краткое описание в карточке. */
    var showDesc by mutableStateOf(true); private set

    /** Показывать размер файла в карточке. */
    var showSize by mutableStateOf(false); private set

    /** Вибрация при нажатии на кнопки дока. */
    var haptics by mutableStateOf(true); private set

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("fstore_ui", 0)
        sp = p
        accent = p.getInt("accent", 0).coerceIn(0, ACCENTS.lastIndex)
        fontScale = p.getFloat("fontScale", 1f).coerceIn(0.85f, 1.3f)
        compactCards = p.getBoolean("compact", false)
        showDesc = p.getBoolean("showDesc", true)
        showSize = p.getBoolean("showSize", false)
        haptics = p.getBoolean("haptics", true)
    }

    // @JvmName: у delegated-свойств с private set есть JVM-метод setX, он конфликтует с этими функциями.
    @JvmName("applyAccent")
    fun setAccent(v: Int) { accent = v; sp?.edit()?.putInt("accent", v)?.apply() }
    @JvmName("applyFontScale")
    fun setFontScale(v: Float) { fontScale = v; sp?.edit()?.putFloat("fontScale", v)?.apply() }
    @JvmName("applyCompactCards")
    fun setCompactCards(v: Boolean) { compactCards = v; sp?.edit()?.putBoolean("compact", v)?.apply() }
    @JvmName("applyShowDesc")
    fun setShowDesc(v: Boolean) { showDesc = v; sp?.edit()?.putBoolean("showDesc", v)?.apply() }
    @JvmName("applyShowSize")
    fun setShowSize(v: Boolean) { showSize = v; sp?.edit()?.putBoolean("showSize", v)?.apply() }
    @JvmName("applyHaptics")
    fun setHaptics(v: Boolean) { haptics = v; sp?.edit()?.putBoolean("haptics", v)?.apply() }

    fun reset() {
        setAccent(0); setFontScale(1f); setCompactCards(false)
        setShowDesc(true); setShowSize(false); setHaptics(true)
    }
}
