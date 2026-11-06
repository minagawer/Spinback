package com.minagawer.spinback

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.minagawer.spinback.playback.PlayerConnection
import com.minagawer.spinback.ui.SpinbackApp
import com.minagawer.spinback.ui.theme.SpinbackTheme

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = PlayerConnection(applicationContext)
        enableEdgeToEdge()
        setContent {
            SpinbackTheme {
                SpinbackApp(player)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player.connect()
    }

    override fun onStop() {
        player.release()
        super.onStop()
    }
}
