// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.app.KeyguardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AppLockSettings
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

/** The app lock as stored: on or off, and after how long away it asks again. */
@Immutable
internal data class LockState(val enabled: Boolean = false, val timeoutSeconds: Int = 0)

@HiltViewModel
internal class PrivacyViewModel @Inject constructor(private val lock: AppLockSettings) : ViewModel() {
    val state: StateFlow<LockState> = combine(lock.enabled, lock.timeoutSeconds, ::LockState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), LockState())

    fun onEnabled(enabled: Boolean) {
        viewModelScope.launch { lock.setEnabled(enabled) }
    }

    fun onTimeout(seconds: Int) {
        viewModelScope.launch { lock.setTimeoutSeconds(seconds) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

internal object PrivacySection : SettingsSection {
    override val key: String = "privacy"
    override val order: Int = 370
    override val title: Int = R.string.privacy_title
    override val icon: ImageVector = AlohaIcons.VisibilityPrivate

    @Composable
    override fun Content() {
        val viewModel: PrivacyViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        // a lock needs something to unlock it with: a screen lock, which a fingerprint or face may stand in for
        val secure = remember(context) { context.getSystemService<KeyguardManager>()?.isDeviceSecure == true }
        PrivacyContent(state, secure, viewModel::onEnabled, viewModel::onTimeout)
    }
}

@Composable
internal fun PrivacyContent(
    state: LockState,
    secure: Boolean,
    onEnabled: (Boolean) -> Unit,
    onTimeout: (Int) -> Unit,
) {
    Column {
        if (secure) {
            SwitchRow(
                stringResource(R.string.privacy_lock),
                state.enabled,
                onEnabled,
                stringResource(R.string.privacy_lock_summary),
            )
        } else {
            Text(
                stringResource(R.string.privacy_lock_needs_screen_lock),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
            )
        }
        if (secure && state.enabled) {
            HorizontalDivider()
            ChoiceRows(
                stringResource(R.string.privacy_lock_after),
                TIMEOUTS.map { seconds -> seconds to timeoutLabel(seconds) },
                state.timeoutSeconds,
                onTimeout,
            )
        }
    }
}

@Composable
private fun timeoutLabel(seconds: Int): String = when {
    seconds == 0 -> stringResource(R.string.privacy_lock_immediately)
    seconds < HOUR -> pluralStringResource(R.plurals.privacy_lock_minutes, seconds / MINUTE, seconds / MINUTE)
    else -> pluralStringResource(R.plurals.privacy_lock_hours, seconds / HOUR, seconds / HOUR)
}

private const val MINUTE = 60
private const val HOUR = 3_600
private val TIMEOUTS = listOf(0, MINUTE, 5 * MINUTE, 15 * MINUTE, HOUR)
