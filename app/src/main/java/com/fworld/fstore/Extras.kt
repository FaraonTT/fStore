@file:OptIn(ExperimentalMaterial3Api::class)

package com.fworld.fstore

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/** Ссылки fstore://app/<id>, fstore://music, fstore://downloads — из уведомлений и из «Поделиться». */
object DeepLink {
    var target by mutableStateOf<String?>(null)

    fun from(intent: Intent?) {
        val d = intent?.data ?: return
        if (d.scheme != "fstore") return
        val id = d.pathSegments.firstOrNull().orEmpty()
        target = when (d.host) {
            "app" -> if (id.isNotBlank()) "app:$id" else null
            "music" -> "music"
            "downloads" -> "downloads"
            else -> null
        }
    }

    fun appLink(id: String) = "fstore://app/$id"

    /** Веб-ссылка (страница-переадресация из папки docs): её кликабельно в любом мессенджере. */
    fun webLink(id: String) = "https://${Gh.owner.lowercase()}.github.io/${Gh.repo}/?app=$id"
}

/** «Поделиться» страницей приложения: ссылка, которая по клику открывает его прямо в fStore. */
fun shareApp(ctx: Context, a: AppItem) {
    val text = "«${a.name}» в fStore\n${DeepLink.webLink(a.id)}\n\nОткрыть в приложении: ${DeepLink.appLink(a.id)}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** «Поделиться» самим скачанным APK-файлом. */
fun shareApkFile(ctx: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Поделиться APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.widget.Toast.makeText(ctx, "Не удалось поделиться: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ShareIcon(desc: String = "Поделиться") {
    Icon(Icons.Default.Share, desc, Modifier.size(22.dp))
}

/** Сообщения от модератора/админа владельцу приложения (data/notices.json). */
object Notices {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun send(me: User, a: AppItem, text: String, onMsg: (String) -> Unit) {
        if (!Gh.ready) { onMsg("Сервер не настроен: в сборке нет токена FSTORE_TOKEN"); return }
        scope.launch {
            try {
                require(me.isModerator) { "Нужны права модератора" }
                val ts = System.currentTimeMillis()
                Gh.updateJson("data/notices.json", "notice ${a.id}") { arr ->
                    val l = arr.objs().toMutableList()
                    l += JSONObject()
                        .put("id", "n$ts")
                        .put("to", a.owner)
                        .put("appId", a.id)
                        .put("app", a.name)
                        .put("from", me.username)
                        .put("text", text)
                        .put("time", ts)
                    l.takeLast(200).toJsonArray()
                }
                onMsg("Владелец @${a.owner} уведомлён")
            } catch (e: Exception) {
                onMsg(e.message ?: "Ошибка")
            }
        }
    }
}

/** Показывает мне сообщения, адресованные мне (на новом устройстве первый раз — только за 3 дня). */
object NoticeWatch {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("fstore_notices", 0)

    suspend fun check(ctx: Context) {
        val user = ctx.applicationContext.getSharedPreferences("fstore", 0).getString("user", null) ?: return
        val list = Gh.readArray("data/notices.json").objs()
        val p = sp(ctx)
        val first = !p.contains("seen")
        val seen = (p.getStringSet("seen", emptySet()) ?: emptySet()).toMutableSet()
        val limit = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
        for (o in list) {
            val id = o.optString("id")
            if (id.isBlank() || id in seen) continue
            seen += id
            if (o.optString("to") == user && (!first || o.optLong("time") > limit)) {
                Notifier.notice(
                    ctx, id, o.optString("appId"),
                    "Сообщение по «${o.optString("app")}»",
                    "@${o.optString("from")}: ${o.optString("text")}",
                )
            }
        }
        p.edit().putStringSet("seen", seen).apply()
    }
}

/** Окно «Уведомить владельца»: короткое сообщение, которое придёт ему уведомлением. */
@Composable
fun NoticeDialog(app: AppItem, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Уведомить @${app.owner}") },
        text = {
            Column {
                Text("«${app.name}»: владелец получит уведомление и по нажатию откроет страницу приложения.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(300) },
                    label = { Text("Сообщение") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSend(text.trim()) }) { Text("Отправить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
