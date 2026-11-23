package com.fworld.fstore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Счётчик скачиваний приложений. Живёт в отдельном файле data/downloads.json, поэтому правки
 * каталога (проверка, обновление, удаление) его не затирают.
 *
 * Одно скачивание = один аккаунт: повторная загрузка тем же пользователем счётчик не двигает,
 * для каждого приложения хранится список тех, кто его уже скачал.
 */
object DownloadStats {
    private const val PATH = "data/downloads.json"

    /** appId → сколько разных аккаунтов скачали. */
    var counts by mutableStateOf<Map<String, Int>>(emptyMap()); private set

    /** Приложения, которые уже засчитаны текущему пользователю. */
    var mine by mutableStateOf<Set<String>>(emptySet()); private set

    fun count(appId: String): Int = counts[appId] ?: 0

    fun counted(appId: String): Boolean = appId in mine

    suspend fun load(username: String?) {
        val arr = try {
            Gh.readArray(PATH)
        } catch (e: Exception) {
            return
        }
        val map = mutableMapOf<String, Int>()
        val own = mutableSetOf<String>()
        arr.objs().forEach { o ->
            val id = o.optString("appId")
            if (id.isBlank()) return@forEach
            val users = o.optJSONArray("users") ?: JSONArray()
            map[id] = users.length()
            if (username != null) {
                for (i in 0 until users.length()) {
                    if (users.optString(i) == username) {
                        own += id
                        break
                    }
                }
            }
        }
        counts = map
        mine = own
    }

    /** +1 скачивание, но не больше одного раза на аккаунт. */
    suspend fun tick(appId: String, username: String) {
        if (appId.isBlank() || username.isBlank() || appId in mine) return
        var already = false
        var total = 0
        try {
            Gh.updateJson(PATH, "download $appId") { arr ->
                val list = arr.objs().toMutableList()
                val row = list.find { it.optString("appId") == appId }
                    ?: JSONObject().put("appId", appId).put("users", JSONArray()).also { list += it }
                val users = row.optJSONArray("users") ?: JSONArray()
                val names = (0 until users.length()).map { users.optString(it) }
                if (username in names) {
                    already = true
                    total = names.size
                } else {
                    val next = JSONArray()
                    names.forEach { next.put(it) }
                    next.put(username)
                    row.put("users", next)
                    total = names.size + 1
                }
                list.toJsonArray()
            }
        } catch (e: Exception) {
            return
        }
        if (already) {
            mine = mine + appId
            return
        }
        counts = counts + (appId to total)
        mine = mine + appId
    }
}

/** «1 234» → «1,2 тыс»: короткая подпись для значка. */
fun fmtCount(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1f млн", n / 1_000_000f)
    n >= 1_000 -> String.format("%.1f тыс", n / 1_000f)
    else -> n.toString()
}

/** Средние оценки приложений для значка на карточке (обновляется при загрузке каталога). */
object RatingCache {
    var avg by androidx.compose.runtime.mutableStateOf(mapOf<String, Float>())
        private set

    fun update(reviews: List<Review>) {
        avg = reviews.groupBy { it.appId }.mapValues { e -> e.value.sumOf { it.stars }.toFloat() / e.value.size }
    }
}
