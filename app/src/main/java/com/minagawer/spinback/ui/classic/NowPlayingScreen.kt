package com.minagawer.spinback.ui.classic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.minagawer.spinback.R
import com.minagawer.spinback.data.Library
import com.minagawer.spinback.data.Track
import com.minagawer.spinback.playback.PlayerConnection
import com.minagawer.spinback.ui.Artwork
import com.minagawer.spinback.ui.SpinbackIcons
import kotlinx.coroutines.delay

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

@Composable
internal fun NowPlayingScreen(page: NowPlayingPage, library: Library, player: PlayerConnection) {
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
        ModeIcons(player, Modifier.align(Alignment.TopEnd))
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
                SongInfo(track, player, artSize, titleSize)
            }
            BarRow(page, position, duration, barHeight)
        }
    }
}

/** Shuffle and repeat, in the top corner while they're on. */
@Composable
private fun ModeIcons(player: PlayerConnection, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (player.repeatMode != Player.REPEAT_MODE_OFF) {
            val repeat =
                if (player.repeatMode == Player.REPEAT_MODE_ONE) SpinbackIcons.RepeatOne else SpinbackIcons.Repeat
            Icon(repeat, stringResource(R.string.repeat), Modifier.size(16.dp), tint = LcdText)
        }
        if (player.shuffle) {
            Icon(
                SpinbackIcons.Shuffle,
                stringResource(R.string.shuffle),
                Modifier.size(16.dp),
                tint = LcdText
            )
        }
    }
}

/** Title, artist and album beside the cover, with the song's place in the queue below. */
@Composable
private fun SongInfo(track: Track, player: PlayerConnection, artSize: Dp, titleSize: TextUnit) {
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
        DetailText(track.artist)
        DetailText(track.album)
        // Where the star rating would go.
        Spacer(Modifier.height(22.dp))
        DetailText("${player.queueIndex + 1} / ${player.queueSize}")
    }
}

@Composable
private fun DetailText(text: String) {
    Text(
        text,
        color = SongDetail,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The progress bar with the elapsed and remaining times, or, while the wheel adjusts it, the
 * volume bar between two speakers. Both use the same row and fixed-width slots either side, so the
 * bar never moves when one replaces the other.
 */
@Composable
private fun BarRow(page: NowPlayingPage, position: Long, duration: Long, barHeight: Dp) {
    val volume = page.mode == NowPlayingMode.Volume
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = BAR_ROW_BOTTOM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(BAR_SIDE_SLOT), contentAlignment = Alignment.CenterEnd) {
            if (volume) {
                Icon(
                    SpinbackIcons.VolumeDown,
                    stringResource(R.string.volume),
                    Modifier.size(18.dp),
                    tint = LcdText
                )
            } else {
                TimeText(formatTime(position))
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
                TimeText("-" + formatTime((duration - position).coerceAtLeast(0)))
            }
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, color = LcdText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
