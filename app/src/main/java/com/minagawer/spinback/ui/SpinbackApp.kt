package com.minagawer.spinback.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minagawer.spinback.data.Library
import com.minagawer.spinback.playback.PlayerConnection
import com.minagawer.spinback.ui.classic.ClassicPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface LoadState {
    data object Loading : LoadState
    data class Missing(val dir: String) : LoadState
    data class Failed(val message: String) : LoadState
    data class Loaded(val library: Library) : LoadState
}

@Composable
fun SpinbackApp(player: PlayerConnection) {
    val context = LocalContext.current
    val state by produceState<LoadState>(LoadState.Loading) {
        value = withContext(Dispatchers.IO) {
            val dir = context.getExternalFilesDir(null)
                ?: return@withContext LoadState.Failed("ストレージにアクセスできません")
            runCatching { Library.load(dir) }.fold(
                onSuccess = { it?.let(LoadState::Loaded) ?: LoadState.Missing(dir.absolutePath) },
                onFailure = { LoadState.Failed(it.message ?: it.toString()) },
            )
        }
    }

    when (val s = state) {
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is LoadState.Missing -> Message(
            "ライブラリがまだありません",
            "Mac で tools/push_to_device.sh を実行して、音楽を転送してください。\n\n転送先: ${s.dir}",
        )

        is LoadState.Failed -> Message("ライブラリを読み込めませんでした", s.message)
        is LoadState.Loaded -> ClassicPlayer(s.library, player)
    }
}

@Composable
private fun Message(title: String, body: String) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(12.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
