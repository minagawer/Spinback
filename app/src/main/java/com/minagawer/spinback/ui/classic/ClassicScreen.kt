package com.minagawer.spinback.ui.classic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.minagawer.spinback.data.Library
import com.minagawer.spinback.playback.PlayerConnection

private val LcdShadow = Color.Black.copy(alpha = 0.25f)
private val LCD_SHADOW_DEPTH = 4.dp

/** The 4:3 LCD: a title bar over the current page. */
@Composable
fun ClassicScreen(
    page: Page,
    library: Library,
    player: PlayerConnection,
    wheelHeld: Boolean,
    /** The hairline around the screen, the same as the wheel's. */
    edge: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)
    // On the main menu the cover takes the whole right half, title bar and all.
    val split = page is MenuPage && page.showArtwork
    Row(
        modifier
            .clip(shape)
            .background(LcdBackground)
            // A thin shadow right at the edge all round, so the screen sits a little below the case.
            .drawWithContent {
                drawContent()
                val depth = LCD_SHADOW_DEPTH.toPx()
                // Dark at the very edge, dropping off fast so it stays near the rim.
                val fade = listOf(
                    LcdShadow,
                    LcdShadow.copy(alpha = LcdShadow.alpha * 0.3f),
                    Color.Transparent
                )
                drawRect(Brush.horizontalGradient(fade, startX = 0f, endX = depth))
                drawRect(
                    Brush.horizontalGradient(
                        fade,
                        startX = size.width,
                        endX = size.width - depth
                    )
                )
                drawRect(Brush.verticalGradient(fade, startY = 0f, endY = depth))
                drawRect(
                    Brush.verticalGradient(
                        fade,
                        startY = size.height,
                        endY = size.height - depth
                    )
                )
            }
            .border(1.dp, edge, shape),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            TitleBar(page.title, player)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (page) {
                    is MenuPage -> MenuScreen(page, player, wheelHeld)
                    is NowPlayingPage -> NowPlayingScreen(page, library, player)
                }
            }
        }
        if (split) {
            ArtworkPane(
                library,
                player,
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}
