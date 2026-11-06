package com.minagawer.spinback.ui.classic

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minagawer.spinback.ui.SpinbackIcons
import kotlin.math.abs
import kotlin.math.atan2

/** One menu step per this much rotation, like the original's ~24 detents per turn. */
private const val STEP_DEGREES = 15f
private const val TAP_SLOP_DEGREES = 10f

/**
 * A hold needs the thumb to stay put. Any more drift than this and the touch is a turn, even a slow
 * one, so starting to scroll on ⏭ / ⏮ never turns into scanning.
 */
private const val HOLD_SLOP_DEGREES = 3f
private const val CENTER_BUTTON_RATIO = 0.38f

/** Holding ⏭ / ⏮ this long starts scanning through the song. */
private const val HOLD_MS = 500L

/** Holding MENU this long goes to the top menu; firmer, so a slow tap doesn't jump all the way home. */
private const val MENU_HOLD_MS = 1_000L
private const val SCAN_INTERVAL_MS = 200L

data class BodyColors(
    val body: Brush,
    /** Shade the case darkens to at the edges of the screen. */
    val bodyEdge: Color,
    val wheel: Brush,
    val wheelEdge: Color,
    val label: Color,
    val button: Brush,
    val buttonEdge: Color,
)

// The black and silver iPod classic finishes: a case lighter at the top and darker below, a flat
// matte wheel, and a dished center button in the case's own color.
fun bodyColors(dark: Boolean) = if (dark) {
    BodyColors(
        // A slightly violet mid grey, lighter at the top and falling away to near black below.
        body = Brush.verticalGradient(
            0f to Color(0xFF8A868C),
            0.15f to Color(0xFF7A767C),
            0.5f to Color(0xFF747076),
            0.75f to Color(0xFF4E4D52),
            1f to Color(0xFF2A2A2A),
        ),
        bodyEdge = Color.Black.copy(alpha = 0.45f),
        // A flat, matte dark wheel.
        wheel = SolidColor(Color(0xFF313131)),
        wheelEdge = Color(0xFF1A1A1A),
        label = Color.White,
        // A dished button: dark under its upper rim, catching light toward the bottom.
        button = Brush.verticalGradient(
            0f to Color(0xFF3B3A3F),
            0.5f to Color(0xFF57565B),
            1f to Color(0xFF727176),
        ),
        buttonEdge = Color(0xFF1A1A1A),
    )
} else {
    BodyColors(
        // Pale aluminium, brightest along the top and greying toward the bottom.
        body = Brush.verticalGradient(
            0f to Color(0xFFEDEDED),
            0.15f to Color(0xFFE6E5E7),
            0.5f to Color(0xFFE3E2E4),
            0.8f to Color(0xFFD2D2D2),
            1f to Color(0xFFB5B5B5),
        ),
        bodyEdge = Color.Black.copy(alpha = 0.18f),
        // A flat, faintly cool white wheel.
        wheel = SolidColor(Color(0xFFF9FBFD)),
        wheelEdge = Color(0xFFD0D1D3),
        label = Color(0xFFAFAFAF),
        // A dished button: shaded under its upper rim, lightening toward the bottom.
        button = Brush.verticalGradient(
            0f to Color(0xFFC0C1C2),
            0.5f to Color(0xFFD9D9DB),
            1f to Color(0xFFE6E6E8),
        ),
        buttonEdge = Color(0xFFC4C4C6),
    )
}

/**
 * Circular drag scrolls (clockwise = forward); a tap on the ring presses MENU / next / play-pause /
 * previous depending on where it lands, and a tap in the middle presses the center button.
 * Holding next / previous scans through the song, calling [onScan] until released; holding MENU
 * calls [onMenuHold].
 */
@Composable
fun ClickWheel(
    colors: BodyColors,
    /** Returns false when there was nowhere to go, which keeps the wheel from clicking. */
    onRotate: (steps: Int) -> Boolean,
    onMenu: () -> Unit,
    onMenuHold: () -> Unit,
    onSelect: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onScan: (forward: Boolean, heldMs: Long) -> Unit,
    onScanEnd: () -> Unit,
    onClick: () -> Unit,
    onTouch: (pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    // The gesture loop below outlives recompositions, so it must read the latest callbacks.
    val rotate by rememberUpdatedState(onRotate)
    val menu by rememberUpdatedState(onMenu)
    val menuHold by rememberUpdatedState(onMenuHold)
    val select by rememberUpdatedState(onSelect)
    val playPause by rememberUpdatedState(onPlayPause)
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val scan by rememberUpdatedState(onScan)
    val scanEnd by rememberUpdatedState(onScanEnd)
    val click by rememberUpdatedState(onClick)
    val touch by rememberUpdatedState(onTouch)

    BoxWithConstraints(
        modifier
            .clip(CircleShape)
            .background(colors.wheel)
            .border(0.5.dp, colors.wheelEdge, CircleShape)
            // Reports the thumb resting on the wheel, whatever the gesture below makes of it.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    touch(true)
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        touch(false)
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f
                    val start = down.position - center
                    if (start.getDistance() > radius) return@awaitEachGesture
                    val onButton = start.getDistance() < radius * CENTER_BUTTON_RATIO
                    val startAngle = angleOf(start)
                    val scanForward = when {
                        onButton -> null
                        startAngle in -45f..45f -> true
                        startAngle in 135f..180f || startAngle in -180f..-135f -> false
                        else -> null
                    }
                    val onMenuButton = !onButton && startAngle in -135f..-45f

                    var held = false
                    var nextScanAt = down.uptimeMillis + if (onMenuButton) MENU_HOLD_MS else HOLD_MS
                    var lastAngle = angleOf(start)
                    var lastTime = down.uptimeMillis
                    var pending = 0f
                    var travelled = 0f
                    while (true) {
                        // A still finger gets no events, so wake up on a timer to notice a hold.
                        val holdable = scanForward != null || (onMenuButton && !held)
                        val event = if (holdable && travelled < HOLD_SLOP_DEGREES) {
                            val wait = (nextScanAt - SystemClock.uptimeMillis()).coerceAtLeast(0L)
                            withTimeoutOrNull(wait) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            if (!held) {
                                held = true
                                click()
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                            if (scanForward == null) {
                                menuHold()
                            } else {
                                scan(
                                    scanForward,
                                    SystemClock.uptimeMillis() - down.uptimeMillis - HOLD_MS
                                )
                                nextScanAt += SCAN_INTERVAL_MS
                            }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (onButton || held) continue
                        val angle = angleOf(change.position - center)
                        val delta = (angle - lastAngle + 540f) % 360f - 180f
                        val speed = abs(delta) / (change.uptimeMillis - lastTime).coerceAtLeast(1L)
                        lastAngle = angle
                        lastTime = change.uptimeMillis
                        travelled += abs(delta)
                        pending += delta
                        change.consume()

                        val steps = (pending / STEP_DEGREES).toInt()
                        if (steps != 0) {
                            pending -= steps * STEP_DEGREES
                            // Spinning fast jumps further, so long song lists stay usable.
                            val boost = when {
                                speed > 1.2f -> 6
                                speed > 0.6f -> 2
                                else -> 1
                            }
                            if (rotate(steps * boost)) {
                                click()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }

                    if (held && scanForward != null) scanEnd()
                    if (!held && travelled < TAP_SLOP_DEGREES) {
                        click()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        when {
                            onButton -> select()
                            scanForward == true -> next()
                            scanForward == false -> previous()
                            onMenuButton -> menu()
                            else -> playPause()
                        }
                    }
                }
            },
    ) {
        val labelInset = maxWidth * 0.09f
        // The marks are 12 units tall; this is how tall that is on this wheel.
        val markHeight = maxWidth * 0.042f
        Text(
            "MENU",
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = labelInset),
            color = colors.label,
            fontWeight = FontWeight.Bold,
            letterSpacing = with(LocalDensity.current) { (maxWidth * 0.004f).toSp() },
            fontSize = with(LocalDensity.current) { (maxWidth * 0.06f).toSp() },
        )
        Icon(
            SpinbackIcons.SkipForward,
            "次の曲",
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = labelInset)
                .size(width = markHeight * 22 / 12, height = markHeight),
            tint = colors.label,
        )
        Icon(
            SpinbackIcons.SkipBack,
            "前の曲",
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = labelInset)
                .size(width = markHeight * 22 / 12, height = markHeight),
            tint = colors.label,
        )
        Icon(
            SpinbackIcons.PlayPause,
            "再生・一時停止",
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = labelInset)
                .size(width = markHeight * 23 / 12, height = markHeight),
            tint = colors.label,
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxSize(CENTER_BUTTON_RATIO)
                .clip(CircleShape)
                .background(colors.button)
                .border(0.5.dp, colors.buttonEdge, CircleShape),
        )
    }
}

private fun angleOf(offset: Offset): Float =
    Math.toDegrees(atan2(offset.y, offset.x).toDouble()).toFloat()
