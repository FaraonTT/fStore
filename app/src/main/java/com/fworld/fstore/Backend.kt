package com.fworld.fstore

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class HttpEx(val code: Int, msg: String) : IOException("GitHub $code: $msg")

/** GitHub as a backend: JSON files + uploaded blobs in the project repository. */
object Gh {
    val owner: String = BuildConfig.GH_OWNER
    val repo: String = BuildConfig.GH_REPO
    private val token: String = BuildConfig.GH_TOKEN.trim()

    const val BRANCH = "main"

    /** True when a GitHub token was supplied at build time. */
    val ready: Boolean
        get() = token.isNotBlank()

    private val JSON_TYPE = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private fun enc(path: String) =
        path.split("/").joinToString("/") { Uri.encode(it) }

    private fun api(path: String) =
        "https://api.github.com/repos/$owner/$repo/contents/${enc(path)}"

    fun rawUrl(path: String, v: Long = 0L): String =
        "https://raw.githubusercontent.com/$owner/$repo/$BRANCH/${enc(path)}" +
            (if (v > 0) "?v=$v" else "")

    private fun builder(url: String): Request.Builder {
        val b = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")

        if (token.isNotBlank()) {
            b.header("Authorization", "Bearer $token")
        }
        return b
    }

    private fun rawText(path: String): String {
        val rq = builder(api(path) + "?ref=$BRANCH")
            .header("Accept", "application/vnd.github.raw+json")
            .build()

        client.newCall(rq).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpEx(response.code, response.body?.string()?.take(200).orEmpty())
            }
            return response.body?.string()
                ?: throw IOException("GitHub вернул пустой ответ для $path")
        }
    }

    /** Returns (text, sha) or null if the file does not exist. */
    suspend fun getText(path: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val url = api(path) + "?ref=$BRANCH"
            val (code, body) = client.newCall(builder(url).build()).execute().use {
                it.code to (it.body?.string() ?: "")
            }

            if (code == 404) return@withContext null
            if (code !in 200..299) throw HttpEx(code, body.take(200))

            val json = JSONObject(body)
            val content = json.optString("content", "")
            val text = if (content.isNotEmpty()) {
                String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
            } else {
                rawText(path)
            }
            text to json.getString("sha")
        }

    suspend fun readArray(path: String): JSONArray {
        val text = getText(path)?.first ?: return JSONArray()
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    suspend fun sha(path: String): String? =
        withContext(Dispatchers.IO) {
            client.newCall(builder(api(path) + "?ref=$BRANCH").build())
                .execute().use { response ->
                    when {
                        response.code == 404 -> null
                        !response.isSuccessful ->
                            throw HttpEx(response.code, response.body?.string()?.take(200).orEmpty())
                        else -> JSONObject(response.body?.string().orEmpty()).getString("sha")
                    }
                }
        }

    private fun put(path: String, body: RequestBody) {
        client.newCall(builder(api(path)).put(body).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpEx(
                    response.code,
                    response.body?.string()?.take(300).orEmpty()
                )
            }
        }
    }

    suspend fun putBytes(path: String, bytes: ByteArray, msg: String, sha: String?) =
        withContext(Dispatchers.IO) {
            val json = JSONObject()
                .put("message", msg)
                .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .put("branch", BRANCH)

            if (sha != null) json.put("sha", sha)
            put(path, json.toString().toRequestBody(JSON_TYPE))
        }

    suspend fun overwrite(path: String, bytes: ByteArray, msg: String) {
        putBytes(path, bytes, msg, sha(path))
    }

    suspend fun putFile(
        path: String,
        file: File,
        msg: String,
        onProgress: (Float) -> Unit
    ) {
        val currentSha = sha(path)
        withContext(Dispatchers.IO) {
            put(path, FileBody(file, msg, currentSha, onProgress))
        }
    }

    suspend fun delete(path: String, msg: String) =
        withContext(Dispatchers.IO) {
            val currentSha = sha(path) ?: return@withContext
            val body = JSONObject()
                .put("message", msg)
                .put("sha", currentSha)
                .put("branch", BRANCH)
                .toString()
                .toRequestBody(JSON_TYPE)

            client.newCall(builder(api(path)).delete(body).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpEx(response.code, response.body?.string()?.take(200).orEmpty())
                }
            }
        }

    /** Read-modify-write a JSON array with optimistic-concurrency retries. */
    suspend fun updateJson(
        path: String,
        msg: String,
        transform: (JSONArray) -> JSONArray
    ) {
        repeat(6) { attempt ->
            val current = getText(path)
            val array = if (current == null || current.first.isBlank()) {
                JSONArray()
            } else {
                JSONArray(current.first)
            }
            val output = transform(array)

            try {
                putBytes(
                    path,
                    output.toString(1).toByteArray(Charsets.UTF_8),
                    msg,
                    current?.second
                )
                return
            } catch (e: HttpEx) {
                if (e.code == 409 || e.code == 422) {
                    delay(400L * (attempt + 1))
                } else {
                    throw e
                }
            }
        }

        throw IOException("Не удалось сохранить: слишком много конфликтов, попробуйте ещё раз")
    }
}

private class FileBody(
    private val file: File,
    msg: String,
    sha: String?,
    private val onProgress: (Float) -> Unit
) : RequestBody() {
    private val head: ByteArray
    private val tail = "\"}".toByteArray()

    init {
        val json = JSONObject()
            .put("message", msg)
            .put("branch", Gh.BRANCH)

        if (sha != null) json.put("sha", sha)
        head = (json.toString().dropLast(1) + ",\"content\":\"").toByteArray()
    }

    override fun contentType() = "application/json".toMediaType()

    override fun contentLength(): Long =
        head.size + 4L * ((file.length() + 2) / 3) + tail.size

    override fun writeTo(sink: BufferedSink) {
        sink.write(head)

        val buffer = ByteArray(3 * 16384)
        val total = file.length().coerceAtLeast(1L)
        var done = 0L

        file.inputStream().use { input ->
            while (true) {
                val count = readFully(input, buffer)
                if (count <= 0) break

                sink.write(Base64.encode(buffer, 0, count, Base64.NO_WRAP))
                done += count
                onProgress(done.toFloat() / total)
            }
        }

        sink.write(tail)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) break
            offset += count
        }
        return offset
    }
}
