package com.minagawer.spinback.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.minagawer.spinback.R
import com.minagawer.spinback.data.Library
import com.minagawer.spinback.playback.PlayerConnection
import com.minagawer.spinback.ui.classic.ClassicPlayer
import com.minagawer.spinback.ui.classic.bodyColors
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
                ?: return@withContext LoadState.Failed(context.getString(R.string.storage_unavailable))
            runCatching {
                Library.load(
                    dir,
                    context.getString(R.string.unknown_artist),
                    context.getString(R.string.unknown_album)
                )
            }.fold(
                onSuccess = { it?.let(LoadState::Loaded) ?: LoadState.Missing(dir.absolutePath) },
                onFailure = { LoadState.Failed(it.message ?: it.toString()) },
            )
        }
    }

    when (val s = state) {
        LoadState.Loading -> {
            // On the case color like the splash screen before it, spinning in the wheel's color.
            val colors = bodyColors(isSystemInDarkTheme())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.body),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.wheel)
            }
        }

        is LoadState.Missing -> Message(
            stringResource(R.string.library_missing),
            stringResource(R.string.library_missing_detail, s.dir),
        )

        is LoadState.Failed -> Message(stringResource(R.string.library_failed), s.message)
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
