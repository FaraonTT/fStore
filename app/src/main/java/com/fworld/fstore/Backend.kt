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
    private val token: String = BuildConfig.GH_TOKEN
    const val BRANCH = "main"
    val ready: Boolean get() = token.isNotBlank()

    private val JSON_TYPE = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private fun enc(path: String) = path.split("/").joinToString("/") { Uri.encode(it) }
    private fun api(path: String) = "https://api.github.com/repos/$owner/$repo/contents/${enc(path)}"

    fun rawUrl(path: String, v: Long = 0L): String =
        "https://raw.githubusercontent.com/$owner/$repo/$BRANCH/${enc(path)}" + (if (v > 0) "?v=$v" else "")

    private fun builder(url: String): Request.Builder {
        val b = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotBlank()) b.header("Authorization", "Bearer $token")
        return b
    }

    private fun rawText(path: String): String {
        val rq = builder(api(path) + "?ref=$BRANCH").header("Accept", "application/vnd.github.raw+json").build()
        client.newCall(rq).execute().use { r ->
            if (!r.isSuccessful) throw HttpEx(r.code, "")
            return r.body!!.string()
        }
    }

    /** Returns (text, sha) or null if the file does not exist. */
    suspend fun getText(path: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val url = api(path) + "?ref=$BRANCH"
        val (code, body) = client.newCall(builder(url).build()).execute().use { it.code to (it.body?.string() ?: "") }
        if (code == 404) return@withContext null
        if (code !in 200..299) throw HttpEx(code, body.take(200))
        val j = JSONObject(body)
        val c = j.optString("content", "")
        val text = if (c.isNotEmpty()) String(Base64.decode(c, Base64.DEFAULT), Charsets.UTF_8) else rawText(path)
        text to j.getString("sha")
    }

    suspend fun readArray(path: String): JSONArray {
        val t = getText(path)?.first ?: return JSONArray()
        return if (t.isBlank()) JSONArray() else JSONArray(t)
    }

    suspend fun sha(path: String): String? = withContext(Dispatchers.IO) {
        client.newCall(builder(api(path) + "?ref=$BRANCH").build()).execute().use { r ->
            if (r.code == 404) null
            else if (!r.isSuccessful) throw HttpEx(r.code, "")
            else JSONObject(r.body!!.string()).getString("sha")
        }
    }

    private fun put(path: String, body: RequestBody) {
        client.newCall(builder(api(path)).put(body).build()).execute().use { r ->
            if (!r.isSuccessful) throw HttpEx(r.code, r.body?.string()?.take(300) ?: "")
        }
    }

    suspend fun putBytes(path: String, bytes: ByteArray, msg: String, sha: String?) = withContext(Dispatchers.IO) {
        val j = JSONObject()
            .put("message", msg)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("branch", BRANCH)
        if (sha != null) j.put("sha", sha)
        put(path, j.toString().toRequestBody(JSON_TYPE))
    }

    suspend fun overwrite(path: String, bytes: ByteArray, msg: String) {
        putBytes(path, bytes, msg, sha(path))
    }

    /** Uploads [length] bytes of [file] starting at [offset] as one repository file. */
    suspend fun putFile(
        path: String, file: File, msg: String,
        offset: Long = 0L, length: Long = file.length(), onProgress: (Float) -> Unit,
    ) {
        val s = sha(path)
        withContext(Dispatchers.IO) { put(path, FileBody(file, offset, length, msg, s, onProgress)) }
    }

    /** Streams the given repository files (parts of one APK) into [out]. */
    suspend fun download(paths: List<String>, out: File, total: Long, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            var done = 0L
            var lastPct = -1
            out.outputStream().buffered(65536).use { o ->
                for (p in paths) {
                    val rq = Request.Builder().url(rawUrl(p)).build()
                    client.newCall(rq).execute().use { r ->
                        if (!r.isSuccessful) throw HttpEx(r.code, "файл не найден")
                        val ins = r.body!!.byteStream()
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
        }

    suspend fun delete(path: String, msg: String) = withContext(Dispatchers.IO) {
        val s = sha(path) ?: return@withContext
        val body = JSONObject().put("message", msg).put("sha", s).put("branch", BRANCH)
            .toString().toRequestBody(JSON_TYPE)
        client.newCall(builder(api(path)).delete(body).build()).execute().use { r ->
            if (!r.isSuccessful) throw HttpEx(r.code, "")
        }
    }

    /** Read-modify-write a JSON array with optimistic-concurrency retries. */
    suspend fun updateJson(path: String, msg: String, transform: (JSONArray) -> JSONArray) {
        repeat(6) { attempt ->
            val cur = getText(path)
            val arr = if (cur == null || cur.first.isBlank()) JSONArray() else JSONArray(cur.first)
            val out = transform(arr)
            try {
                putBytes(path, out.toString(1).toByteArray(Charsets.UTF_8), msg, cur?.second)
                return
            } catch (e: HttpEx) {
                if (e.code == 409 || e.code == 422) delay(400L * (attempt + 1)) else throw e
            }
        }
        throw IOException("Не удалось сохранить: слишком много конфликтов, попробуйте ещё раз")
    }
}

private class FileBody(
    private val file: File,
    private val offset: Long,
    private val length: Long,
    msg: String,
    sha: String?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    private val head: ByteArray
    private val tail = "\"}".toByteArray()

    init {
        val j = JSONObject().put("message", msg).put("branch", Gh.BRANCH)
        if (sha != null) j.put("sha", sha)
        head = (j.toString().dropLast(1) + ",\"content\":\"").toByteArray()
    }

    override fun contentType() = "application/json".toMediaType()

    override fun contentLength(): Long = head.size + 4L * ((length + 2) / 3) + tail.size

    override fun writeTo(sink: BufferedSink) {
        sink.write(head)
        val buf = ByteArray(3 * 16384)
        val total = length.coerceAtLeast(1L)
        var done = 0L
        file.inputStream().use { ins ->
            var skipped = 0L
            while (skipped < offset) {
                val k = ins.skip(offset - skipped)
                if (k <= 0) break
                skipped += k
            }
            while (done < length) {
                val want = minOf(buf.size.toLong(), length - done).toInt()
                val n = readFully(ins, buf, want)
                if (n <= 0) break
                sink.write(Base64.encode(buf, 0, n, Base64.NO_WRAP))
                done += n
                onProgress(done.toFloat() / total)
            }
        }
        sink.write(tail)
    }

    private fun readFully(i: InputStream, b: ByteArray, max: Int): Int {
        var o = 0
        while (o < max) {
            val r = i.read(b, o, max - o)
            if (r < 0) break
            o += r
        }
        return o
    }
}
