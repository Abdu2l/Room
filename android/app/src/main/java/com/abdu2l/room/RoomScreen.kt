package com.abdu2l.room

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle

import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite

import androidx.compose.material.icons.rounded.Home

import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

internal data class RoomPalette(
    val background: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val line: Color,
    val deep: Color
)

private fun roomGrain(): androidx.compose.ui.graphics.ImageBitmap {
    val size = 128
    val random = java.util.Random(17)
    val pixels = IntArray(size * size) {
        val value = if (random.nextBoolean()) 255 else 0
        android.graphics.Color.argb(30, value, value, value)
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, size, 0, 0, size, size) }.asImageBitmap()
}

private fun palette(theme: String) = when (theme) {
    "pink" -> RoomPalette(Color(0xFFC6B9C1), Color(0x70FFFFFF), Color(0xFF241920), Color(0xFF796773), Color(0xFFE778B3), Color(0x80FFFFFF), Color(0xFF382230))
    "green" -> RoomPalette(Color(0xFFBAC8BC), Color(0x70FFFFFF), Color(0xFF17231A), Color(0xFF65756A), Color(0xFF6ACB84), Color(0x80FFFFFF), Color(0xFF1F3928))
    "red" -> RoomPalette(Color(0xFFCDBBBB), Color(0x70FFFFFF), Color(0xFF261A1A), Color(0xFF796565), Color(0xFFE67679), Color(0x80FFFFFF), Color(0xFF3B2223))
    else -> RoomPalette(Color(0xFFC8C8C8), Color(0x70FFFFFF), Color(0xFF191919), Color(0xFF747474), Color(0xFF939393), Color(0x99FFFFFF), Color(0xFF252525))
}

@Composable
internal fun RoomApp(viewModel: RoomViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val colors = palette(state.theme)
    val activity = LocalContext.current as? Activity
    SideEffect {
        activity?.window?.statusBarColor = colors.background.toArgbInt()
        activity?.window?.navigationBarColor = colors.background.toArgbInt()
        @Suppress("DEPRECATION")
        run { activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR }
    }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.ink,
        background = colors.background,
        onBackground = colors.ink,
        surface = colors.card,
        onSurface = colors.ink,
        outline = colors.line
    )) {
        val context = LocalContext.current
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> viewModel.importHistory(uris) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showShareCard by rememberSaveable { mutableStateOf(false) }
        var editingTrack by remember { mutableStateOf<ListeningTrack?>(null) }

        val grain = remember { roomGrain() }
        val ambientMotion = rememberInfiniteTransition(label = "ambient-canvas")
        val ambientShift by ambientMotion.animateFloat(-.08f, .08f, infiniteRepeatable(tween(28_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ambient-shift")
        Scaffold(
            modifier = Modifier.fillMaxSize().drawBehind {
                drawRect(Brush.linearGradient(listOf(colors.background, colors.background.copy(alpha = .92f), colors.background)))
                drawCircle(colors.accent.copy(alpha = .14f), size.minDimension * .72f, androidx.compose.ui.geometry.Offset(size.width * (.12f + ambientShift), size.height * .08f))
                drawCircle(Color.White.copy(alpha = .12f), size.minDimension * .58f, androidx.compose.ui.geometry.Offset(size.width * (.92f - ambientShift), size.height * .68f))
                val tile = 192.dp.toPx().toInt().coerceAtLeast(1)
                for (y in 0 until size.height.toInt() step tile) for (x in 0 until size.width.toInt() step tile) {
                    drawImage(grain, dstOffset = IntOffset(x, y), dstSize = IntSize(tile, tile), alpha = .10f)
                }
            },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column {
                    RoomTopBar(
                        state = state,
                        colors = colors,
                        onSettings = { showSettings = true },
                        onRefresh = viewModel::refresh,
                        onShare = { showShareCard = true }
                    )
                    RoomNavigationPill(state.tab, colors, viewModel::setTab)
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.error != null) NoticeCard(state.error!!, true, colors, viewModel::clearNotice)
                else if (state.message != null) NoticeCard(state.message!!, false, colors, viewModel::clearNotice)
                if (state.loading) LinearLoading(colors)
                AnimatedContent(
                    targetState = state.tab,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 18 }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 24 })
                    },
                    label = "room-screen-transition"
                ) { tab ->
                    when (tab) {
                        RoomTab.OVERVIEW -> OverviewScreen(state, colors, viewModel, onConnect = { showSettings = true }, onImport = { importPicker.launch(arrayOf("application/json", "text/json")) }, onTrack = { editingTrack = it })
                        RoomTab.TRACKS -> TracksScreen(state, colors, viewModel::setRange, onTrack = { editingTrack = it })
                        RoomTab.ARTISTS -> ArtistsScreen(state, colors, viewModel::setRange)
                        RoomTab.GALAXY -> GalaxyScreen(state, colors, viewModel, onConnect = { showSettings = true })
                        RoomTab.ACTIVITY -> ActivityScreen(state, colors, viewModel::setRange)
                    }
                }
            }
        }
        if (showSettings) SettingsDialog(state, colors, viewModel, onDismiss = { showSettings = false })
        if (showShareCard) RoomShareCardDialog(state, colors, onDismiss = { showShareCard = false }) { bitmap, format ->
            coroutineScope.launch {
                val uri = withContext(Dispatchers.IO) {
                    runCatching {
                        val name = "room-$format-${System.currentTimeMillis()}.png"
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, name)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Room")
                        }
                        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also { saved ->
                            context.contentResolver.openOutputStream(saved)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                    }.getOrNull()
                }
                if (uri != null) {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TITLE, "My Room listening card")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share your Room card"))
                    showShareCard = false
                } else {
                    viewModel.clearNotice()
                }
            }
        }
        editingTrack?.let { track -> TrackNoteDialog(track, state.notes[noteKey(track)] ?: TrackNote(), colors,
            onDismiss = { editingTrack = null },
            onSave = { note -> viewModel.saveNote(track, note); editingTrack = null },
            onShare = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${track.name} — ${track.artist}\n${"★".repeat(it.rating)}${if (it.text.isNotBlank()) "\n“${it.text}”" else ""}\nShared from Room")
                }, "Share track note"))
                editingTrack = null
            })
        }
    }
}

@Composable
private fun RoomTopBar(state: RoomUiState, colors: RoomPalette, onSettings: () -> Unit, onRefresh: () -> Unit, onShare: () -> Unit) {
    Surface(color = Color.Transparent, shadowElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher), contentDescription = "Room", modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("room", color = colors.ink, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                Text(if (state.username.isBlank()) "YOUR LISTENING, IN FULL COLOR" else "ROOM FOR ${state.username.uppercase(Locale.getDefault())}", color = colors.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onShare, modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .42f)).border(1.dp, Color.White.copy(alpha = .58f), CircleShape)) { Icon(Icons.Rounded.Share, "Share", tint = colors.ink, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onRefresh, modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .42f)).border(1.dp, Color.White.copy(alpha = .58f), CircleShape)) { Icon(Icons.Rounded.Refresh, "Refresh", tint = colors.ink, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onSettings, modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .42f)).border(1.dp, Color.White.copy(alpha = .58f), CircleShape)) { Icon(Icons.Rounded.Settings, "Settings", tint = colors.ink, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun RoomNavigationPill(selected: RoomTab, colors: RoomPalette, onSelect: (RoomTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .58f), Color.White.copy(alpha = .34f))))
            .border(1.dp, Color.White.copy(alpha = .72f), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoomTab.entries.forEach { tab ->
            val active = selected == tab
            val pillColor by animateColorAsState(if (active) Color.White.copy(alpha = .94f) else Color.Transparent, tween(300), label = "nav-pill-color")
            val scale by animateFloatAsState(if (active) 1f else .97f, tween(430, easing = FastOutSlowInEasing), label = "nav-pill")
            Surface(
                color = pillColor,
                shape = CircleShape,
                modifier = Modifier.weight(1f).graphicsLayer(scaleX = scale, scaleY = scale)
                    .clickable { onSelect(tab) }
            ) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(tabIcon(tab), contentDescription = null, tint = if (active) colors.ink else colors.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(when (tab) { RoomTab.OVERVIEW -> "Overview"; else -> tab.title }, color = if (active) colors.ink else colors.muted, fontSize = 9.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

@Composable
private fun OverviewScreen(state: RoomUiState, colors: RoomPalette, viewModel: RoomViewModel, onConnect: () -> Unit, onImport: () -> Unit, onTrack: (ListeningTrack) -> Unit) {
    val rows = rangeEntries(state)
    val tracks = if (state.activeHistory) rankTracks(rows) else state.stats?.tracks.orEmpty()
    val artists = if (state.activeHistory) rankArtists(rows) else state.stats?.artists.orEmpty()
    val totalPlays = if (state.activeHistory) rows.size else state.stats?.totalScrobbles ?: 0
    val totalTime = if (state.activeHistory) formatDuration(rows.sumOf { it.durationMs }) else "≈ ${formatDuration((totalPlays * 3.5 * 60_000).toLong())}"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(6.dp))
        HeroCard(state, colors, onConnect, onImport) { viewModel.clearHistory() }
        Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("THE LAST LITTLE WHILE", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
            Text("A PORTRAIT IN PLAYS", color = colors.ink, fontSize = 31.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal, letterSpacing = (-.8).sp, modifier = Modifier.graphicsLayer(scaleX = .88f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)))
        }
        RangeSelector(state.range, colors, viewModel::setRange)
        if (state.stats != null || state.activeHistory) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("SCROBBLES", NumberFormat.getIntegerInstance().format(totalPlays), state.range.label, "♫", colors, Modifier.weight(1f))
                MetricCard("TOP TRACKS", NumberFormat.getIntegerInstance().format(tracks.size), "in this view", "♪", colors, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("TOP ARTISTS", NumberFormat.getIntegerInstance().format(artists.size), "in this view", "✳", colors, Modifier.weight(1f))
                MetricCard(if (state.activeHistory) "LISTENING TIME" else "TIME ESTIMATE", totalTime, if (state.activeHistory) "from Spotify export" else "estimated from scrobbles", "◷", colors, Modifier.weight(1f))
            }
            Panel(colors) {
                PanelHeading("Seven little days", "A week of listening, one day at a time", colors)
                WeekChart(rows, state.activeHistory, colors)
            }
            Panel(colors) {
                PanelHeading("On repeat", "Your most-played tracks", colors, trailing = "${minOf(tracks.size, 5)} tracks")
                if (tracks.isEmpty()) EmptyMessage("No tracks in this date range yet.", colors)
                tracks.take(5).forEach { TrackRow(it, state.notes[noteKey(it)] ?: TrackNote(), colors, onTrack) }
            }
            Panel(colors) {
                PanelHeading("Familiar faces", "Artists showing up in your listening", colors, trailing = "${minOf(artists.size, 5)} artists")
                if (artists.isEmpty()) EmptyMessage("No artists in this date range yet.", colors)
                artists.take(5).forEach { ArtistRow(it, colors) }
            }
        } else {
            GettingStarted(colors, onConnect, onImport)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HeroCard(state: RoomUiState, colors: RoomPalette, onConnect: () -> Unit, onImport: () -> Unit, onClearImport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF898989), Color(0xFF939393), Color(0xFF9B9B9B))), RoundedCornerShape(26.dp))
            .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(26.dp))
    ) {
        Box(Modifier.fillMaxWidth().padding(22.dp)) {
            Column(Modifier.fillMaxWidth(0.70f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (state.activeHistory) "YOUR ARCHIVE, IN FULL COLOR" else if (state.username.isBlank()) "YOUR LISTENING, IN FULL COLOR" else "YOUR LISTENING, IN FULL COLOR", color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(if (state.activeHistory) "A LITTLE\nHISTORY." else "YOUR MUSIC\nHAS A STORY.", color = Color.White, fontSize = 41.sp, lineHeight = 37.sp, fontWeight = FontWeight.Normal, letterSpacing = (-1.2).sp, modifier = Modifier.graphicsLayer(scaleX = .78f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)) )
                Text(if (state.activeHistory) "${state.history?.size ?: 0} plays imported from your Spotify archive." else if (state.stats != null) "Your Last.fm listening, gathered in one room." else "A little room for the songs you keep coming back to.", color = Color.White.copy(alpha = .72f), fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.stats == null && !state.activeHistory) SmallAction("Connect Last.fm", colors.accent, colors.ink, onConnect)
                    SmallAction(if (state.activeHistory) "Import more" else "Import history", Color.White.copy(alpha = .12f), Color.White, onImport)
                    if (state.activeHistory) SmallAction("Clear import", Color.White.copy(alpha = .12f), Color.White, onClearImport)
                }
                state.stats?.recent?.firstOrNull()?.let { latest ->
                    val latestArtwork = state.stats.tracks.firstOrNull { it.name.equals(latest.track, true) && it.artist.equals(latest.artist, true) }?.image.orEmpty()
                    NowPlayingBadge(latest, latestArtwork)
                }
            }
            VinylArtwork(colors, Modifier.align(Alignment.CenterEnd).size(132.dp))
            Text(if (state.activeHistory) "HISTORY · LOCAL" else if (state.stats == null) "ROOM NO. 01" else "LAST.FM · LIVE", color = Color.White.copy(alpha = .5f), fontSize = 8.sp, letterSpacing = 1.1.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun NowPlayingBadge(entry: PlayEntry, artwork: String) {
    Row(
        Modifier.fillMaxWidth(.98f).clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .19f), Color.White.copy(alpha = .09f))))
            .border(1.dp, Color.White.copy(alpha = .25f), RoundedCornerShape(13.dp))
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoomArtwork(artwork, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), ContentScale.Crop, artist = entry.artist, track = entry.track) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Text("♫", color = Color.White, fontSize = 15.sp) }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("LAST.FM IS TRACKING", color = Color.White.copy(alpha = .64f), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, maxLines = 1)
            Text(entry.track, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, color = Color.White.copy(alpha = .68f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("♫", color = Color.White.copy(alpha = .82f), fontSize = 16.sp, modifier = Modifier.padding(horizontal = 5.dp))
    }
}

@Composable
private fun VinylArtwork(colors: RoomPalette, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "record-motion")
    val rotation by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "record-spin")
    val pulse by motion.animateFloat(.94f, 1.06f, infiniteRepeatable(tween(4_000), RepeatMode.Reverse), label = "record-pulse")
    Canvas(modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width * .52f, size.height * .49f)
        val radius = size.minDimension * .37f
        drawCircle(Color.White.copy(alpha = .13f), radius = radius * 1.22f * pulse, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(Color.White.copy(alpha = .045f), radius = radius * 1.42f, center = center, style = Stroke(width = 8.dp.toPx()))
        val orbit = Path().apply {
            moveTo(size.width * .05f, size.height * .71f)
            cubicTo(size.width * .14f, size.height * .09f, size.width * .83f, size.height * .13f, size.width * .95f, size.height * .48f)
        }
        rotate(rotation * 1.25f, center) { drawPath(orbit, Color.White.copy(alpha = .42f), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)) }
        rotate(-rotation * .88f, center) {
            drawOval(Color.White.copy(alpha = .13f), topLeft = androidx.compose.ui.geometry.Offset(size.width * .09f, size.height * .23f), size = androidx.compose.ui.geometry.Size(size.width * .82f, size.height * .45f), style = Stroke(width = .8.dp.toPx()))
        }
        rotate(rotation, center) {
            drawCircle(Color(0xFF292929), radius, center)
            for (ring in 1..6) drawCircle(Color.White.copy(alpha = .075f), radius * (ring / 7f), center, style = Stroke(width = 1.dp.toPx()))
            drawArc(Color.White.copy(alpha = .22f), startAngle = 35f, sweepAngle = 38f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(center.x - radius * .84f, center.y - radius * .84f), size = androidx.compose.ui.geometry.Size(radius * 1.68f, radius * 1.68f), style = Stroke(width = 2.dp.toPx()))
            drawLine(Color.White.copy(alpha = .14f), androidx.compose.ui.geometry.Offset(center.x - radius * .72f, center.y - radius * .18f), androidx.compose.ui.geometry.Offset(center.x - radius * .48f, center.y - radius * .52f), strokeWidth = 1.dp.toPx())
            drawCircle(colors.accent, radius * .31f, center)
            drawCircle(Color(0xFFD8D8D8), radius * .055f, center)
        }
        val bars = listOf(.19f, .32f, .48f, .26f, .68f, .43f, .23f, .58f, .34f)
        val x0 = size.width * .78f
        bars.forEachIndexed { index, height ->
            val wobble = .75f + .25f * kotlin.math.sin((rotation * .035f) + index).toFloat().let { kotlin.math.abs(it) }
            val barHeight = size.height * height * wobble
            drawRoundRect(Color.White.copy(alpha = .78f), androidx.compose.ui.geometry.Offset(x0 + index * size.width * .022f, center.y + radius * .55f - barHeight / 2), androidx.compose.ui.geometry.Size(size.width * .012f, barHeight), androidx.compose.ui.geometry.CornerRadius(size.width * .01f))
        }
    }
}

@Composable
private fun GettingStarted(colors: RoomPalette, onConnect: () -> Unit, onImport: () -> Unit) {
    Panel(colors) {
        Text("ONE QUICK SETUP", color = colors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text("Bring your listening in.", color = colors.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Room reads your public Last.fm scrobbles or imports Spotify's JSON listening-history export. Spotify linking happens in Last.fm; Room never asks for your Spotify password.", color = colors.muted, fontSize = 14.sp, lineHeight = 20.sp)
        Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = colors.deep, contentColor = Color.White), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text("♫", fontSize = 17.sp); Spacer(Modifier.width(8.dp)); Text("Connect with Last.fm")
        }
        OutlinedButton(onClick = onImport, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text("↑", color = colors.ink, fontSize = 17.sp); Spacer(Modifier.width(8.dp)); Text("Import Spotify history", color = colors.ink)
        }
        Text("Your imported history is processed on this device and isn't uploaded to Room.", color = colors.muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun TracksScreen(state: RoomUiState, colors: RoomPalette, onRange: (TimeRange) -> Unit, onTrack: (ListeningTrack) -> Unit) {
    val rows = rangeEntries(state)
    val tracks = if (state.activeHistory) rankTracks(rows) else state.stats?.tracks.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeading("The tracks you return to", "Ranked by plays · ${state.range.label}", colors)
        RangeSelector(state.range, colors, onRange)
        Panel(colors) {
            PanelHeading("Your tracks, in order", if (state.activeHistory) "From your imported Spotify archive" else "From your Last.fm scrobbles", colors, trailing = "TOP ${tracks.size}")
            if (tracks.isEmpty()) EmptyMessage("Connect Last.fm or import your Spotify history to see your tracks.", colors)
            tracks.forEachIndexed { index, track ->
                TrackRow(track, state.notes[noteKey(track)] ?: TrackNote(), colors, onTrack, rank = index + 1)
            }
            if (state.stats?.truncated == true && !state.activeHistory) Text("Activity rows are limited to the 200 most recent scrobbles returned by Last.fm. Period rankings come from Last.fm's top charts.", color = colors.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ArtistsScreen(state: RoomUiState, colors: RoomPalette, onRange: (TimeRange) -> Unit) {
    val rows = rangeEntries(state)
    val artists = if (state.activeHistory) rankArtists(rows) else state.stats?.artists.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeading("The artists in your orbit", "Familiar faces · ${state.range.label}", colors)
        RangeSelector(state.range, colors, onRange)
        Panel(colors) {
            PanelHeading("Your familiar faces", if (state.activeHistory) "Ranked by imported plays" else "Ranked by Last.fm scrobbles", colors, trailing = "TOP ${artists.size}")
            if (artists.isEmpty()) EmptyMessage("Connect Last.fm or import your Spotify history to discover your artists.", colors)
            artists.forEachIndexed { index, artist -> ArtistRow(artist, colors, index + 1) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalaxyScreen(state: RoomUiState, colors: RoomPalette, viewModel: RoomViewModel, onConnect: () -> Unit) {
    var selectedGenre by rememberSaveable(state.username) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeading("Your universe of sound", "A galaxy made from your top artists' Last.fm tags", colors)
        if (state.stats == null) {
            Panel(colors) {
                Text("Genre constellations", color = colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Connect a Last.fm profile to map genres from the tags on your most-played artists.", color = colors.muted, fontSize = 14.sp, lineHeight = 20.sp)
                Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = colors.deep, contentColor = Color.White)) { Text("Connect Last.fm") }
            }
        } else {
            if (state.genres.isEmpty() && !state.loadingGenres) androidx.compose.runtime.LaunchedEffect(state.username, state.range) { viewModel.loadGenres() }
            Panel(colors) {
                PanelHeading("A galaxy of genres", "Bubble size follows the play count of tagged artists", colors, trailing = "${state.genres.size} FLAVORS")
                if (state.loadingGenres) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Mapping your universe…", color = colors.muted) }
                else if (state.genres.isEmpty()) Text("No tags were returned for these artists. Try another time range.", color = colors.muted, fontSize = 13.sp)
                else {
                    Text(selectedGenre?.let { "$it · ${NumberFormat.getIntegerInstance().format(state.genres.firstOrNull { row -> row.first == it }?.second ?: 0)} plays behind this flavor" } ?: "Tap a flavor to spotlight it", color = colors.muted, fontSize = 11.sp)
                    val locations = listOf(.50f to .50f, .20f to .28f, .78f to .23f, .25f to .77f, .76f to .75f, .49f to .17f, .49f to .83f, .12f to .52f, .88f to .49f, .35f to .40f, .67f to .59f, .33f to .62f, .65f to .37f, .50f to .50f)
                    BoxWithConstraints(Modifier.fillMaxWidth().height(330.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .5f)
                            state.genres.take(10).forEachIndexed { index, _ ->
                                val point = locations[(index + 1) % locations.size]
                                drawLine(Color.White.copy(alpha = .3f), center, androidx.compose.ui.geometry.Offset(size.width * point.first, size.height * point.second), strokeWidth = 1.dp.toPx())
                            }
                            drawCircle(Color.White.copy(alpha = .24f), size.minDimension * .26f, center, style = Stroke(width = 1.dp.toPx()))
                        }
                        state.genres.take(10).forEachIndexed { index, (tag, plays) ->
                            val max = state.genres.maxOf { it.second }.coerceAtLeast(1)
                            val diameter = (62 + (plays.toDouble() / max * 46).toInt()).coerceIn(62, 108).dp
                            val point = if (index == 0) locations[0] else locations[index % locations.size]
                            val chosen = selectedGenre == tag
                            val dimmed = selectedGenre != null && !chosen
                            val scale by animateFloatAsState(if (chosen) 1.1f else 1f, tween(380, easing = FastOutSlowInEasing), label = "genre-bubble")
                            Box(Modifier.offset(x = maxWidth * point.first - diameter / 2, y = maxHeight * point.second - diameter / 2)
                                .size(diameter).graphicsLayer(scaleX = scale, scaleY = scale, alpha = if (dimmed) .34f else 1f)
                                .shadow(12.dp, CircleShape).clip(CircleShape)
                                .background(Brush.radialGradient(if (index == 0) listOf(colors.accent, colors.deep) else listOf(Color(0xFF676767), Color(0xFF252525))))
                                .border(1.dp, Color.White.copy(alpha = .34f), CircleShape).clickable { selectedGenre = if (chosen) null else tag }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(7.dp)) {
                                    Text(tag, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(NumberFormat.getIntegerInstance().format(plays), color = Color.White.copy(alpha = .72f), fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityScreen(state: RoomUiState, colors: RoomPalette, onRange: (TimeRange) -> Unit) {
    val rows = rangeEntries(state)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeading("Your listening, over time", "A diary in little moments · ${state.range.label}", colors)
        RangeSelector(state.range, colors, onRange)
        Panel(colors) {
            PanelHeading("Seven little days", "Recent activity · local time", colors)
            WeekChart(rows, state.activeHistory, colors)
            Text(if (state.activeHistory) "Bars show listening time from the Spotify export." else "Bars show scrobbles, not listening duration. Last.fm does not provide play duration.", color = colors.muted, fontSize = 11.sp)
        }
        Panel(colors) {
            PanelHeading("The last few songs", "${rows.size} available scrobbles", colors)
            if (rows.isEmpty()) EmptyMessage("No recent entries in this range.", colors)
            rows.take(60).forEachIndexed { index, entry ->
                RecentEntry(entry, colors, index == 0)
            }
            if (state.stats?.truncated == true && !state.activeHistory) Text("Last.fm's activity endpoint returned up to 200 recent items for this request.", color = colors.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RangeSelector(selected: TimeRange, colors: RoomPalette, onChange: (TimeRange) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeRange.entries.forEach { range ->
            val active = selected == range
            val scale by animateFloatAsState(if (active) 1f else .97f, animationSpec = tween(260, easing = FastOutSlowInEasing), label = "range-pill")
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) colors.deep else colors.card.copy(alpha = .56f),
                modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale).border(1.dp, Color.White.copy(alpha = if (active) .18f else .48f), RoundedCornerShape(50)).clickable { onChange(range) }
            ) { Text(range.label, color = if (active) Color.White else colors.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, note: String, icon: String, colors: RoomPalette, modifier: Modifier = Modifier) {
    Card(modifier.shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = .07f), spotColor = Color.Black.copy(alpha = .07f)).border(1.dp, Color.White.copy(alpha = .62f), RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = colors.card)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("$icon  $label", color = colors.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Text(value.uppercase(Locale.ROOT), color = colors.ink, fontSize = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = (-.35).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note, color = colors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Panel(colors: RoomPalette, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .54f), Color.White.copy(alpha = .28f))), RoundedCornerShape(22.dp)).border(1.dp, Color.White.copy(alpha = .58f), RoundedCornerShape(22.dp)).shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .10f))) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun PanelHeading(title: String, subtitle: String, colors: RoomPalette, trailing: String? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title.uppercase(), color = colors.ink, fontSize = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = (-.35).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer(scaleX = .88f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)))
            Text(subtitle, color = colors.muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (trailing != null) Text(trailing, color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp)
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String, colors: RoomPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp, bottom = 2.dp)) {
        Text(title.uppercase(), color = colors.ink, fontSize = 33.sp, lineHeight = 33.sp, fontWeight = FontWeight.Normal, letterSpacing = (-.9).sp, modifier = Modifier.graphicsLayer(scaleX = .86f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)))
        Text(subtitle.uppercase(), color = colors.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

internal object ArtworkLoader {
    private val memory = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val resolved = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val networkPermits = java.util.concurrent.Semaphore(4)

    fun cached(url: String): Bitmap? = memory.get(url)

    fun resolve(url: String, artist: String, track: String, isArtist: Boolean): String? {
        if (url.startsWith("https://") && !url.contains("2a96cbd8b46e442fc41c2b86b821562f")) return url
        val query = "${if (isArtist) "artist" else "track"}:${artist.trim().lowercase(Locale.ROOT)}:${track.trim().lowercase(Locale.ROOT)}"
        resolved[query]?.let { return it.ifBlank { null } }
        fun json(address: String): org.json.JSONObject? = runCatching {
            networkPermits.acquire()
            val connection = (URL(address).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 6_000
                setRequestProperty("User-Agent", "RoomAndroid/1.0")
            }
            try { connection.inputStream.bufferedReader().use { org.json.JSONObject(it.readText()) } }
            finally { connection.disconnect(); networkPermits.release() }
        }.getOrNull()
        fun encode(value: String) = android.net.Uri.encode(value)
        val image = if (isArtist) {
            val deezer = json("https://api.deezer.com/search/artist?q=${encode(artist)}&limit=5")
            val candidates = deezer?.optJSONArray("data")
            var found = ""
            for (index in 0 until (candidates?.length() ?: 0)) {
                val item = candidates?.optJSONObject(index) ?: continue
                val photo = item.optString("picture_big").ifBlank { item.optString("picture_medium") }
                if (photo.startsWith("https://") && !photo.contains("/images/artist//")) {
                    found = photo
                    if (item.optString("name").equals(artist, ignoreCase = true)) break
                }
            }
            if (found.isBlank()) {
                val itunes = json("https://itunes.apple.com/search?term=${encode(artist)}&media=music&entity=album&limit=3")?.optJSONArray("results")
                found = itunes?.optJSONObject(0)?.optString("artworkUrl100").orEmpty()
            }
            found
        } else {
            val itunes = json("https://itunes.apple.com/search?term=${encode("$artist $track")}&media=music&entity=song&limit=8")?.optJSONArray("results")
            var best = ""
            var bestScore = -1
            for (index in 0 until (itunes?.length() ?: 0)) {
                val item = itunes?.optJSONObject(index) ?: continue
                val titleMatch = item.optString("trackName").equals(track, ignoreCase = true)
                val artistMatch = item.optString("artistName").contains(artist, ignoreCase = true) || artist.contains(item.optString("artistName"), ignoreCase = true)
                val score = (if (titleMatch) 3 else 0) + (if (artistMatch) 2 else 0)
                if (score > bestScore) { bestScore = score; best = item.optString("artworkUrl100") }
            }
            if (best.isBlank()) {
                val key = BuildConfig.LASTFM_API_KEY
                val info = json("https://ws.audioscrobbler.com/2.0/?method=track.getinfo&api_key=${encode(key)}&artist=${encode(artist)}&track=${encode(track)}&format=json")
                val album = info?.optJSONObject("track")?.optJSONObject("album")?.optJSONArray("image")
                for (index in 0 until (album?.length() ?: 0)) {
                    val image = album?.optJSONObject(index)?.optString("#text").orEmpty()
                    if (image.startsWith("https://") && !image.contains("2a96cbd8b46e442fc41c2b86b821562f")) best = image
                }
            }
            best
        }.replace(Regex("/\\d+x\\d+bb\\.jpg$"), "/600x600bb.jpg")
        resolved[query] = image
        return image.takeIf { it.startsWith("https://") }
    }

    fun fetch(url: String): Bitmap? {
        memory.get(url)?.let { return it }
        networkPermits.acquire()
        val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "RoomAndroid/1.0")
        }
        return try {
            connection.inputStream.use { BitmapFactory.decodeStream(it) }?.also { memory.put(url, it) }
        } finally {
            connection.disconnect()
            networkPermits.release()
        }
    }
}

@Composable
private fun RoomArtwork(url: String, modifier: Modifier, contentScale: ContentScale, artist: String = "", track: String = "", isArtist: Boolean = false, fallback: @Composable () -> Unit) {
    val key = "$url|$artist|$track|$isArtist"
    val bitmap by produceState<Bitmap?>(ArtworkLoader.cached(url), key) {
        if (value == null) {
            val resolvedUrl = withContext(Dispatchers.IO) { runCatching { ArtworkLoader.resolve(url, artist, track, isArtist) }.getOrNull() }
            if (!resolvedUrl.isNullOrBlank()) value = withContext(Dispatchers.IO) { runCatching { ArtworkLoader.fetch(resolvedUrl) }.getOrNull() }
        }
    }
    if (bitmap == null) {
        Box(modifier, contentAlignment = Alignment.Center) { fallback() }
    } else {
        Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
    }
}

@Composable
private fun TrackRow(track: ListeningTrack, note: TrackNote, colors: RoomPalette, onClick: (ListeningTrack) -> Unit, rank: Int? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onClick(track) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (rank != null) Text(rank.toString().padStart(2, '0'), color = colors.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(25.dp))
        RoomArtwork(track.image, Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(colors.background), ContentScale.Crop, artist = track.artist, track = track.name) {
            Text("♫", color = colors.accent, fontSize = 20.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(track.name, color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = colors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note.rating > 0 || note.text.isNotBlank()) Text("${"★".repeat(note.rating)}${if (note.text.isNotBlank()) "  ${note.text}" else ""}", color = colors.accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text("${NumberFormat.getIntegerInstance().format(track.plays)}\nplays", color = colors.muted, fontSize = 9.sp, lineHeight = 12.sp)
    }
}

@Composable
private fun ArtistRow(artist: ListeningArtist, colors: RoomPalette, rank: Int? = null) {
    val animated = rank == null || rank <= 10
    val motion = if (animated) rememberInfiniteTransition(label = "artist-orb-${artist.name}") else null
    val yaw by if (animated) motion!!.animateFloat(-11f, 11f, infiniteRepeatable(tween(3_800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "artist-orb-yaw") else remember { mutableStateOf(0f) }
    val gloss by if (animated) motion!!.animateFloat(-.22f, .22f, infiniteRepeatable(tween(4_600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "artist-orb-gloss") else remember { mutableStateOf(0f) }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (rank != null) Text(rank.toString().padStart(2, '0'), color = colors.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(25.dp))
        Box(Modifier.size(46.dp).graphicsLayer(rotationY = yaw, rotationX = 3f, cameraDistance = 720f)) {
            RoomArtwork(artist.image, Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(listOf(colors.accent, colors.deep))), ContentScale.Crop, artist = artist.name, isArtist = true) {
                Text(artist.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.linearGradient(listOf(Color.White.copy(alpha = .25f), Color.Transparent, Color.Black.copy(alpha = .22f)), start = androidx.compose.ui.geometry.Offset(gloss * 100, 0f), end = androidx.compose.ui.geometry.Offset(46f, 46f))))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.tags.isNotEmpty()) Text(artist.tags.take(2).joinToString(" · "), color = colors.muted, fontSize = 10.sp)
        }
        Text("${NumberFormat.getIntegerInstance().format(artist.plays)} plays", color = colors.muted, fontSize = 10.sp)
    }
}

@Composable
private fun RecentEntry(entry: PlayEntry, colors: RoomPalette, isFirst: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (isFirst) colors.accent.copy(alpha = .3f) else colors.background), contentAlignment = Alignment.Center) {
            Text("♫", color = colors.ink, fontSize = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.track, color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, color = colors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(relative(entry.timestamp), color = colors.muted, fontSize = 9.sp)
    }
}

@Composable
private fun WeekChart(rows: List<PlayEntry>, isHistory: Boolean, colors: RoomPalette) {
    val formatter = remember { SimpleDateFormat("EEEEE", Locale.getDefault()) }
    val days = (6 downTo 0).map { back ->
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -back)
        }
        val start = calendar.timeInMillis
        val end = start + 24 * 60 * 60_000L
        val matching = rows.filter { it.timestamp in start until end }
        Triple(formatter.format(Date(start)).take(1).uppercase(), if (isHistory) matching.sumOf { it.durationMs }.toFloat() else matching.size.toFloat(), matching.size)
    }
    val maximum = (days.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth().height(118.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        days.forEach { (label, value, count) ->
            val barFraction by animateFloatAsState((value / maximum).coerceIn(.035f, 1f), tween(700, easing = FastOutSlowInEasing), label = "activity-bar")
            Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(if (count > 0) count.toString() else "", color = colors.muted, fontSize = 8.sp)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth(.52f).weight(1f).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)).background(colors.background), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(barFraction).background(Brush.verticalGradient(listOf(colors.accent, colors.deep.copy(alpha = .8f)))))
                }
                Spacer(Modifier.height(6.dp))
                Text(label, color = colors.muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Surface(color = bg, shape = RoundedCornerShape(50), modifier = Modifier.clickable(onClick = onClick)) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp))
    }
}

@Composable
private fun EmptyMessage(message: String, colors: RoomPalette) {
    Text(message, color = colors.muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 5.dp))
}

@Composable
private fun NoticeCard(text: String, isError: Boolean, colors: RoomPalette, dismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp).clip(RoundedCornerShape(13.dp)).background(if (isError) Color(0xFFFFE7E4) else colors.accent.copy(alpha = .18f)).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = if (isError) Color(0xFF8B2D25) else colors.ink, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = dismiss, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Close, null, tint = colors.muted, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun LinearLoading(colors: RoomPalette) {
    Box(Modifier.fillMaxWidth().height(2.dp).background(colors.accent.copy(alpha = .18f))) { Box(Modifier.fillMaxWidth(.35f).fillMaxSize().background(colors.accent)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(state: RoomUiState, colors: RoomPalette, viewModel: RoomViewModel, onDismiss: () -> Unit) {
    var username by rememberSaveable { mutableStateOf(state.username) }
    var apiKey by rememberSaveable { mutableStateOf(state.apiKey) }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card.copy(alpha = 1f),
        title = { Text("Make Room yours", color = colors.ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Connect Spotify in Last.fm, then enter your Last.fm username. Room reads public scrobbles; it never asks for your Spotify password.", color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Last.fm username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text(if (BuildConfig.LASTFM_API_KEY.isBlank()) "Last.fm API key" else "App API key configured") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = BuildConfig.LASTFM_API_KEY.isBlank())
                Text("The API key is public Last.fm app configuration, not a Spotify password. For a published build, the app owner should configure their key.", color = colors.muted, fontSize = 10.sp, lineHeight = 15.sp)
                OutlinedButton(onClick = { uriHandler.openUri("https://www.last.fm/settings/applications") }, modifier = Modifier.fillMaxWidth()) {
                    Text("♫", fontSize = 17.sp); Spacer(Modifier.width(8.dp)); Text("Link Spotify on Last.fm")
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("COLOR", color = colors.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("grey" to "Grey", "pink" to "Pink", "green" to "Green", "red" to "Red").forEach { (id, name) ->
                            val swatch = palette(id)
                            Surface(color = swatch.accent, shape = CircleShape, modifier = Modifier.clickable { viewModel.setTheme(id) }.size(if (state.theme == id) 37.dp else 31.dp), border = if (state.theme == id) androidx.compose.foundation.BorderStroke(2.dp, colors.ink) else null) { Box(contentAlignment = Alignment.Center) { if (state.theme == id) Text("✓", color = swatch.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
                        }
                    }
                }
                if (state.username.isNotBlank()) OutlinedButton(onClick = { viewModel.forgetLastFm(); username = "" }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect Last.fm") }
            }
        },
        confirmButton = { Button(onClick = { viewModel.connect(username, apiKey); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = colors.deep, contentColor = Color.White)) { Text("Show my stats") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = colors.muted) } }
    )
}

@Composable
private fun TrackNoteDialog(track: ListeningTrack, initial: TrackNote, colors: RoomPalette, onDismiss: () -> Unit, onSave: (TrackNote) -> Unit, onShare: (TrackNote) -> Unit) {
    var rating by remember(track.name, track.artist) { mutableIntStateOf(initial.rating) }
    var text by remember(track.name, track.artist) { mutableStateOf(initial.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card.copy(alpha = 1f),
        title = { Text("Why this song matters", color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(track.name, color = colors.ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(track.artist, color = colors.muted, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { star -> TextButton(onClick = { rating = if (rating == star) 0 else star }) { Text(if (star <= rating) "★" else "☆", color = colors.accent, fontSize = 25.sp) } }
                }
                OutlinedTextField(value = text, onValueChange = { text = it.take(140) }, label = { Text("One sentence") }, placeholder = { Text("Why does this one matter?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(TrackNote(rating, text.trim())) }, colors = ButtonDefaults.buttonColors(containerColor = colors.deep, contentColor = Color.White)) { Text("Save note") } },
        dismissButton = { TextButton(onClick = { onShare(TrackNote(rating, text.trim())) }) { Icon(Icons.Rounded.Share, null); Text("Share") } }
    )
}

private fun rangeEntries(state: RoomUiState): List<PlayEntry> {
    val rows = if (state.activeHistory) state.history.orEmpty() else state.stats?.recent.orEmpty()
    val seconds = state.range.seconds ?: return rows
    val cutoff = System.currentTimeMillis() - seconds * 1000
    return rows.filter { it.timestamp >= cutoff }
}

private fun rankTracks(rows: List<PlayEntry>) = rows.groupBy { it.track.lowercase() + "\u0000" + it.artist.lowercase() }
    .map { (_, entries) -> ListeningTrack(entries.first().track, entries.first().artist, entries.size, durationMs = entries.sumOf { it.durationMs }) }.sortedByDescending { it.plays }

private fun rankArtists(rows: List<PlayEntry>) = rows.groupBy { it.artist.lowercase() }
    .map { (_, entries) -> ListeningArtist(entries.first().artist, entries.size) }.sortedByDescending { it.plays }

private fun noteKey(track: ListeningTrack) = "${track.artist.trim().lowercase(Locale.ROOT)} | ${track.name.trim().lowercase(Locale.ROOT)}"

private fun relative(timestamp: Long): String {
    val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
    val mins = elapsed / 60_000
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (remaining == 0L) "$hours hr" else "$hours hr $remaining min"
}

private fun shareSummary(state: RoomUiState): String {
    val title = when {
        state.activeHistory -> "My Spotify history on Room"
        state.stats != null -> "My Last.fm listening on Room"
        else -> "My listening room"
    }
    val tracks = state.activeTracks.take(5).joinToString("\n") { "• ${it.name} — ${it.artist} (${it.plays} plays)" }
    val artists = state.activeArtists.take(3).joinToString(", ") { it.name }
    return buildString {
        appendLine(title)
        appendLine("${state.range.label} · ${state.stats?.totalScrobbles ?: state.history?.size ?: 0} plays")
        if (artists.isNotBlank()) appendLine("Artists: $artists")
        if (tracks.isNotBlank()) appendLine(tracks)
        append("Made with Room · https://abdu2l.github.io/Room/")
    }
}

private fun tabIcon(tab: RoomTab) = when (tab) {
    RoomTab.OVERVIEW -> Icons.Rounded.Home
    RoomTab.TRACKS -> Icons.Rounded.Favorite
    RoomTab.ARTISTS -> Icons.Rounded.AccountCircle
    RoomTab.GALAXY -> Icons.Rounded.Favorite
    RoomTab.ACTIVITY -> Icons.Rounded.Refresh
}

private fun Color.toArgbInt(): Int = AndroidColor.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
