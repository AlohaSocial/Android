// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import social.aloha.core.model.SensitiveMediaPolicy

/**
 * How media marked sensitive shows for the account in use, as its server keeps the choice; covered
 * where nothing provides it.
 */
public val LocalSensitiveMediaPolicy: ProvidableCompositionLocal<SensitiveMediaPolicy> =
    staticCompositionLocalOf { SensitiveMediaPolicy.Blur }
