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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minagawer.spinback.R
import com.minagawer.spinback.playback.SCAN_INTERVAL_MS
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
                    val zone =
                        zoneOf(down.position - center, size.width / 2f) ?: return@awaitEachGesture
                    val scanForward = when (zone) {
                        WheelZone.Next -> true
                        WheelZone.Previous -> false
                        else -> null
                    }
                    val rotation = Rotation(angleOf(down.position - center), down.uptimeMillis)

                    var held = false
                    var nextTickAt =
                        down.uptimeMillis + if (zone == WheelZone.Menu) MENU_HOLD_MS else HOLD_MS
                    while (true) {
                        // A still finger gets no events, so wake up on a timer to notice a hold.
                        val holdable = scanForward != null || (zone == WheelZone.Menu && !held)
                        val event = if (holdable && rotation.travelled < HOLD_SLOP_DEGREES) {
                            val wait = (nextTickAt - SystemClock.uptimeMillis()).coerceAtLeast(0L)
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
                                nextTickAt += SCAN_INTERVAL_MS
                            }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (zone == WheelZone.Center || held) continue
                        val steps =
                            rotation.advance(angleOf(change.position - center), change.uptimeMillis)
                        change.consume()
                        if (steps != 0 && rotate(steps)) {
                            click()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }

                    if (held && scanForward != null) scanEnd()
                    if (!held && rotation.travelled < TAP_SLOP_DEGREES) {
                        click()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        when (zone) {
                            WheelZone.Center -> select()
                            WheelZone.Next -> next()
                            WheelZone.Previous -> previous()
                            WheelZone.Menu -> menu()
                            WheelZone.PlayPause -> playPause()
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
            stringResource(R.string.next_song),
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = labelInset)
                .size(width = markHeight * 22 / 12, height = markHeight),
            tint = colors.label,
        )
        Icon(
            SpinbackIcons.SkipBack,
            stringResource(R.string.previous_song),
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = labelInset)
                .size(width = markHeight * 22 / 12, height = markHeight),
            tint = colors.label,
        )
        Icon(
            SpinbackIcons.PlayPause,
            stringResource(R.string.play_pause),
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

/** Where a touch lands: the center button, or one of the four labels around the ring. */
internal enum class WheelZone { Center, Menu, Next, PlayPause, Previous }

/** The zone at [offset] from the center of a wheel of [radius], or null off the wheel. */
internal fun zoneOf(offset: Offset, radius: Float): WheelZone? {
    val distance = offset.getDistance()
    if (distance > radius) return null
    if (distance < radius * CENTER_BUTTON_RATIO) return WheelZone.Center
    val angle = angleOf(offset)
    return when {
        angle in -45f..45f -> WheelZone.Next
        angle in 135f..180f || angle in -180f..-135f -> WheelZone.Previous
        angle in -135f..-45f -> WheelZone.Menu
        else -> WheelZone.PlayPause
    }
}

/** Turns a finger going round the wheel into menu steps; spinning fast jumps further. */
internal class Rotation(private var lastAngle: Float, private var lastTime: Long) {
    private var pending = 0f

    /** Degrees turned so far, either way. */
    var travelled = 0f
        private set

    /** Feeds the finger's latest angle and returns how many steps to move (0 until a full step). */
    fun advance(angle: Float, time: Long): Int {
        val delta = (angle - lastAngle + 540f) % 360f - 180f
        val speed = abs(delta) / (time - lastTime).coerceAtLeast(1L)
        lastAngle = angle
        lastTime = time
        travelled += abs(delta)
        pending += delta
        val steps = (pending / STEP_DEGREES).toInt()
        if (steps == 0) return 0
        pending -= steps * STEP_DEGREES
        // Spinning fast jumps further, so long song lists stay usable.
        val boost = when {
            speed > 1.2f -> 6
            speed > 0.6f -> 2
            else -> 1
        }
        return steps * boost
    }
}

internal fun angleOf(offset: Offset): Float =
    Math.toDegrees(atan2(offset.y, offset.x).toDouble()).toFloat()
