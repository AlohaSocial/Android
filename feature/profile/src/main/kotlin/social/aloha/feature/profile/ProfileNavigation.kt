// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import social.aloha.core.navigation.PeopleKind
import social.aloha.core.ui.StatusNavigation

/** Where a profile sends the person, beyond where any post does: back, and to followers or following. */
public interface ProfileNavigation : StatusNavigation {
    public fun openPeople(accountId: String, kind: PeopleKind)

    public fun back()
}
