// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.profile

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.database.AccountDao
import social.aloha.core.model.Account
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.endpoints.CredentialEndpoints
import social.aloha.core.network.endpoints.CredentialsUpdate

/**
 * The reader's own profile as they edit it: loaded with its source (the note as written, the
 * posting defaults), saved with only what changed, pictures removed where asked. What the server
 * answers with becomes the stored account, so the switcher shows the new name and picture.
 */
@Singleton
public class OwnProfile @Inject constructor(private val clients: ClientFactory, private val accounts: AccountDao) {
    public suspend fun load(reader: SignedInAccount): Answer<Account> =
        clients.answer(reader, AccountEndpoints.verifyCredentials())

    /**
     * Removes the avatar and the header where asked, then saves [changes]; the account as the server
     * last answered, or the first refusal, which stops the rest.
     */
    public suspend fun save(
        reader: SignedInAccount,
        changes: CredentialsUpdate,
        removeAvatar: Boolean,
        removeHeader: Boolean,
    ): Answer<Account> {
        val steps = listOfNotNull(
            CredentialEndpoints.deleteAvatar().takeIf { removeAvatar },
            CredentialEndpoints.deleteHeader().takeIf { removeHeader },
            CredentialEndpoints.update(changes).takeIf { !changes.isEmpty },
        )
        val saved = run(reader, steps) ?: load(reader)
        // the stored account is what the switcher and the shell draw, so they follow at once
        (saved as? Answer.Got)?.value?.let { accounts.setProfile(reader.id, it.displayName, it.avatar, it.header) }
        return saved
    }

    /** Makes [steps] in turn; the last answer, or the first refusal, which stops the rest; null for none. */
    private suspend fun run(reader: SignedInAccount, steps: List<ApiRequest<Account>>): Answer<Account>? {
        var last: Answer<Account>? = null
        for (step in steps) {
            last = clients.answer(reader, step)
            if (last is Answer.Missed) break
        }
        return last
    }
}
