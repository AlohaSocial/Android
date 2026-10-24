// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import social.aloha.core.navigation.PeopleKind
import social.aloha.core.ui.StatusNavigation

/**
 * Where a profile sends the person, beyond where any post does: back, to followers or following, and to
 * the account's posts with a hashtag.
 */
public interface ProfileNavigation : StatusNavigation {
    public fun openPeople(accountId: String, kind: PeopleKind)

    public fun back()

    /** Account [accountId]'s posts with hashtag [name], the account known as [handle]. */
    public fun openAccountTag(name: String, accountId: String, handle: String)

    /** The reader's own profile, to edit. */
    public fun editProfile()
}
