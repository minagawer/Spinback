package com.minagawer.spinback.ui.classic

import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minagawer.spinback.data.Library
import com.minagawer.spinback.playback.PlayerConnection
import kotlin.math.exp

/** How fast the edge shade fades toward the middle, as a fraction of the half-width. */
private const val EDGE_FALLOFF = 0.07f
private const val EDGE_FALLOFF_VERTICAL = 0.025f
private const val EDGE_STRENGTH_VERTICAL = 0.6f
private const val EDGE_STEPS = 32

@Composable
fun ClassicPlayer(library: Library, player: PlayerConnection) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val navigator = remember(library) { Navigator(library, player) }
    val clicker = remember { Clicker(context) }
    DisposableEffect(clicker) { onDispose { clicker.release() } }
    val colors = bodyColors(isSystemInDarkTheme())
    var wheelHeld by remember { mutableStateOf(false) }

    BackHandler(enabled = navigator.canGoBack) { navigator.back() }
    // Leave "Now Playing" once the queue is gone.
    LaunchedEffect(player.currentTrackId, navigator.current) {
        if (player.currentTrackId == null && navigator.current is NowPlayingPage) navigator.back()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.body)
            .drawBehind {
                // From the center out to each edge of the screen: barely changing at first, then
                // darkening quickly near the edge.
                // A smooth exponential falloff sampled finely, so no kinks show as lines.
                fun stops(falloff: Float, strength: Float) = Array(EDGE_STEPS + 1) { i ->
                    val t = i / EDGE_STEPS.toFloat()
                    val fall = (exp(-t / falloff) - exp(-1 / falloff)) / (1 - exp(-1 / falloff))
                    t to colors.bodyEdge.copy(alpha = colors.bodyEdge.alpha * strength * fall)
                }

                val sides = stops(EDGE_FALLOFF, 1f)
                // The top and bottom shade hugs the edge more tightly and stays fainter.
                val ends = stops(EDGE_FALLOFF_VERTICAL, EDGE_STRENGTH_VERTICAL)
                val halfW = size.width / 2
                val halfH = size.height / 2
                drawRect(
                    Brush.horizontalGradient(*sides, startX = 0f, endX = halfW),
                    size = Size(halfW, size.height)
                )
                drawRect(
                    Brush.horizontalGradient(*sides, startX = size.width, endX = halfW),
                    Offset(halfW, 0f),
                    Size(halfW, size.height),
                )
                drawRect(
                    Brush.verticalGradient(*ends, startY = 0f, endY = halfH),
                    size = Size(size.width, halfH)
                )
                drawRect(
                    Brush.verticalGradient(*ends, startY = size.height, endY = halfH),
                    Offset(0f, halfH),
                    Size(size.width, halfH),
                )
            }
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClassicScreen(
            navigator.current,
            library,
            player,
            wheelHeld,
            colors.wheelEdge,
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ClickWheel(
                colors,
                onRotate = { navigator.rotate(it, audio) },
                onMenu = navigator::back,
                onMenuHold = navigator::home,
                onSelect = navigator::select,
                onPlayPause = player::togglePlay,
                onPrevious = { player.previous() },
                onNext = { player.next() },
                onScan = player::scan,
                onScanEnd = player::endScan,
                onClick = clicker::click,
                onTouch = { wheelHeld = it },
                modifier = Modifier.size(minOf(maxWidth * 0.82f, maxHeight * 0.92f)),
            )
        }
    }
}
