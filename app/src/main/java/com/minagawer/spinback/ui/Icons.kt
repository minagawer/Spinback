package com.minagawer.spinback.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** A few Material Symbols paths and the click wheel's marks, so we don't need the icons-extended artifact. */
object SpinbackIcons {
    // The click wheel's own marks: wide and flat, two triangles to a skip.
    val SkipBack = wide("SkipBack", 22f, "M0,0h2v12h-2z M12,0L2,6 12,12z M22,0L12,6 22,12z")
    val SkipForward = wide("SkipForward", 22f, "M0,0L10,6 0,12z M10,0L20,6 10,12z M20,0h2v12h-2z")
    val PlayPause = wide("PlayPause", 23f, "M0,0L10,6 0,12z M14,0h3v12h-3z M20,0h3v12h-3z")
    val Shuffle = icon(
        "Shuffle",
        "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5z" +
                "m0.33,9.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z",
    )
    val Repeat =
        icon("Repeat", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zm10,10H7v-3l-4,4 4,4v-3h12v-6h-2v4z")
    val RepeatOne = icon(
        "RepeatOne",
        "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zm10,10H7v-3l-4,4 4,4v-3h12v-6h-2v4zm-4,-2V9h-1l-2,1v1h1.5v4H13z",
    )

    /** A heavier ">" than Material's, like the iPod's menu arrow. */
    val Chevron = icon("Chevron", "M8.4,4.8L16,12 8.4,19.2 6.2,17 11.6,12 6.2,7z")
    val VolumeDown = icon(
        "VolumeDown",
        "M18.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM5,9v6h4l5,5V4L9,9H5z",
    )
    val VolumeUp = icon(
        "VolumeUp",
        "M3,9v6h4l5,5V4L7,9H3zm13.5,3c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02z" +
                "M14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
    )

    /** An icon 12 units tall and [width] wide. */
    private fun wide(name: String, width: Float, path: String): ImageVector =
        ImageVector.Builder(name, width.dp, 12.dp, width, 12f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
