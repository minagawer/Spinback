package com.minagawer.spinback.ui.classic

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Colors shared by every page on the LCD.
internal val LcdBackground = Color.White
internal val LcdText = Color(0xFF111111)
internal val LcdSubtext = Color(0xFF6B6E73)
internal val LcdLine = Color(0xFF9EA2A8)
internal val Highlight =
    Brush.verticalGradient(listOf(Color(0xFF8CC8F7), Color(0xFF4495E3), Color(0xFF2B79D3)))
