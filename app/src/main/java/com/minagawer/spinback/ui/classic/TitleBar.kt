package com.minagawer.spinback.ui.classic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.minagawer.spinback.playback.PlayerConnection

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

@Composable
internal fun TitleBar(title: String, player: PlayerConnection) {
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
