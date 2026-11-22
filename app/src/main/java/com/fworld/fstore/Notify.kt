package com.fworld.fstore

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Настройки поведения: уведомления, режим «Только Wi-Fi», сортировка списка. */
object AppPrefs {
    private var sp: SharedPreferences? = null

    var notifyRelease by mutableStateOf(true); private set
    var notifyStart by mutableStateOf(true); private set
    var notifyDone by mutableStateOf(true); private set
    var wifiOnly by mutableStateOf(false); private set

    /** 0 — сначала новые, 1 — по названию, 2 — по размеру, 3 — по рейтингу. */
    var sort by mutableStateOf(0); private set

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("fstore_behavior", 0)
        sp = p
        notifyRelease = p.getBoolean("nRelease", true)
        notifyStart = p.getBoolean("nStart", true)
        notifyDone = p.getBoolean("nDone", true)
        wifiOnly = p.getBoolean("wifiOnly", false)
        sort = p.getInt("sort", 0).coerceIn(0, 3)
    }

    fun asked(): Boolean = sp?.getBoolean("askedNotif", false) ?: true
    fun markAsked() { sp?.edit()?.putBoolean("askedNotif", true)?.apply() }

    // @JvmName: у delegated-свойств с private set есть JVM-метод setX, он конфликтует с этими функциями.
    @JvmName("applyNotifyRelease")
    fun setNotifyRelease(v: Boolean) { notifyRelease = v; sp?.edit()?.putBoolean("nRelease", v)?.apply() }
    @JvmName("applyNotifyStart")
    fun setNotifyStart(v: Boolean) { notifyStart = v; sp?.edit()?.putBoolean("nStart", v)?.apply() }
    @JvmName("applyNotifyDone")
    fun setNotifyDone(v: Boolean) { notifyDone = v; sp?.edit()?.putBoolean("nDone", v)?.apply() }
    @JvmName("applyWifiOnly")
    fun setWifiOnly(v: Boolean) { wifiOnly = v; sp?.edit()?.putBoolean("wifiOnly", v)?.apply() }
    @JvmName("applySort")
    fun setSort(v: Int) { sort = v; sp?.edit()?.putInt("sort", v)?.apply() }
}

object Notifier {
    private const val CH_PROGRESS = "downloads"
    private const val CH_DONE = "downloads_done"
    private const val CH_REL = "releases"

    /** Каналы нужно создавать до первого уведомления — в том числе из фонового воркера. */
    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Скачивание началось", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_DONE, "Скачивание завершено", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CH_REL, "Новые релизы", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    /** Выдано ли системное разрешение (на Android 12 и ниже оно не требуется). */
    fun permissionGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Реально ли система покажет уведомления (разрешение + не выключены в настройках приложения). */
    fun allowed(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Системный экран уведомлений fStore — на случай, если пользователь отказал в разрешении. */
    fun openSystemSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, channel: String, title: String, text: String) {
        if (!allowed(ctx)) return
        init(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (channel == CH_PROGRESS) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    // «Началось» и «Скачано» используют один id: второе уведомление заменяет первое.
    fun downloadStarted(ctx: Context, name: String) {
        if (AppPrefs.notifyStart) post(ctx, ("dl:$name").hashCode(), CH_PROGRESS, "Началось скачивание", name)
    }

    fun downloadDone(ctx: Context, name: String) {
        if (AppPrefs.notifyDone) post(ctx, ("dl:$name").hashCode(), CH_DONE, "Скачано", "$name — открой «Загрузки» в настройках, чтобы установить")
    }

    fun release(ctx: Context, id: String, title: String, text: String) {
        post(ctx, ("rel:$id").hashCode(), CH_REL, title, text)
    }

    /** Кнопка «Проверить уведомления» в настройках. */
    fun test(ctx: Context) {
        post(ctx, 7001, CH_DONE, "Уведомления работают", "fStore сможет сообщать о новых релизах и загрузках")
    }

    fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

/** Следит за каталогом: новое приложение или новая версия → уведомление «новый релиз». */
object ReleaseWatch {
    private const val WORK_PERIODIC = "release-watch"
    private const val WORK_NOW = "release-watch-now"

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("fstore_releases", 0)

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Включает или выключает фоновую проверку. Периодическая проверка идёт примерно раз в 15 минут
     * (минимум, который разрешает Android; в режиме экономии батареи система может отложить её),
     * плюс одна проверка сразу при запуске.
     */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (!AppPrefs.notifyRelease) {
            wm.cancelUniqueWork(WORK_PERIODIC)
            wm.cancelUniqueWork(WORK_NOW)
            return
        }
        wm.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReleaseWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
        )
        wm.enqueueUniqueWork(
            WORK_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReleaseWorker>().setConstraints(online).build(),
        )
    }

    /** Первый вызов только запоминает текущие релизы, чтобы не засыпать уведомлениями. */
    fun process(ctx: Context, apps: List<AppItem>) {
        val p = sp(ctx)
        val raw = p.getString("known", null)
        val approved = apps.filter { it.status == "approved" }
        val now = JSONObject()
        approved.forEach { now.put(it.id, it.version) }
        if (raw != null && AppPrefs.notifyRelease) {
            val old = try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
            for (a in approved) {
                if (!old.has(a.id)) {
                    Notifier.release(ctx, a.id, "Новое приложение", "${a.name} v${a.version}")
                } else if (old.optString(a.id) != a.version) {
                    Notifier.release(ctx, a.id, "Вышло обновление", "${a.name} → v${a.version}")
                }
            }
        }
        p.edit().putString("known", now.toString()).apply()
    }

    suspend fun check(ctx: Context) {
        val apps = Gh.readArray("data/apps.json").objs().map { AppItem.from(it) }
        process(ctx, apps)
    }
}

class ReleaseWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        // Воркер может стартовать в «холодном» процессе без Activity — готовим всё сами.
        AppPrefs.init(applicationContext)
        Notifier.init(applicationContext)
        ReleaseWatch.check(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
