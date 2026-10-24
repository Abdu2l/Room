package com.abdu2l.room

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RoomShareCardDialog(
    state: RoomUiState,
    colors: RoomPalette,
    onDismiss: () -> Unit,
    onShare: (Bitmap, String) -> Unit
) {
    var format by remember { mutableStateOf("story") }
    val bitmap by produceState<Bitmap?>(null, state.username, state.range, state.activeTracks.size, state.activeArtists.size, format) {
        value = withContext(Dispatchers.Default) { drawRoomCard(state, format) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.92f), shape = RoundedCornerShape(26.dp), color = colors.card.copy(alpha = 1f)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PASS IT AROUND", color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Share your sound", color = colors.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDismiss) { Text("Close", color = colors.muted) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("story" to "Story · 9:16", "square" to "Square · 1:1", "track" to "Track").forEach { (id, label) ->
                        val active = format == id
                        Surface(color = if (active) colors.deep else Color.White.copy(alpha = .45f), shape = RoundedCornerShape(50), modifier = Modifier.weight(1f), onClick = { format = id }) {
                            Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp), color = if (active) Color.White else colors.ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Room share card preview", modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp), contentScale = ContentScale.Fit) }
                Button(onClick = { bitmap?.let { onShare(it, format) } }, enabled = bitmap != null, colors = ButtonDefaults.buttonColors(containerColor = colors.deep, contentColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Text("Share / save PNG")
                }
            }
        }
    }
}

private fun drawRoomCard(state: RoomUiState, format: String): Bitmap {
    val isTrack = format == "track"
    val width = 1080
    val height = if (format == "square") 1080 else if (isTrack) 1350 else 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val ink = android.graphics.Color.rgb(25, 25, 25)
    val muted = android.graphics.Color.rgb(100, 100, 100)
    val accent = android.graphics.Color.rgb(145, 145, 145)
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), android.graphics.Color.rgb(220, 220, 220), android.graphics.Color.rgb(174, 174, 174), android.graphics.Shader.TileMode.CLAMP) }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val pad = 84f
    paint.color = ink
    canvas.drawCircle(pad + 22, pad + 2, 22f, paint)
    paint.color = accent
    canvas.drawCircle(pad + 22, pad + 2, 8f, paint)
    paint.color = ink
    paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    paint.textSize = 48f
    canvas.drawText("ROOM", pad + 57, pad + 17, paint)
    paint.color = muted
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 24f
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText(if (state.activeHistory) "YOUR ARCHIVE" else "YOUR LISTENING", width - pad, pad + 14, paint)
    paint.textAlign = Paint.Align.LEFT

    val tracks = state.activeTracks.take(if (isTrack) 1 else 5)
    val artists = state.activeArtists.take(5)
    var y = 230f
    paint.color = ink
    paint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    paint.textSize = if (isTrack) 82f else 76f
    val title = if (isTrack) tracks.firstOrNull()?.name ?: "A song that stays" else "${state.username.ifBlank { "My" }}'s sound"
    canvas.drawText(clipText(title, paint, width - pad * 2), pad, y, paint)
    y += 74f
    paint.color = muted
    paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    paint.textSize = 27f
    canvas.drawText(if (isTrack) tracks.firstOrNull()?.artist ?: "Your track diary" else state.range.label.uppercase(), pad, y, paint)
    y += 70f

    if (!isTrack && artists.isNotEmpty()) {
        paint.color = muted
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = 27f
        canvas.drawText("ARTISTS IN YOUR ORBIT", pad, y, paint)
        y += 30f
        val diameter = 142f
        artists.forEachIndexed { index, artist ->
            val left = pad + index * 184f
            val rect = RectF(left, y, left + diameter, y + diameter)
            drawArtworkCircle(canvas, paint, artist.image, rect, artist.name)
            paint.color = ink
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textSize = 19f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(clipText(artist.name, paint, diameter + 20), left + diameter / 2, y + diameter + 28, paint)
            paint.textAlign = Paint.Align.LEFT
        }
        y += diameter + 102f
    }
    if (!isTrack && tracks.isNotEmpty()) {
        paint.color = muted
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = 27f
        canvas.drawText("ON REPEAT", pad, y, paint)
        y += 34f
        tracks.forEachIndexed { index, track ->
            if (y + 110f > height - 100f) return@forEachIndexed
            paint.color = ink
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textSize = 25f
            canvas.drawText("${index + 1}".padStart(2, '0'), pad, y + 54f, paint)
            val cover = RectF(pad + 60, y, pad + 140, y + 80)
            drawArtworkSquare(canvas, paint, track.image, cover, track.name)
            paint.color = ink
            paint.textSize = 25f
            canvas.drawText(clipText(track.name, paint, width - pad - cover.right - 20), cover.right + 20, y + 34f, paint)
            paint.color = muted
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = 19f
            canvas.drawText(clipText(track.artist, paint, width - pad - cover.right - 20), cover.right + 20, y + 63f, paint)
            y += 106f
        }
    } else if (isTrack) {
        val track = tracks.firstOrNull()
        if (track != null) {
            val image = ArtworkLoader.cached(track.image)
            val rect = RectF(190f, y, 890f, y + 700f)
            if (image != null) canvas.drawBitmap(image, null, rect, paint) else {
                paint.color = android.graphics.Color.rgb(45, 45, 45)
                canvas.drawRoundRect(rect, 46f, 46f, paint)
                paint.color = android.graphics.Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 190f
                canvas.drawText(track.name.take(1).uppercase(), width / 2f, y + 390f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
        }
    }
    paint.color = muted
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = 25f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText("ROOM  ·  abdu2l.github.io/Room", width / 2f, height - 58f, paint)
    return bitmap
}

private fun drawArtworkCircle(canvas: Canvas, paint: Paint, url: String, rect: RectF, name: String) {
    val image = ArtworkLoader.cached(url)
    canvas.save()
    val path = android.graphics.Path().apply { addCircle(rect.centerX(), rect.centerY(), rect.width() / 2, android.graphics.Path.Direction.CW) }
    canvas.clipPath(path)
    if (image != null) canvas.drawBitmap(image, null, rect, paint) else {
        paint.color = android.graphics.Color.rgb(52, 52, 52)
        canvas.drawRect(rect, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 58f
        canvas.drawText(name.take(1).uppercase(), rect.centerX(), rect.centerY() + 20f, paint)
        paint.textAlign = Paint.Align.LEFT
    }
    canvas.restore()
}

private fun drawArtworkSquare(canvas: Canvas, paint: Paint, url: String, rect: RectF, name: String) {
    val image = ArtworkLoader.cached(url)
    if (image != null) canvas.drawBitmap(image, null, rect, paint) else {
        paint.color = android.graphics.Color.rgb(55, 55, 55)
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 34f
        canvas.drawText(name.take(1).uppercase(), rect.centerX(), rect.centerY() + 12f, paint)
        paint.textAlign = Paint.Align.LEFT
    }
}

private fun clipText(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var clipped = text
    while (clipped.length > 1 && paint.measureText("$clipped…") > maxWidth) clipped = clipped.dropLast(1)
    return "$clipped…"
}
