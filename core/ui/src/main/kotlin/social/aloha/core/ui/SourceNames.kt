// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.annotation.StringRes
import social.aloha.core.model.TimelineSource

/** What a mode calls the timeline it reads from, on its tabs or chips: the people followed, the server, all. */
@StringRes
public fun sourceName(source: TimelineSource): Int = when (source) {
    TimelineSource.Local -> R.string.source_local
    TimelineSource.Federated -> R.string.source_federated
    else -> R.string.source_home
}
