package com.fworld.fstore

/**
 * GitHub отклоняет файлы больше 100 МБ, поэтому большой APK хранится частями по 25 МБ.
 * Одна часть (APK до 25 МБ) лежит по исходному пути, как и раньше — старые приложения совместимы.
 */
const val APK_PART = 25L * 1024 * 1024

/** Максимальный размер загружаемого APK. */
const val MAX_UPLOAD = 300L * 1024 * 1024

fun apkPartCount(total: Long): Int = ((total + APK_PART - 1) / APK_PART).toInt().coerceAtLeast(1)

fun apkParts(path: String, parts: Int): List<String> =
    if (parts <= 1) listOf(path) else (1..parts).map { "$path.p$it" }
