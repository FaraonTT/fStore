package com.fworld.fstore

import org.json.JSONArray
import org.json.JSONObject

const val OWNER_ADMIN = "void_fworld"

fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

fun List<JSONObject>.toJsonArray(): JSONArray {
    val a = JSONArray()
    forEach { a.put(it) }
    return a
}

data class User(
    val username: String,
    val name: String,
    val desc: String = "",
    val avatar: String = "",
    val salt: String = "",
    val hash: String = "",
    val role: String = "user",
    val banned: Boolean = false,
    val verified: Boolean = false,
    val glow: Boolean = false,
    val nameColor: String = "",
    val glowColor: String = "",
    val badges: List<String> = emptyList(),
    val friend: Boolean = false,
    val virus: Boolean = false,
    val updated: Long = 0L,
) {
    val isOwner: Boolean get() = username == OWNER_ADMIN
    val isAdmin: Boolean get() = isOwner || role == "admin"
    val isModerator: Boolean get() = isAdmin || role == "moderator"

    fun toJson(): JSONObject = JSONObject()
        .put("username", username)
        .put("name", name)
        .put("desc", desc)
        .put("avatar", avatar)
        .put("salt", salt)
        .put("hash", hash)
        .put("role", role)
        .put("banned", banned)
        .put("verified", verified)
        .put("glow", glow)
        .put("nameColor", nameColor)
        .put("glowColor", glowColor)
        .put("badges", JSONArray(badges))
        .put("friend", friend)
        .put("virus", virus)
        .put("updated", updated)

    companion object {
        fun from(o: JSONObject): User {
            val b = o.optJSONArray("badges") ?: JSONArray()
            return User(
                username = o.optString("username"),
                name = o.optString("name"),
                desc = o.optString("desc"),
                avatar = o.optString("avatar"),
                salt = o.optString("salt"),
                hash = o.optString("hash"),
                role = o.optString("role", "user"),
                banned = o.optBoolean("banned"),
                verified = o.optBoolean("verified"),
                glow = o.optBoolean("glow"),
                nameColor = o.optString("nameColor"),
                glowColor = o.optString("glowColor"),
                badges = (0 until b.length()).map { b.getString(it) },
                friend = o.optBoolean("friend"),
                virus = o.optBoolean("virus"),
                updated = o.optLong("updated"),
            )
        }
    }
}

data class PendingUpdate(
    val name: String,
    val version: String,
    val whatsNew: String,
    val icon: String,
    val apk: String,
    val size: Long,
    val time: Long,
    val parts: Int = 1,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("version", version)
        .put("whatsNew", whatsNew)
        .put("icon", icon)
        .put("apk", apk)
        .put("size", size)
        .put("time", time)
        .put("parts", parts)

    companion object {
        fun from(o: JSONObject) = PendingUpdate(
            o.optString("name"),
            o.optString("version"),
            o.optString("whatsNew"),
            o.optString("icon"),
            o.optString("apk"),
            o.optLong("size"),
            o.optLong("time"),
            o.optInt("parts", 1),
        )
    }
}

data class AppItem(
    val id: String,
    val owner: String,
    val name: String,
    val version: String,
    val shortDesc: String,
    val desc: String,
    val whatsNew: String = "",
    val icon: String,
    val apk: String,
    val size: Long,
    val status: String = "pending",
    val fworld: Boolean = false,
    val created: Long,
    val updated: Long,
    val pending: PendingUpdate? = null,
    val parts: Int = 1,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("id", id)
            .put("owner", owner)
            .put("name", name)
            .put("version", version)
            .put("shortDesc", shortDesc)
            .put("desc", desc)
            .put("whatsNew", whatsNew)
            .put("icon", icon)
            .put("apk", apk)
            .put("size", size)
            .put("status", status)
            .put("fworld", fworld)
            .put("created", created)
            .put("updated", updated)
            .put("parts", parts)
        if (pending != null) o.put("pending", pending.toJson())
        return o
    }

    companion object {
        fun from(o: JSONObject) = AppItem(
            id = o.optString("id"),
            owner = o.optString("owner"),
            name = o.optString("name"),
            version = o.optString("version"),
            shortDesc = o.optString("shortDesc"),
            desc = o.optString("desc"),
            whatsNew = o.optString("whatsNew"),
            icon = o.optString("icon"),
            apk = o.optString("apk"),
            size = o.optLong("size"),
            status = o.optString("status", "pending"),
            fworld = o.optBoolean("fworld"),
            created = o.optLong("created"),
            updated = o.optLong("updated"),
            pending = o.optJSONObject("pending")?.let { PendingUpdate.from(it) },
            parts = o.optInt("parts", 1),
        )
    }
}

data class Review(
    val id: String,
    val appId: String,
    val user: String,
    val stars: Int,
    val text: String,
    val time: Long,
    val reply: String = "",
    val replyTime: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("appId", appId)
        .put("user", user)
        .put("stars", stars)
        .put("text", text)
        .put("time", time)
        .put("reply", reply)
        .put("replyTime", replyTime)

    companion object {
        fun from(o: JSONObject) = Review(
            o.optString("id"), o.optString("appId"), o.optString("user"), o.optInt("stars"),
            o.optString("text"), o.optLong("time"), o.optString("reply"), o.optLong("replyTime"),
        )
    }
}

data class BadgeDef(
    val id: String,
    val name: String,
    val desc: String,
    val kind: String, // emoji | color | image
    val value: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("desc", desc)
        .put("kind", kind)
        .put("value", value)

    companion object {
        fun from(o: JSONObject) = BadgeDef(
            o.optString("id"), o.optString("name"), o.optString("desc"),
            o.optString("kind", "emoji"), o.optString("value"),
        )
    }
}

val SYS_OWNER = BadgeDef("sys_owner", "#fworld", "Создатель fStore", "emoji", "👑")
val SYS_ADMIN = BadgeDef("sys_admin", "Админ", "Администратор fStore", "emoji", "🛡️")
val SYS_MODERATOR = BadgeDef("sys_moderator", "Модератор", "Проверяет приложения в fStore", "emoji", "🔍")
val SYS_FRIEND = BadgeDef("sys_friend", "Друг #fworld", "Верифицированный друг #fworld", "emoji", "💜")
val SYS_VIRUS = BadgeDef("sys_virus", "Вирусня...", "Этот пользователь замечен в распространении вредоносных приложений", "emoji", "🦠")
val SYS_FWORLD_APP = BadgeDef("sys_fworld_app", "#fworld", "Приложение с бейджем от #fworld", "emoji", "✨")

fun userBadges(u: User, all: List<BadgeDef>): List<BadgeDef> = buildList {
    if (u.isOwner) add(SYS_OWNER) else if (u.role == "admin") add(SYS_ADMIN) else if (u.role == "moderator") add(SYS_MODERATOR)
    if (u.friend) add(SYS_FRIEND)
    if (u.virus) add(SYS_VIRUS)
    u.badges.mapNotNull { id -> all.find { it.id == id } }.forEach { add(it) }
}
