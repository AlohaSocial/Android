// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import social.aloha.core.model.SignedInAccount

/** Told when a poll finds that an account's unread count moved, before the poll counts as done. */
public fun interface PollListener {
    /**
     * Acts on [count]; false when it could not finish, the page it needed failed or it had to wait, and
     * wants to be told again on the next poll even if the count stays the same.
     */
    public suspend fun onUnreadChanged(account: SignedInAccount, count: Int): Boolean
}
