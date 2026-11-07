package com.minagawer.spinback.ui.classic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minagawer.spinback.R
import com.minagawer.spinback.playback.PlayerConnection
import com.minagawer.spinback.ui.Artwork
import com.minagawer.spinback.ui.SpinbackIcons
import kotlinx.coroutines.delay

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

@Composable
internal fun MenuScreen(page: MenuPage, player: PlayerConnection, wheelHeld: Boolean) {
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
        if (playing) PlayingMark(selected)
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

/** The speaker at the end of the row that's playing. */
@Composable
private fun PlayingMark(selected: Boolean) {
    Icon(
        SpinbackIcons.VolumeUp,
        stringResource(R.string.now_playing),
        Modifier
            .padding(end = 6.dp)
            .size(16.dp),
        tint = if (selected) Color.White else LcdText,
    )
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
                if (playing) PlayingMark(selected)
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
