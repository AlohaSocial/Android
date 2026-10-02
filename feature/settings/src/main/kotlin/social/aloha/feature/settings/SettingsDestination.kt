// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A screen of its own that Settings lists among its sections, such as Filters: the app knows where it
 * leads, Settings only where it sits ([order], as a section's) and what it is called.
 */
public class SettingsDestination(
    public val key: String,
    public val order: Int,
    @param:StringRes public val title: Int,
    public val icon: ImageVector,
    public val onOpen: () -> Unit,
)
