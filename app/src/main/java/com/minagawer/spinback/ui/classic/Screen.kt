package com.minagawer.spinback.ui.classic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.minagawer.spinback.data.Library
import com.minagawer.spinback.data.Track
import com.minagawer.spinback.playback.PlayerConnection
import com.minagawer.spinback.ui.Artwork
import com.minagawer.spinback.ui.SpinbackIcons
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Rows on screen at once: nine single lines, five with a second line, or four with artwork. */
private const val VISIBLE_ROWS = 9
private const val VISIBLE_TWO_LINE_ROWS = 5
private const val VISIBLE_ARTWORK_ROWS = 4

private val MenuItem.hasTile get() = artwork != null || allSongsTile
private val AllSongsBackground =
    Brush.verticalGradient(listOf(Color(0xFF2E3034), Color(0xFF101114)))
private val TileDivider = Color(0xFFD9DADD)
private const val SCROLL_BAR_LINGER_MS = 700L
private val ScrollThumb = Color.Black.copy(alpha = 0.4f)

private val LcdBackground = Color.White
private val LcdShadow = Color.Black.copy(alpha = 0.25f)
private val LCD_SHADOW_DEPTH = 4.dp
private val LcdText = Color(0xFF111111)
private val LcdSubtext = Color(0xFF6B6E73)
private val LcdLine = Color(0xFF9EA2A8)
private val Highlight =
    Brush.verticalGradient(listOf(Color(0xFF8CC8F7), Color(0xFF4495E3), Color(0xFF2B79D3)))
private val TitleBar = Brush.verticalGradient(
    listOf(0xFFFDFDFD, 0xFFEDEDED, 0xFFE7E7E7, 0xFFD1D1D1, 0xFFBBBBBB, 0xFFABABAB).map(::Color)
)
private val TITLE_BAR_HEIGHT = 26.dp
private val STATUS_HEIGHT = 16.dp
private val BatteryCase = Color(0xFF54585B)
private val BatteryEdge = Color(0xFF626262)
private val BatteryCap = Color(0xFFC4C4C4)
private val BatteryHealthy = Color(0xFFA5E07F)
private val BatteryLow = Color(0xFFD17F6B)

/** The bolt and plug: a deep shade of the charge's green. */
private val BatteryGlyph = Color(0xFF2F5F22)
private val SongDetail = Color(0xFF4A4C50)

// An Aqua-style glossy bar: a frosted upper half, then a hard edge into clear blue that glows
// brighter toward the bottom.
// A gradient without explicit bounds spans the whole canvas, not the rect it fills, so these take
// the height they are drawn at.
private fun barFill(h: Float) = Brush.verticalGradient(
    0f to Color(0xFF7DA5F5),
    0.5f to Color(0xFF73A3F3),
    0.5f to Color(0xFF4E99F2),
    0.75f to Color(0xFF58B3FF),
    1f to Color(0xFF96D5FF),
    startY = 0f,
    endY = h,
)

/** White sheen over the upper half, strongest at the top. */
private fun barGloss(h: Float) = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f)),
    startY = 0f,
    endY = h / 2,
)

private val BarFillTopEdge = Color(0xFF8AA1DB)
private val BarFillEdge = Color(0xFF6BA8D4)
private fun barTrack(h: Float) = Brush.verticalGradient(
    0f to Color(0xFFFDFDFD),
    0.5f to Color(0xFFF3F3F3),
    0.5f to Color(0xFFE5E6E6),
    1f to Color(0xFFF1F2F2),
    startY = 0f,
    endY = h,
)

private val BarTrackEdge = Color(0xFFC5C5C5)

/** The empty part of the bar is a groove: a shadow falls from its top edge. */
private fun barTrackShadow(h: Float) = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.14f),
    0.06f to Color.Black.copy(alpha = 0.06f),
    0.18f to Color.Black.copy(alpha = 0f),
    startY = 0f,
    endY = h,
)

private fun barReflection(h: Float) = Brush.verticalGradient(
    listOf(Color(0xFFBFE2FC).copy(alpha = 0.55f), Color(0xFFBFE2FC).copy(alpha = 0f)),
    startY = 0f,
    endY = h,
)

/** The 4:3 LCD: a title bar over the current page. */
@Composable
fun ClassicScreen(
    page: Page,
    library: Library,
    player: PlayerConnection,
    wheelHeld: Boolean,
    /** The hairline around the screen, the same as the wheel's. */
    edge: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    // On the main menu the cover takes the whole right half, title bar and all.
    val split = page is MenuPage && page.showArtwork
    Row(
        modifier
            .clip(shape)
            .background(LcdBackground)
            // A thin shadow right at the edge all round, so the screen sits a little below the case.
            .drawWithContent {
                drawContent()
                val depth = LCD_SHADOW_DEPTH.toPx()
                // Dark at the very edge, dropping off fast so it stays near the rim.
                val fade = listOf(
                    LcdShadow,
                    LcdShadow.copy(alpha = LcdShadow.alpha * 0.3f),
                    Color.Transparent
                )
                drawRect(Brush.horizontalGradient(fade, startX = 0f, endX = depth))
                drawRect(
                    Brush.horizontalGradient(
                        fade,
                        startX = size.width,
                        endX = size.width - depth
                    )
                )
                drawRect(Brush.verticalGradient(fade, startY = 0f, endY = depth))
                drawRect(
                    Brush.verticalGradient(
                        fade,
                        startY = size.height,
                        endY = size.height - depth
                    )
                )
            }
            .border(1.dp, edge, shape),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            TitleBar(page.title, player)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (page) {
                    is MenuPage -> MenuScreen(page, player, wheelHeld)
                    is NowPlayingPage -> NowPlayingScreen(page, library, player)
                }
            }
        }
        if (split) {
            ArtworkPane(
                library,
                player,
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun TitleBar(title: String, player: PlayerConnection) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TITLE_BAR_HEIGHT)
            .background(TitleBar)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            color = LcdText,
            // Bold, as on the iPod.
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val state = when {
            player.scanningForward == true -> PlayState.Forward
            player.scanningForward == false -> PlayState.Rewind
            player.currentTrackId == null -> null
            player.isPlaying -> PlayState.Playing
            else -> PlayState.Paused
        }
        if (state != null) {
            PlayStateGlyph(state)
            Spacer(Modifier.width(10.dp))
        }
        Battery()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LcdLine),
    )
}

private enum class PlayState { Playing, Paused, Forward, Rewind }

/** The title bar's glossy blue play / pause / scan glyph, as tall as the battery. */
@Composable
private fun PlayStateGlyph(state: PlayState) {
    val width = when (state) {
        PlayState.Playing -> 15.dp
        PlayState.Paused -> 12.dp
        PlayState.Forward, PlayState.Rewind -> 22.dp
    }
    Canvas(Modifier.size(width = width, height = STATUS_HEIGHT)) {
        val h = size.height
        // The seek bar's blues, darker at the ends and brightest just below the middle.
        val fill = Brush.verticalGradient(
            0f to Color(0xFF4E99F2),
            0.2f to Color(0xFF58B3FF),
            0.4f to Color(0xFF7DC4FF),
            0.55f to Color(0xFF96D5FF),
            0.7f to Color(0xFF8ACDFF),
            0.82f to Color(0xFF58B3FF),
            1f to Color(0xFF4E99F2),
            startY = 0f,
            endY = h,
        )
        // The rim is dark at the corners and catches the light in the middle.
        val rim = Brush.verticalGradient(
            0f to Color(0xFF3A78C8),
            0.5f to Color(0xFF73A3F3),
            1f to Color(0xFF3A78C8),
            startY = 0f,
            endY = h,
        )
        val outline = Stroke(1.dp.toPx())
        fun triangle(left: Float, w: Float, pointRight: Boolean) = Path().apply {
            if (pointRight) {
                moveTo(left, 0f)
                lineTo(left + w, h / 2)
                lineTo(left, h)
            } else {
                moveTo(left + w, 0f)
                lineTo(left, h / 2)
                lineTo(left + w, h)
            }
            close()
        }

        val shapes = when (state) {
            PlayState.Playing -> listOf(triangle(0f, size.width, pointRight = true))
            PlayState.Forward -> listOf(
                triangle(0f, size.width / 2, pointRight = true),
                triangle(size.width / 2, size.width / 2, pointRight = true),
            )

            PlayState.Rewind -> listOf(
                triangle(0f, size.width / 2, pointRight = false),
                triangle(size.width / 2, size.width / 2, pointRight = false),
            )

            PlayState.Paused -> {
                val bar = size.width * 0.36f
                listOf(0f, size.width - bar).map { x ->
                    Path().apply { addRect(Rect(x, 0f, x + bar, h)) }
                }
            }
        }
        shapes.forEach {
            drawPath(it, fill)
            drawPath(it, rim, style = outline)
        }
    }
}

private data class BatteryState(val level: Float, val charging: Boolean, val plugged: Boolean)

private fun Intent.toBatteryState() = BatteryState(
    level = getIntExtra(BatteryManager.EXTRA_LEVEL, 100) / getIntExtra(
        BatteryManager.EXTRA_SCALE,
        100
    ).toFloat(),
    charging = getIntExtra(
        BatteryManager.EXTRA_STATUS,
        -1
    ) == BatteryManager.BATTERY_STATUS_CHARGING,
    plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
)

/**
 * A slate case lit from below, a green charge (red when low) with a light band and shaded base,
 * and a rounded cap. A bolt shows while charging; a plug once full on the charger.
 */
@Composable
private fun Battery(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by produceState(BatteryState(1f, charging = false, plugged = false)) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = intent.toBatteryState()
            }
        }
        // Battery changes are a sticky broadcast, so registering also hands back the current state.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { value = it.toBatteryState() }
        awaitDispose { context.unregisterReceiver(receiver) }
    }
    val full = state.level >= 0.995f
    // Twice as wide as it is tall, frame included, and as tall as the play glyph.
    val bodyHeight = STATUS_HEIGHT
    val bodyWidth = bodyHeight * 2
    val capWidth = bodyWidth * 0.055f
    Canvas(modifier.size(width = bodyWidth + capWidth, height = bodyHeight)) {
        val line = 1.dp.toPx()
        val cap = Size(capWidth.toPx(), size.height * 0.28f)
        val body = Size(size.width - cap.width, size.height)

        drawRect(BatteryCase, size = body)
        drawRect(
            Brush.verticalGradient(
                0.25f to Color.Transparent,
                0.65f to Color.White.copy(alpha = 0.25f),
                1f to Color.White.copy(alpha = 0.5f),
                endY = body.height,
            ),
            size = body,
        )

        val inner = Size(body.width - line * 2, body.height - line * 2)
        // While charging the iPod shows a full bar, with the bolt saying it isn't full yet.
        val shown = if (state.charging) 1f else state.level.coerceIn(0.15f, 1f)
        val charge = Size(inner.width * shown, inner.height)
        translate(line, line) {
            drawRect(
                if (state.level > 0.2f || state.charging) BatteryHealthy else BatteryLow,
                size = charge
            )
            drawRect(
                Brush.verticalGradient(
                    0.1f to Color.Transparent,
                    0.2f to Color.White.copy(alpha = 0.5f),
                    0.35f to Color.White.copy(alpha = 0.25f),
                    0.4f to Color.Transparent,
                    0.55f to Color.Black.copy(alpha = 0.2f),
                    0.65f to Color.Black.copy(alpha = 0.3f),
                    0.8f to Color.Black.copy(alpha = 0.4f),
                    endY = inner.height,
                ),
                size = charge,
            )
        }
        drawRect(
            BatteryEdge,
            Offset(line / 2, line / 2),
            Size(body.width - line, body.height - line),
            style = Stroke(line)
        )

        // The cap: framed on three sides, rounded on the right.
        val capTop = (size.height - cap.height) / 2
        val capPath = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(body.width - line, capTop, size.width, capTop + cap.height),
                    topRight = CornerRadius(1.5.dp.toPx()),
                    bottomRight = CornerRadius(1.5.dp.toPx()),
                ),
            )
        }
        drawPath(capPath, BatteryCap)
        drawPath(capPath, BatteryEdge, style = Stroke(line))

        if (state.plugged && (state.charging || full)) {
            val glyph = if (state.charging && !full) BoltPath else PlugPath
            val glyphHeight = body.height * if (glyph === BoltPath) 0.9f else 0.5f
            val scale = glyphHeight / glyph.getBounds().height
            val bounds = glyph.getBounds()
            translate(
                left = (body.width - bounds.width * scale) / 2 - bounds.left * scale,
                top = (body.height - glyphHeight) / 2 - bounds.top * scale,
            ) {
                scale(scale, pivot = Offset.Zero) {
                    drawPath(glyph, BatteryGlyph)
                }
            }
        }
    }
}

/** A lightning bolt, on a 10x16 grid. */
private val BoltPath =
    PathParser().parsePathString("M6.5,0L0.5,9H4.6L3.5,16L9.5,6.6H5.4L6.5,0Z").toPath()

/**
 * A plug as on the iPod's "Charged" screen, on a 19x11.5 grid: a short cord, a body rounded on the
 * cord side, and two prongs to the right.
 */
private val PlugPath = PathParser().parsePathString(
    "M0,4.6H5V6.9H0Z" +
            "M8,0H14V11.5H8A4,5.75 0 0 1 8,0Z" +
            "M14,2H18.8V3.9H14Z" +
            "M14,7.6H18.8V9.5H14Z",
).toPath()

@Composable
private fun MenuScreen(page: MenuPage, player: PlayerConnection, wheelHeld: Boolean) {
    val items = page.items
    val selected = page.selected.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val rows = when {
        items.any { it.hasTile } -> VISIBLE_ARTWORK_ROWS
        items.any { it.subtitle != null } -> VISIBLE_TWO_LINE_ROWS
        else -> VISIBLE_ROWS
    }

    // Keep the selected row wholly on screen. Go by where the row actually is: rows are rounded to
    // whole pixels, so the list can rest with its first row all but hidden yet still "first".
    LaunchedEffect(selected) {
        val state = page.listState
        val info = state.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == selected }
        when {
            row == null ->
                state.scrollToItem(if (selected < state.firstVisibleItemIndex) selected else selected - rows + 1)

            row.offset < info.viewportStartOffset -> state.scrollToItem(selected)
            row.offset + row.size > info.viewportEndOffset ->
                state.scrollBy((row.offset + row.size - info.viewportEndOffset).toFloat())
        }
    }

    // The scroll bar shows once the wheel turns and starts fading the moment the thumb lifts.
    // (Moving the selection some other way, it lingers briefly instead.)
    var lastSelected by remember(page) { mutableIntStateOf(selected) }
    var scrolling by remember(page) { mutableStateOf(false) }
    val held by rememberUpdatedState(wheelHeld)
    LaunchedEffect(selected) {
        if (selected == lastSelected) return@LaunchedEffect
        lastSelected = selected
        scrolling = true
        if (!held) {
            delay(SCROLL_BAR_LINGER_MS)
            scrolling = false
        }
    }
    LaunchedEffect(wheelHeld) {
        if (!wheelHeld) scrolling = false
    }
    val scrollBarAlpha by animateFloatAsState(
        if (scrolling) 1f else 0f,
        // Quick to appear, slow to fade.
        tween(if (scrolling) 150 else 250, easing = LinearEasing),
        label = "scrollBar",
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowHeight = maxHeight / rows
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                LazyColumn(Modifier.fillMaxSize(), state = page.listState) {
                    itemsIndexed(items) { i, item ->
                        MenuRow(
                            item,
                            i == selected,
                            rowHeight,
                            playing = item.trackId != null && item.trackId == player.currentTrackId,
                        ) {
                            page.selected = i
                            item.onSelect()
                        }
                    }
                }
                if (items.size > rows) {
                    ScrollBar(
                        page.listState,
                        items.size,
                        rows,
                        Modifier
                            .align(Alignment.CenterEnd)
                            .graphicsLayer { alpha = scrollBarAlpha },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    item: MenuItem,
    selected: Boolean,
    height: Dp,
    playing: Boolean,
    onClick: () -> Unit
) {
    if (item.hasTile || item.subtitle != null) {
        TwoLineRow(item, selected, height, playing, onClick)
        return
    }
    val textColor = if (selected) Color.White else LcdText
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (selected) Modifier.background(Highlight) else Modifier)
            .clickable(onClick = onClick)
            // A small end padding: the ">" sits near the edge (its glyph has its own margin).
            .padding(start = 8.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.label,
            Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .then(if (selected) Modifier.selectedMarquee() else Modifier),
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.detail != null) {
            Text(
                item.detail,
                Modifier.padding(end = 8.dp),
                color = if (selected) Color.White else LcdSubtext,
                fontSize = 13.sp,
            )
        }
        if (playing) {
            Icon(
                SpinbackIcons.VolumeUp,
                "再生中",
                Modifier
                    .padding(end = 6.dp)
                    .size(16.dp),
                tint = if (selected) Color.White else LcdText,
            )
        }
        // Like the iPod, only the highlighted row shows where it leads.
        if (item.opens && selected) {
            Icon(SpinbackIcons.Chevron, null, Modifier.size(18.dp), tint = textColor)
        }
    }
}

/** The "all songs" tile: a big pair of beamed eighth notes on near-black. */
@Composable
private fun AllSongsTile(size: Dp) {
    Canvas(
        Modifier
            .size(size)
            .background(AllSongsBackground),
    ) {
        // Laid out on a 24-unit grid, then scaled to the tile.
        val u = this.size.width / 24f
        // A heavy, gently rising beam over two thick stems.
        val beamAndStems = Path().apply {
            moveTo(8.9f * u, 6.1f * u)
            lineTo(17.6f * u, 3.8f * u)
            lineTo(17.6f * u, 16.6f * u)
            lineTo(16.1f * u, 16.6f * u)
            lineTo(16.1f * u, 7.05f * u)
            lineTo(10.25f * u, 8.6f * u)
            lineTo(10.25f * u, 18.2f * u)
            lineTo(8.9f * u, 18.2f * u)
            close()
        }
        drawPath(beamAndStems, Highlight)
        // Small, nearly round heads at the foot of each stem, flush with its right side.
        val head = Size(5f * u, 4.4f * u)
        listOf(Offset(7.75f * u, 18.2f * u), Offset(15.1f * u, 16.6f * u)).forEach { c ->
            rotate(-15f, pivot = c) {
                drawOval(Highlight, Offset(c.x - head.width / 2, c.y - head.height / 2), head)
            }
        }
    }
}

/** A long label on the selected row scrolls sideways so all of it can be read, as on the iPod. */
private fun Modifier.selectedMarquee() = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = 1_000,
    spacing = MarqueeSpacing(32.dp),
)

/**
 * A bold title over a small second line (an artist, a song count). With artwork, the cover fills
 * the left edge, and the highlight and divider cover only the text, never the cover.
 */
@Composable
private fun TwoLineRow(
    item: MenuItem,
    selected: Boolean,
    height: Dp,
    playing: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onClick),
    ) {
        if (item.artwork != null) {
            Artwork(item.artwork, height, shape = RectangleShape)
        } else if (item.allSongsTile) {
            AllSongsTile(height)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (selected) Modifier.background(Highlight) else Modifier),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                ) {
                    Text(
                        item.label,
                        if (selected) Modifier.selectedMarquee() else Modifier,
                        color = if (selected) Color.White else LcdText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.subtitle != null) {
                        Text(
                            item.subtitle,
                            color = if (selected) Color.White else LcdSubtext,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
                if (playing) {
                    Icon(
                        SpinbackIcons.VolumeUp,
                        "再生中",
                        Modifier
                            .padding(end = 6.dp)
                            .size(16.dp),
                        tint = if (selected) Color.White else LcdText,
                    )
                }
                if (item.opens && selected) {
                    Icon(SpinbackIcons.Chevron, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
            if (!selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TileDivider),
                )
            }
        }
    }
}

/** A thin see-through grey thumb on the right edge, drawn over the rows (no track), as on the iPod classic. */
@Composable
private fun ScrollBar(state: LazyListState, total: Int, rows: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .padding(end = 2.dp)
            .width(5.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp),
    ) {
        val thumbHeight = maxOf(maxHeight * rows / total, 10.dp)
        // Rows are rounded to whole pixels, so at the end the list can stop a fraction of a row
        // short of "first visible == total - rows"; go by where it can still scroll instead.
        val progress = when {
            !state.canScrollForward -> 1f
            !state.canScrollBackward -> 0f
            else -> {
                val rowSize =
                    state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
                (state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset.toFloat() / rowSize) /
                        (total - rows).coerceAtLeast(1)
            }
        }
        Box(
            Modifier
                .offset(y = (maxHeight - thumbHeight) * progress.coerceIn(0f, 1f))
                .fillMaxWidth()
                .height(thumbHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(ScrollThumb),
        )
    }
}

/** Where a cover's slow pan starts and ends, as fractions (-1..1) of the room it has to move. */
private data class KenBurns(
    val startX: Float,
    val endX: Float,
    val startY: Float,
    val endY: Float
) {
    companion object {
        fun from(random: Random): KenBurns {
            // Along an axis, start near one side and end near the other, so the pan travels a good
            // way; across it, hold still somewhere in the middle.
            fun travel(): Pair<Float, Float> {
                val sign = if (random.nextBoolean()) 1 else -1
                return sign * (0.45f + random.nextFloat() * 0.3f) to -sign * (0.45f + random.nextFloat() * 0.3f)
            }

            fun hold(): Pair<Float, Float> = (random.nextFloat() - 0.5f).let { it to it }
            // Sideways, up or down, or diagonally.
            val (x, y) = when (random.nextInt(3)) {
                0 -> travel() to hold()
                1 -> hold() to travel()
                else -> travel() to travel()
            }
            return KenBurns(x.first, x.second, y.first, y.second)
        }
    }
}

/** How much larger than the pane a cover is drawn, leaving room to pan in every direction. */
private const val COVER_SCALE = 1.35f
private const val COVER_INTERVAL_MS = 11_000
private const val COVER_FADE_MS = 600

/** Right half of the main menu: the playing song's art, or a slow shuffle through album covers. */
@Composable
private fun ArtworkPane(library: Library, player: PlayerConnection, modifier: Modifier = Modifier) {
    val current = player.currentTrackId?.let(library.byId::get)
    val covers = remember(library) {
        library.albums.mapNotNull { album -> album.tracks.firstOrNull { it.artwork != null } }
            .shuffled()
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(current == null) {
        while (current == null && covers.isNotEmpty()) {
            delay(COVER_INTERVAL_MS.toLong())
            index = (index + 1) % covers.size
        }
    }
    val shown: Track? = current ?: covers.getOrNull(index)
    BoxWithConstraints(
        modifier
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val side = maxOf(maxWidth, maxHeight)
        val paneWidth = maxWidth
        val paneHeight = maxHeight
        Crossfade(shown, animationSpec = tween(COVER_FADE_MS), label = "cover") { track ->
            if (track != null) {
                // The view slowly pans across each cover while it's up, along a path picked from the
                // album so it's the same every time.
                val drift = remember(track) { Animatable(0f) }
                LaunchedEffect(track) {
                    drift.animateTo(
                        1f,
                        tween(COVER_INTERVAL_MS + COVER_FADE_MS, easing = LinearEasing)
                    )
                }
                val path = remember(track) { KenBurns.from(Random(track.album.hashCode())) }
                Artwork(
                    track,
                    side,
                    Modifier
                        .requiredSize(side)
                        .graphicsLayer {
                            val p = drift.value
                            scaleX = COVER_SCALE
                            scaleY = COVER_SCALE
                            // Never pan past the cover's edge.
                            val roomX =
                                ((side * COVER_SCALE - paneWidth) / 2).toPx().coerceAtLeast(0f)
                            val roomY =
                                ((side * COVER_SCALE - paneHeight) / 2).toPx().coerceAtLeast(0f)
                            translationX = roomX * (path.startX + (path.endX - path.startX) * p)
                            translationY = roomY * (path.startY + (path.endY - path.startY) * p)
                        },
                    shape = RectangleShape
                )
            }
        }
        // The menu casts a soft black shadow onto the cover's left edge.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val width = 26.dp.toPx()
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.5f),
                            0.15f to Color.Black.copy(alpha = 0.35f),
                            0.35f to Color.Black.copy(alpha = 0.17f),
                            0.6f to Color.Black.copy(alpha = 0.05f),
                            1f to Color.Transparent,
                            startX = 0f,
                            endX = width,
                        ),
                        size = Size(width, size.height),
                    )
                },
        )
    }
}

@Composable
private fun NowPlayingScreen(page: NowPlayingPage, library: Library, player: PlayerConnection) {
    val track = player.currentTrackId?.let(library.byId::get) ?: return
    var position by remember { mutableLongStateOf(player.positionMs) }
    LaunchedEffect(track.id) {
        while (true) {
            position = player.positionMs
            delay(250)
        }
    }
    LaunchedEffect(page.mode, page.lastInteraction) {
        if (page.mode != NowPlayingMode.Progress) {
            delay(3_000)
            page.mode = NowPlayingMode.Progress
        }
    }
    val duration = player.durationMs.takeIf { it > 0 } ?: track.durationMs

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        val artSize = maxHeight * 0.68f
        val barHeight = maxHeight * 0.064f
        // The cover's reflection fades out just below the bottom of the progress bar.
        // The bar row sits on the bottom padding, with the bar centered in its reflection margins.
        val reflectionHeight =
            maxHeight - ART_TOP - artSize - BAR_ROW_BOTTOM - barHeight * BAR_REFLECTION_RATIO + REFLECTION_OVERSHOOT
        val titleSize = with(LocalDensity.current) { (maxHeight * 0.075f).toSp() }
        Row(
            Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (player.repeatMode != Player.REPEAT_MODE_OFF) {
                val repeat =
                    if (player.repeatMode == Player.REPEAT_MODE_ONE) SpinbackIcons.RepeatOne else SpinbackIcons.Repeat
                Icon(repeat, "リピート", Modifier.size(16.dp), tint = LcdText)
            }
            if (player.shuffle) {
                Icon(SpinbackIcons.Shuffle, "シャッフル", Modifier.size(16.dp), tint = LcdText)
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = ART_TOP),
            ) {
                // Stretched but turned away, the cover ends up about as wide as its layout box.
                ReflectedArtwork(
                    track,
                    artSize,
                    reflectionHeight,
                    // Mostly gone a little above the bar, then a faint tail runs on past it.
                    fadedAt = reflectionHeight - REFLECTION_OVERSHOOT - barHeight - 4.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.height(artSize)) {
                    Spacer(Modifier.height(artSize * 0.15f))
                    Text(
                        track.name,
                        Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 2_000,
                            spacing = MarqueeSpacing(32.dp),
                        ),
                        color = LcdText,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        track.artist,
                        color = SongDetail,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.album,
                        color = SongDetail,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Where the star rating would go.
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "${player.queueIndex + 1} / ${player.queueSize}",
                        color = SongDetail,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // The volume bar takes the progress bar's exact place: the same row, with fixed-width
            // slots either side for the times or the speaker icons, both hugging the bar.
            val volume = page.mode == NowPlayingMode.Volume
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = BAR_ROW_BOTTOM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(BAR_SIDE_SLOT), contentAlignment = Alignment.CenterEnd) {
                    if (volume) {
                        Icon(SpinbackIcons.VolumeDown, "音量", Modifier.size(18.dp), tint = LcdText)
                    } else {
                        Text(
                            formatTime(position),
                            color = LcdText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Bar(
                    if (volume) page.volume else if (duration > 0) position.toFloat() / duration else 0f,
                    scrubbing = page.mode == NowPlayingMode.Scrub,
                    barHeight = barHeight,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Box(Modifier.width(BAR_SIDE_SLOT), contentAlignment = Alignment.CenterStart) {
                    if (volume) {
                        Icon(SpinbackIcons.VolumeUp, null, Modifier.size(18.dp), tint = LcdText)
                    } else {
                        Text(
                            "-" + formatTime((duration - position).coerceAtLeast(0)),
                            color = LcdText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** The cover, a little wide and turned away, standing on a faded mirror image of itself. */
@Composable
private fun ReflectedArtwork(track: Track, size: Dp, reflectionHeight: Dp, fadedAt: Dp) {
    val height = reflectionHeight.coerceIn(0.dp, size)
    // Measured down from the bottom of the cover.
    val faded = (fadedAt / height).coerceIn(0.05f, 1f)
    Column(
        Modifier
            // The reflection may run on under the progress bar.
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .graphicsLayer {
                rotationY = ARTWORK_TILT_DEGREES
                scaleX = ARTWORK_STRETCH
                transformOrigin = TransformOrigin(0f, 0.5f)
                cameraDistance = 12f * density
            },
    ) {
        Artwork(track, size, shape = RectangleShape)
        Box(
            Modifier
                .size(size, height)
                .clipToBounds(),
        ) {
            Artwork(
                track,
                size,
                Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer {
                        scaleY = -1f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            // Drawn before the flip, so the cover's bottom edge is at 1f here.
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                (1 - faded) to Color.Black.copy(alpha = REFLECTION_ALPHA * 0.15f),
                                (1 - faded / 2) to Color.Black.copy(alpha = REFLECTION_ALPHA * 0.5f),
                                1f to Color.Black.copy(alpha = REFLECTION_ALPHA),
                                startY = this.size.height - height.toPx(),
                                endY = this.size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                shape = RectangleShape,
            )
        }
    }
}

private const val ARTWORK_TILT_DEGREES = 28f
private const val ARTWORK_STRETCH = 1.15f
private val ART_TOP = 12.dp
private val BAR_ROW_BOTTOM = 4.dp

/** Room either side of the progress / volume bar, wide enough for a time like "-10:00". */
private val BAR_SIDE_SLOT = 46.dp

/** How far past the bottom of the progress bar the cover's reflection runs before it has faded out. */
private val REFLECTION_OVERSHOOT = 8.dp
private const val REFLECTION_ALPHA = 0.5f

/**
 * The glossy progress bar, with a faint reflection of the filled part below it; while scrubbing, a
 * diamond marks the playhead.
 */
@Composable
private fun Bar(fraction: Float, scrubbing: Boolean, barHeight: Dp, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            // Room above and below for the reflection, keeping the bar itself centered.
            .height(barHeight * (1 + 2 * BAR_REFLECTION_RATIO)),
    ) {
        // Blurred on its own layer so it goes soft as it fades out.
        Canvas(
            Modifier
                .matchParentSize()
                .blur(barHeight * 0.2f, BlurredEdgeTreatment.Unbounded),
        ) {
            val h = barHeight.toPx()
            if (!scrubbing) {
                translate(top = (size.height + h) / 2) {
                    drawRect(
                        barReflection(h * BAR_REFLECTION_RATIO),
                        size = Size(size.width * f, h * BAR_REFLECTION_RATIO)
                    )
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val h = barHeight.toPx()
            val top = (size.height - h) / 2
            // Gradients start at the origin, so move it to the bar rather than offsetting the rects.
            translate(top = top) {
                val w = size.width * f
                val line = 1.dp.toPx()
                drawRect(barTrack(h), size = Size(size.width, h))
                drawRect(barTrackShadow(h), size = Size(size.width, h))
                drawRect(BarTrackEdge, Offset(0f, h - line), Size(size.width, line))
                // While scrubbing the bar empties and only the diamond marks the position.
                if (!scrubbing) {
                    drawRect(barFill(h), size = Size(w, h))
                    drawRect(barGloss(h), size = Size(w, h / 2))
                    drawRect(BarFillTopEdge, size = Size(w, line))
                    drawRect(BarFillEdge, Offset(0f, h - line), Size(w, line))
                }
            }
            if (scrubbing) {
                // A small diamond in the bar's own glossy blue, about three quarters of its height.
                val x = size.width * f
                val r = h * 0.375f
                translate(top = top + h / 2 - r) {
                    val diamond = Path().apply {
                        moveTo(x, 0f)
                        lineTo(x + r, r)
                        lineTo(x, 2 * r)
                        lineTo(x - r, r)
                        close()
                    }
                    drawPath(diamond, barFill(2 * r))
                    drawPath(diamond, barGloss(2 * r))
                    // A rounded join softens the corners slightly.
                    drawPath(
                        diamond,
                        BarFillEdge,
                        style = Stroke(1.dp.toPx(), join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

private const val BAR_REFLECTION_RATIO = 0.65f

fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
