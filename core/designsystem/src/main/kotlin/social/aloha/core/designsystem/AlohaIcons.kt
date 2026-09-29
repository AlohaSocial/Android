// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AmpStories
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AmpStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The only way features reach an icon. Outlined for the resting state, filled
 * for the selected one, so state is carried by shape as well as colour.
 *
 * ponytail: backed by the frozen Material Icons set for now; swap the bodies for
 * Material Symbols vectors (Apache-2.0) without touching a caller.
 */
public object AlohaIcons {
    public val Home: ImageVector = Icons.Outlined.Home
    public val HomeSelected: ImageVector = Icons.Filled.Home
    public val Photos: ImageVector = Icons.Outlined.PhotoLibrary
    public val PhotosSelected: ImageVector = Icons.Filled.PhotoLibrary
    public val Video: ImageVector = Icons.Outlined.VideoLibrary
    public val VideoSelected: ImageVector = Icons.Filled.VideoLibrary

    // stacked portrait cards, as on Apple; Slideshow looks the same filled and outlined
    public val Shorts: ImageVector = Icons.Outlined.AmpStories
    public val ShortsSelected: ImageVector = Icons.Filled.AmpStories
    public val Notifications: ImageVector = Icons.Outlined.Notifications
    public val NotificationsSelected: ImageVector = Icons.Filled.Notifications
    public val Profile: ImageVector = Icons.Outlined.AccountCircle
    public val ProfileSelected: ImageVector = Icons.Filled.AccountCircle
}
