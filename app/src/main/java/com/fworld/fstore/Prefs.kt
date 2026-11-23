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

    /** Сила «жидкого стекла»: 0 — выключено, 1 — как раньше, 1.8 — совсем желе. */
    var liquid by mutableStateOf(1f); private set

    /** Подсветка-рамка вокруг карточки: белая у проверенных, красная у остальных. */
    var glow by mutableStateOf(true); private set

    /** Яркость свечения рамки. */
    var glowPower by mutableStateOf(1f); private set

    /** Скругление карточек приложений, dp. */
    var corner by mutableStateOf(28); private set

    /** Значок «сколько скачали» на карточке. */
    var showDownloads by mutableStateOf(true); private set

    /** Аватарки в отзывах. */
    var reviewAvatars by mutableStateOf(true); private set

    /** Полка «Недавно открытые» на главной. */
    var showRecent by mutableStateOf(true); private set

    /** Средняя оценка на карточке. */
    var showRating by mutableStateOf(true); private set

    /** Каталог сеткой в две колонки. */
    var grid by mutableStateOf(false); private set

    /** Карточка сжимается под пальцем. */
    var pressFx by mutableStateOf(true); private set

    /** Док можно зажать и перетащить в любое место экрана. */
    var dockDrag by mutableStateOf(true); private set

    /** Куда пользователь утащил док (смещение от обычного места, px). */
    var dockX by mutableStateOf(0f); private set
    var dockY by mutableStateOf(0f); private set

    /** Растёт при «вернуть док на место» — сам док следит за этим счётчиком. */
    var dockToken by mutableStateOf(0); private set

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
        liquid = p.getFloat("liquid", 1f).coerceIn(0f, 1.8f)
        glow = p.getBoolean("glow", true)
        glowPower = p.getFloat("glowPower", 1f).coerceIn(0.4f, 1.8f)
        corner = p.getInt("corner", 28).coerceIn(12, 34)
        showDownloads = p.getBoolean("showDownloads", true)
        reviewAvatars = p.getBoolean("reviewAvatars", true)
        grid = p.getBoolean("grid", false)
        showRecent = p.getBoolean("showRecent", true)
        showRating = p.getBoolean("showRating", true)
        pressFx = p.getBoolean("pressFx", true)
        dockDrag = p.getBoolean("dockDrag", true)
        dockX = p.getFloat("dockX", 0f)
        dockY = p.getFloat("dockY", 0f)
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

    @JvmName("applyLiquid")
    fun setLiquid(v: Float) { liquid = v.coerceIn(0f, 1.8f); sp?.edit()?.putFloat("liquid", liquid)?.apply() }
    @JvmName("applyGlow")
    fun setGlow(v: Boolean) { glow = v; sp?.edit()?.putBoolean("glow", v)?.apply() }
    @JvmName("applyGlowPower")
    fun setGlowPower(v: Float) { glowPower = v.coerceIn(0.4f, 1.8f); sp?.edit()?.putFloat("glowPower", glowPower)?.apply() }
    @JvmName("applyCorner")
    fun setCorner(v: Int) { corner = v.coerceIn(12, 34); sp?.edit()?.putInt("corner", corner)?.apply() }
    @JvmName("applyShowDownloads")
    fun setShowDownloads(v: Boolean) { showDownloads = v; sp?.edit()?.putBoolean("showDownloads", v)?.apply() }
    @JvmName("applyReviewAvatars")
    fun setReviewAvatars(v: Boolean) { reviewAvatars = v; sp?.edit()?.putBoolean("reviewAvatars", v)?.apply() }
    @JvmName("applyShowRecent")
    fun setShowRecent(v: Boolean) { showRecent = v; sp?.edit()?.putBoolean("showRecent", v)?.apply() }
    @JvmName("applyShowRating")
    fun setShowRating(v: Boolean) { showRating = v; sp?.edit()?.putBoolean("showRating", v)?.apply() }
    @JvmName("applyGrid")
    fun setGrid(v: Boolean) { grid = v; sp?.edit()?.putBoolean("grid", v)?.apply() }
    @JvmName("applyPressFx")
    fun setPressFx(v: Boolean) { pressFx = v; sp?.edit()?.putBoolean("pressFx", v)?.apply() }
    @JvmName("applyDockDrag")
    fun setDockDrag(v: Boolean) { dockDrag = v; sp?.edit()?.putBoolean("dockDrag", v)?.apply() }

    /** Запомнить, куда утащили док (px от обычного места). */
    fun setDock(x: Float, y: Float) {
        dockX = x; dockY = y
        sp?.edit()?.putFloat("dockX", x)?.putFloat("dockY", y)?.apply()
    }

    /** Вернуть док на обычное место: он сам пружинкой уедет назад. */
    fun resetDock() {
        setDock(0f, 0f)
        dockToken += 1
    }

    fun reset() {
        setAccent(0); setFontScale(1f); setCompactCards(false)
        setShowDesc(true); setShowSize(false); setHaptics(true)
        setLiquid(1f); setGlow(true); setGlowPower(1f); setCorner(28)
        setShowDownloads(true); setReviewAvatars(true); setShowRecent(true); setShowRating(true); setGrid(false); setPressFx(true)
        setDockDrag(true); resetDock()
    }
}
