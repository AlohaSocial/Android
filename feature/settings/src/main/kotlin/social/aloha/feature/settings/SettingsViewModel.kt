// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import social.aloha.core.ui.SettingsSection

/** The sections every feature registered, in their order. */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(sections: Set<@JvmSuppressWildcards SettingsSection>) :
    ViewModel() {
    val sections: List<SettingsSection> = sections.sortedWith(compareBy({ it.order }, { it.key }))

    fun section(key: String): SettingsSection? = sections.firstOrNull { it.key == key }
}
