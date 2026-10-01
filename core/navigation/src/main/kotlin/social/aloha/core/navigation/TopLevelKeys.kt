// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The top-level destinations the navigation suite shows (the modes of the Apple
 * app's tab bar). Keys are serialisable so the back stack survives process death.
 */
@Serializable
public sealed interface TopLevelKey : NavKey

@Serializable
public data object HomeKey : TopLevelKey

@Serializable
public data object PhotosKey : TopLevelKey

@Serializable
public data object VideoKey : TopLevelKey

@Serializable
public data object ShortsKey : TopLevelKey

@Serializable
public data object NewsKey : TopLevelKey

@Serializable
public data object AudioKey : TopLevelKey

@Serializable
public data object NotificationsKey : TopLevelKey

@Serializable
public data object ProfileKey : TopLevelKey
