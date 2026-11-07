package com.minagawer.spinback.ui.classic

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.minagawer.spinback.data.Library
import com.minagawer.spinback.data.Track
import com.minagawer.spinback.playback.PlayerConnection
import com.minagawer.spinback.ui.Artwork
import kotlinx.coroutines.delay
import kotlin.random.Random

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
internal fun ArtworkPane(
    library: Library,
    player: PlayerConnection,
    modifier: Modifier = Modifier
) {
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
