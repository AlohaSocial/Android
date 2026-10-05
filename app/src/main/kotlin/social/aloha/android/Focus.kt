// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.FocusMode

/** Focus mode as the account sheet switches it. */
@HiltViewModel
internal class FocusViewModel @Inject constructor(private val focus: FocusMode) : ViewModel() {
    val on: StateFlow<Boolean> = focus.on.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), false)

    fun onFocus(on: Boolean) {
        viewModelScope.launch { focus.set(on) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

/** Focus mode from Quick Settings: the tile is on while it is, and a tap turns it the other way. */
@AndroidEntryPoint
class FocusTileService : TileService() {
    @Inject lateinit var focus: FocusMode

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    override fun onStartListening() {
        watching = scope.launch { focus.on.collect(::show) }
    }

    override fun onStopListening() {
        watching?.cancel()
    }

    override fun onClick() {
        val on = qsTile?.state == Tile.STATE_ACTIVE
        scope.launch { focus.set(!on) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun show(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
