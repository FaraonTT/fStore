@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.fworld.fstore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream

const val MAX_APK = 100L * 1024 * 1024

fun fmtSize(b: Long): String =
    if (b >= 1048576) String.format("%.1f МБ", b / 1048576.0) else String.format("%d КБ", b / 1024)

fun fmtDate(t: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(t))

fun hexColor(s: String): Color? {
    if (s.isBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(if (s.startsWith("#")) s else "#$s"))
    } catch (e: Exception) {
        null
    }
}

/** Center-crops a picked image to a square PNG of [max] px. */
fun loadImage(ctx: Context, uri: Uri, max: Int = 256): ByteArray? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var s = 1
        while (opts.outWidth / s > max * 2 || opts.outHeight / s > max * 2) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            ?: return null
        val side = minOf(bmp.width, bmp.height)
        val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(sq, max, max, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun Avatar(u: User?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (u != null && u.avatar.isNotBlank()) {
            AsyncImage(
                model = Gh.rawUrl(u.avatar, u.updated),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                (u?.name?.firstOrNull() ?: '?').uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun AppIcon(a: AppItem, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (a.icon.isNotBlank()) {
            AsyncImage(
                model = Gh.rawUrl(a.icon, a.updated),
                contentDescription = a.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun NameLine(
    u: User?,
    fallback: String = "",
    style: TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier,
) {
    // Клик по имени открывает страницу пользователя (если экран это разрешает).
    val open = LocalOpenUser.current
    val un = u?.username ?: ""
    val link = if (open != null && un.isNotBlank()) Modifier.clickable { open(un) } else Modifier
    if (u == null) {
        Text(fallback, style = style, modifier = modifier)
        return
    }
    val base = hexColor(u.nameColor) ?: MaterialTheme.colorScheme.onSurface
    val glow = hexColor(u.glowColor) ?: base
    Row(modifier.then(link), verticalAlignment = Alignment.CenterVertically) {
        Text(
            u.name,
            color = base,
            style = if (u.glow) style.copy(shadow = Shadow(glow, Offset.Zero, 24f)) else style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (u.verified) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Verified, "Верифицирован", tint = Color(0xFF1D9BF0), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun BadgeGlyph(b: BadgeDef, size: Dp) {
    when (b.kind) {
        "color" -> Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(hexColor(b.value) ?: MaterialTheme.colorScheme.primary),
        )
        "image" -> AsyncImage(
            model = Gh.rawUrl(b.value),
            contentDescription = b.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
        else -> Text(b.value, fontSize = (size.value * 0.8f).sp)
    }
}

@Composable
fun BadgeChip(b: BadgeDef, onClick: () -> Unit) {
    val tint = if (b.kind == "color") hexColor(b.value) else null
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tint?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BadgeGlyph(b, 16.dp)
            Text(b.name, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun BadgeRow(list: List<BadgeDef>) {
    if (list.isEmpty()) return
    var open by remember { mutableStateOf<BadgeDef?>(null) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        list.forEach { b -> BadgeChip(b) { open = b } }
    }
    open?.let { b ->
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Ок") } },
            icon = { BadgeGlyph(b, 28.dp) },
            title = { Text(b.name) },
            text = { Text(b.desc.ifBlank { "Без описания" }) },
        )
    }
}

@Composable
fun Tag(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Плашка «#fworld» с переливающимся чёрно-фиолетовым градиентом. */
@Composable
fun FworldTag() {
    Box(
        Modifier
            .clip(CircleShape)
            .background(fworldBrush(260f, 80f)),
    ) {
        Text(
            "✨ #fworld",
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

@Composable
fun StatusTag(status: String) {
    val cs = MaterialTheme.colorScheme
    when (status) {
        "approved" -> Tag("Проверено", cs.primaryContainer, cs.onPrimaryContainer)
        "rejected" -> Tag("Отклонено", cs.errorContainer, cs.onErrorContainer)
        else -> Tag("Не проверено", cs.tertiaryContainer, cs.onTertiaryContainer)
    }
}

@Composable
fun AppCard(a: AppItem, onClick: () -> Unit) {
    val compact = UiPrefs.compactCards
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && LocalAnim.current) 0.97f else 1f, label = "press")
    val openUser = LocalOpenUser.current
    val cardShape = RoundedCornerShape(if (compact) 20.dp else 28.dp)
    Card(
        onClick = onClick,
        interactionSource = src,
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (a.fworld) Modifier.fworldGlow(cardShape) else Modifier),
    ) {
        Row(Modifier.padding(if (compact) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a, if (compact) 48.dp else 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    Text(
                        "v${a.version}" + (if (UiPrefs.showSize) " · ${fmtSize(a.size)}" else "") + " · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Клик по @нику открывает профиль разработчика.
                    Text(
                        "@${a.owner}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (openUser != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (openUser != null) Modifier.clickable { openUser(a.owner) } else Modifier,
                    )
                }
                if (!compact && UiPrefs.showDesc && a.shortDesc.isNotBlank()) {
                    Text(
                        a.shortDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (a.fworld) FworldTag()
                    StatusTag(a.status)
                }
            }
        }
    }
}

@Composable
fun StarsRow(value: Int, size: Dp = 20.dp, onSet: ((Int) -> Unit)? = null) {
    Row {
        for (i in 1..5) {
            val ic = if (i <= value) Icons.Default.Star else Icons.Default.StarBorder
            val m = if (onSet != null) Modifier.size(size).clickable { onSet(i) } else Modifier.size(size)
            Icon(ic, null, m, tint = Color(0xFFFFB300))
        }
    }
}

@Composable
fun ImagePicker(
    bytes: ByteArray?,
    onPick: (ByteArray) -> Unit,
    size: Dp = 96.dp,
    round: Boolean = true,
    label: String = "Фото",
    placeholder: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(ctx, uri)?.let(onPick)
    }
    val bmp = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(
        Modifier
            .size(size)
            .clip(if (round) CircleShape else RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { launcher.launch("image/*") },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (placeholder != null) {
            placeholder()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddAPhoto, null)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val PALETTE = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#3F51B5", "#2196F3",
    "#00BCD4", "#4CAF50", "#FFEB3B", "#FF9800", "#FFFFFF",
)

@Composable
fun ColorPicker(label: String, value: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, cs.outline, CircleShape)
                    .clickable { onChange("") },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, "Нет", Modifier.size(18.dp)) }
            PALETTE.forEach { c ->
                val sel = value.equals(c, ignoreCase = true)
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(hexColor(c) ?: Color.Gray)
                        .border(if (sel) 3.dp else 1.dp, if (sel) cs.primary else cs.outlineVariant, CircleShape)
                        .clickable { onChange(c) },
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text("HEX, например #FF00AA") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
