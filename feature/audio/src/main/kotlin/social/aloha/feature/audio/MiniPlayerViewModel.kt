// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.audio

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
internal class MiniPlayerViewModel @Inject constructor(private val playback: AudioPlayback) : ViewModel() {
    val nowPlaying: StateFlow<NowPlaying?> get() = playback.nowPlaying

    fun onToggle() = playback.toggle()

    fun onStop() = playback.stop()
}
