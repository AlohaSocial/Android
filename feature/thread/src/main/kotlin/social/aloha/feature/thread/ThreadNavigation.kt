// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import social.aloha.core.navigation.StatusListKind
import social.aloha.core.ui.StatusNavigation

/** Where a thread sends the person, beyond where any post does: back, and to one of a post's lists. */
public interface ThreadNavigation : StatusNavigation {
    public fun openList(statusId: String, kind: StatusListKind)

    public fun back()
}
