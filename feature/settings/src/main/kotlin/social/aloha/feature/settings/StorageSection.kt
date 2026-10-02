// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import social.aloha.core.data.DeviceStorage
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.ui.SettingsSection

/** How much the caches take; null while measured. [cleared] after the reader emptied them. */
@Immutable
internal data class StorageState(val bytes: Long? = null, val clearing: Boolean = false, val cleared: Boolean = false)

@HiltViewModel
internal class StorageViewModel @Inject constructor(private val storage: DeviceStorage) : ViewModel() {
    private val current = MutableStateFlow(StorageState())
    val state: StateFlow<StorageState> = current.asStateFlow()

    init {
        viewModelScope.launch { current.value = StorageState(storage.cacheBytes()) }
    }

    fun clear() {
        if (current.value.clearing) return
        current.value = current.value.copy(clearing = true)
        viewModelScope.launch {
            storage.clear()
            current.value = StorageState(storage.cacheBytes(), cleared = true)
        }
    }
}

internal object StorageSection : SettingsSection {
    override val key: String = "storage"
    override val order: Int = 800
    override val title: Int = R.string.storage_title
    override val icon: ImageVector = AlohaIcons.Archived

    @Composable
    override fun Content() {
        val viewModel: StorageViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        StorageContent(state, viewModel::clear)
    }
}

/** The caches' size and the way to empty them; nothing the reader made is in them. */
@Composable
internal fun StorageContent(state: StorageState, onClear: () -> Unit) {
    val context = LocalContext.current
    val size = state.bytes?.let { Formatter.formatShortFileSize(context, it) }
    Column {
        ListItem(
            headlineContent = { Text(stringResource(R.string.storage_cache)) },
            supportingContent = {
                Text(
                    when {
                        state.cleared && size != null -> stringResource(R.string.storage_cleared, size)
                        size != null -> stringResource(R.string.storage_cache_size, size)
                        else -> stringResource(R.string.storage_measuring)
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            },
            trailingContent = {
                TextButton(onClick = onClear, enabled = state.bytes != null && !state.clearing) {
                    Text(stringResource(R.string.storage_clear))
                }
            },
        )
    }
}
