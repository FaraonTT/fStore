package com.fworld.fstore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

fun slug(s: String): String {
    val r = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    return r.ifBlank { "app" }.take(30)
}

private fun randomSalt(): String {
    val b = ByteArray(12)
    SecureRandom().nextBytes(b)
    return b.joinToString("") { "%02x".format(it) }
}

private fun hashPw(pw: String, salt: String): String {
    val spec = PBEKeySpec(pw.toCharArray(), salt.toByteArray(), 30000, 256)
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        .encoded.joinToString("") { "%02x".format(it) }
}

class StoreVM(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("fstore", 0)

    var users by mutableStateOf(listOf<User>()); private set
    var apps by mutableStateOf(listOf<AppItem>()); private set
    var reviews by mutableStateOf(listOf<Review>()); private set
    var badges by mutableStateOf(listOf<BadgeDef>()); private set
    var me by mutableStateOf<User?>(null); private set
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set
    var stage by mutableStateOf(""); private set
    var message by mutableStateOf<String?>(null)

    init {
        refresh()
    }

    private suspend fun loadAll() = coroutineScope {
        val u = async { Gh.readArray("data/users.json") }
        val a = async { Gh.readArray("data/apps.json") }
        val r = async { Gh.readArray("data/reviews.json") }
        val b = async { Gh.readArray("data/badges.json") }
        users = u.await().objs().map { User.from(it) }
        apps = a.await().objs().map { AppItem.from(it) }
        reviews = r.await().objs().map { Review.from(it) }
        badges = b.await().objs().map { BadgeDef.from(it) }
        syncMe()
    }

    private fun syncMe() {
        val name = prefs.getString("user", null) ?: run { me = null; return }
        val h = prefs.getString("hash", null)
        val u = users.find { it.username == name }
        if (u != null && !u.banned && u.hash == h) {
            me = u
        } else {
            if (u?.banned == true) message = "Аккаунт заблокирован"
            me = null
            prefs.edit().clear().apply()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            try {
                loadAll()
            } catch (e: Exception) {
                message = "Нет связи с сервером: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    private fun act(stageText: String, onOk: () -> Unit = {}, block: suspend () -> Unit) {
        if (!Gh.ready) {
            message = "Сервер не настроен: в сборке нет токена FSTORE_TOKEN"
            return
        }
        viewModelScope.launch {
            busy = true
            stage = stageText
            progress = 0f
            try {
                block()
                loadAll()
                onOk()
            } catch (e: Exception) {
                message = e.message ?: "Ошибка"
            } finally {
                busy = false
            }
        }
    }

    // ---------- accounts ----------

    fun register(username: String, name: String, desc: String, pw: String, avatar: ByteArray?, onOk: () -> Unit) =
        act("Создаю аккаунт…", onOk) {
            val un = username.trim().lowercase()
            require(Regex("^[a-z0-9_]{3,20}$").matches(un)) { "Имя пользователя: 3–20 символов, a–z, 0–9 и _" }
            require(pw.length >= 6) { "Пароль — минимум 6 символов" }
            require(name.isNotBlank()) { "Введите имя" }
            val salt = randomSalt()
            val hash = withContext(Dispatchers.Default) { hashPw(pw, salt) }
            val now = System.currentTimeMillis()
            val user = User(username = un, name = name.trim(), desc = desc.trim(), salt = salt, hash = hash, updated = now)
            Gh.updateJson("data/users.json", "register $un") { arr ->
                val l = arr.objs()
                if (l.any { it.optString("username") == un }) throw IllegalStateException("Это имя пользователя занято")
                (l + user.toJson()).toJsonArray()
            }
            if (avatar != null) {
                val path = "data/avatars/$un.png"
                Gh.overwrite(path, avatar, "avatar $un")
                editUser(un, "avatar $un") { it.copy(avatar = path, updated = System.currentTimeMillis()) }
            }
            prefs.edit().putString("user", un).putString("hash", hash).apply()
        }

    fun login(username: String, pw: String, onOk: () -> Unit) = act("Вхожу…", onOk) {
        val un = username.trim().lowercase()
        val fresh = Gh.readArray("data/users.json").objs().map { User.from(it) }
        val u = fresh.find { it.username == un } ?: error("Пользователь не найден")
        val h = withContext(Dispatchers.Default) { hashPw(pw, u.salt) }
        check(h == u.hash) { "Неверный пароль" }
        check(!u.banned) { "Аккаунт заблокирован" }
        prefs.edit().putString("user", un).putString("hash", h).apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
        me = null
    }

    fun updateProfile(name: String, desc: String, avatar: ByteArray?, onOk: () -> Unit) =
        act("Сохраняю профиль…", onOk) {
            val m = me ?: error("Нужен вход")
            require(name.isNotBlank()) { "Введите имя" }
            var path = m.avatar
            if (avatar != null) {
                path = "data/avatars/${m.username}.png"
                Gh.overwrite(path, avatar, "avatar ${m.username}")
            }
            val finalPath = path
            editUser(m.username, "profile ${m.username}") {
                it.copy(name = name.trim(), desc = desc.trim(), avatar = finalPath, updated = System.currentTimeMillis())
            }
        }

    private suspend fun editUser(un: String, msg: String, f: (User) -> User) {
        Gh.updateJson("data/users.json", msg) { arr ->
            arr.objs().map { User.from(it) }
                .map { if (it.username == un) f(it) else it }
                .map { it.toJson() }.toJsonArray()
        }
    }

    // ---------- apps ----------

    private suspend fun editApps(msg: String, f: (List<AppItem>) -> List<AppItem>) {
        Gh.updateJson("data/apps.json", msg) { arr ->
            f(arr.objs().map { AppItem.from(it) }).map { it.toJson() }.toJsonArray()
        }
    }

    private suspend fun cleanup(vararg paths: String) {
        for (p in paths) {
            if (p.isBlank()) continue
            try {
                Gh.delete(p, "cleanup")
            } catch (_: Exception) {
            }
        }
    }

    fun submitNew(
        name: String, version: String, shortDesc: String, desc: String,
        icon: ByteArray, apk: File, onOk: () -> Unit,
    ) = act("Загружаю иконку…", onOk) {
        val m = me ?: error("Нужен вход")
        require(name.isNotBlank() && version.isNotBlank()) { "Заполните название и версию" }
        val id = slug(name) + "-" + System.currentTimeMillis().toString(36)
        val dir = "apps/${m.username}"
        val iconPath = "$dir/$id.png"
        val apkPath = "$dir/$id-${slug(version)}.apk"
        val size = apk.length()
        Gh.overwrite(iconPath, icon, "icon $id")
        stage = "Загружаю APK…"
        Gh.putFile(apkPath, apk, "apk $id $version") { progress = it }
        val now = System.currentTimeMillis()
        editApps("new app $id") {
            it + AppItem(
                id = id, owner = m.username, name = name.trim(), version = version.trim(),
                shortDesc = shortDesc.trim(), desc = desc.trim(), icon = iconPath, apk = apkPath,
                size = size, status = "pending", created = now, updated = now,
            )
        }
        apk.delete()
    }

    fun submitUpdate(
        appId: String, name: String, version: String, whatsNew: String,
        icon: ByteArray?, apk: File, onOk: () -> Unit,
    ) = act("Загружаю обновление…", onOk) {
        val m = me ?: error("Нужен вход")
        val a = apps.find { it.id == appId && it.owner == m.username } ?: error("Приложение не найдено")
        require(name.isNotBlank() && version.isNotBlank()) { "Заполните название и версию" }
        val ts = System.currentTimeMillis()
        val dir = "apps/${m.username}"
        var iconPath = ""
        if (icon != null) {
            iconPath = "$dir/$appId-icon-$ts.png"
            Gh.overwrite(iconPath, icon, "icon $appId")
        }
        val apkPath = "$dir/$appId-${slug(version)}-$ts.apk"
        val size = apk.length()
        stage = "Загружаю APK…"
        Gh.putFile(apkPath, apk, "apk update $appId $version") { progress = it }
        val p = PendingUpdate(name.trim(), version.trim(), whatsNew.trim(), iconPath, apkPath, size, ts)
        editApps("update $appId") { l -> l.map { if (it.id == appId) it.copy(pending = p) else it } }
        a.pending?.let { cleanup(it.apk, it.icon) }
        apk.delete()
    }

    fun approve(id: String) = act("Одобряю…") {
        val m = me ?: error("Нужен вход")
        check(m.isAdmin) { "Нет прав" }
        var oldApk = ""
        var oldIcon = ""
        val now = System.currentTimeMillis()
        editApps("approve $id") { l ->
            l.map { a ->
                if (a.id != id) a else {
                    val p = a.pending
                    if (p == null) {
                        a.copy(status = "approved", updated = now)
                    } else {
                        oldApk = a.apk
                        oldIcon = if (p.icon.isNotBlank()) a.icon else ""
                        a.copy(
                            name = p.name, version = p.version, whatsNew = p.whatsNew, apk = p.apk,
                            size = p.size, icon = p.icon.ifBlank { a.icon }, status = "approved",
                            pending = null, updated = now,
                        )
                    }
                }
            }
        }
        cleanup(oldApk, oldIcon)
    }

    fun reject(id: String) = act("Отклоняю…") {
        val m = me ?: error("Нужен вход")
        check(m.isAdmin) { "Нет прав" }
        var dropApk = ""
        var dropIcon = ""
        editApps("reject $id") { l ->
            l.map { a ->
                if (a.id != id) a else {
                    val p = a.pending
                    if (p != null) {
                        dropApk = p.apk
                        dropIcon = p.icon
                        a.copy(pending = null)
                    } else {
                        a.copy(status = "rejected")
                    }
                }
            }
        }
        cleanup(dropApk, dropIcon)
    }

    fun setFworld(id: String, on: Boolean) = act("Сохраняю…") {
        val m = me ?: error("Нужен вход")
        check(m.isAdmin) { "Нет прав" }
        editApps("fworld badge $id") { l -> l.map { if (it.id == id) it.copy(fworld = on) else it } }
    }

    fun deleteApp(id: String, onOk: () -> Unit = {}) = act("Удаляю…", onOk) {
        val a = apps.find { it.id == id } ?: error("Приложение не найдено")
        val m = me ?: error("Нужен вход")
        check(m.isAdmin || m.username == a.owner) { "Нет прав" }
        editApps("delete $id") { l -> l.filter { it.id != id } }
        Gh.updateJson("data/reviews.json", "delete reviews $id") { arr ->
            arr.objs().filter { it.optString("appId") != id }.toJsonArray()
        }
        cleanup(a.apk, a.icon, a.pending?.apk ?: "", a.pending?.icon ?: "")
    }

    // ---------- reviews ----------

    fun submitReview(appId: String, stars: Int, text: String, onOk: () -> Unit) = act("Отправляю отзыв…", onOk) {
        val m = me ?: error("Нужен вход")
        require(stars in 1..5) { "Поставьте оценку от 1 до 5" }
        val rid = "$appId:${m.username}"
        val now = System.currentTimeMillis()
        Gh.updateJson("data/reviews.json", "review $rid") { arr ->
            val l = arr.objs().map { Review.from(it) }
            val old = l.find { it.id == rid }
            val nr = Review(rid, appId, m.username, stars, text.trim(), now, old?.reply ?: "", old?.replyTime ?: 0L)
            (l.filter { it.id != rid } + nr).map { it.toJson() }.toJsonArray()
        }
    }

    fun reply(reviewId: String, text: String, onOk: () -> Unit) = act("Отправляю ответ…", onOk) {
        val m = me ?: error("Нужен вход")
        val appId = reviewId.substringBefore(":")
        val a = apps.find { it.id == appId } ?: error("Приложение не найдено")
        check(a.owner == m.username || m.isAdmin) { "Отвечать может только разработчик" }
        val now = System.currentTimeMillis()
        Gh.updateJson("data/reviews.json", "reply $reviewId") { arr ->
            arr.objs().map { Review.from(it) }
                .map { if (it.id == reviewId) it.copy(reply = text.trim(), replyTime = now) else it }
                .map { it.toJson() }.toJsonArray()
        }
    }

    // ---------- admin ----------

    fun adminEditUser(un: String, ownerOnly: Boolean = false, f: (User) -> User) = act("Сохраняю…") {
        val m = me ?: error("Нужен вход")
        check(m.isAdmin) { "Нет прав" }
        check(!ownerOnly || m.isOwner) { "Только владелец" }
        check(un != OWNER_ADMIN || m.isOwner) { "Нельзя менять владельца" }
        editUser(un, "admin edit $un", f)
    }

    fun createBadge(
        name: String, desc: String, kind: String, value: String, image: ByteArray?, onOk: () -> Unit,
    ) = act("Создаю бейдж…", onOk) {
        val m = me ?: error("Нужен вход")
        check(m.isOwner) { "Только владелец" }
        require(name.isNotBlank()) { "Введите название бейджа" }
        val id = "b" + System.currentTimeMillis().toString(36)
        var v = value.trim()
        if (kind == "image") {
            require(image != null) { "Выберите фото" }
            v = "data/badges/$id.png"
            Gh.overwrite(v, image, "badge $id")
        } else {
            require(v.isNotBlank()) { "Укажите эмодзи или цвет" }
        }
        val def = BadgeDef(id, name.trim(), desc.trim(), kind, v)
        Gh.updateJson("data/badges.json", "badge $id") { arr -> (arr.objs() + def.toJson()).toJsonArray() }
    }

    fun deleteBadge(id: String) = act("Удаляю бейдж…") {
        val m = me ?: error("Нужен вход")
        check(m.isOwner) { "Только владелец" }
        val img = badges.find { it.id == id }?.takeIf { it.kind == "image" }?.value ?: ""
        Gh.updateJson("data/badges.json", "delete badge $id") { arr ->
            arr.objs().filter { it.optString("id") != id }.toJsonArray()
        }
        cleanup(img)
    }
}
