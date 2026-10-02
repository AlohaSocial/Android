// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import social.aloha.core.database.AccountOrderDao

/** The order the reader keeps their accounts in, which the switcher and the launcher follow. */
public class AccountOrder @Inject constructor(private val dao: AccountOrderDao) {
    /**
     * The accounts in the order of [ids], first to last. One write per account: a run cut short leaves
     * an order half changed, never an account lost.
     */
    public suspend fun reorder(ids: List<String>) {
        ids.forEachIndexed { index, id -> dao.setSortIndex(id, index) }
    }
}
