package com.minagawer.spinback.ui.classic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Shades every edge inward: [sides] from the left and right edges over [sideDepth], and [ends]
 * from the top and bottom over [endDepth]. Each runs from the edge (0) to its depth (1).
 */
internal fun DrawScope.drawEdgeShade(
    sides: Array<Pair<Float, Color>>,
    sideDepth: Float,
    ends: Array<Pair<Float, Color>> = sides,
    endDepth: Float = sideDepth,
) {
    drawRect(
        Brush.horizontalGradient(*sides, startX = 0f, endX = sideDepth),
        size = Size(sideDepth, size.height)
    )
    drawRect(
        Brush.horizontalGradient(*sides, startX = size.width, endX = size.width - sideDepth),
        Offset(size.width - sideDepth, 0f),
        Size(sideDepth, size.height),
    )
    drawRect(
        Brush.verticalGradient(*ends, startY = 0f, endY = endDepth),
        size = Size(size.width, endDepth)
    )
    drawRect(
        Brush.verticalGradient(*ends, startY = size.height, endY = size.height - endDepth),
        Offset(0f, size.height - endDepth),
        Size(size.width, endDepth),
    )
}
