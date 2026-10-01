// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.navigation3.runtime.NavKey
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.data.RemoteLookup
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.AccountKey
import social.aloha.core.navigation.LinkTarget
import social.aloha.core.navigation.RouteResolver
import social.aloha.core.navigation.TagKey
import social.aloha.core.navigation.ThreadKey

/**
 * Where a link opens: in the app when it points at a post, a profile or a hashtag, else the browser
 * (null). A post on the reader's own server opens by its id there; one elsewhere is looked up through
 * the reader's server. Only addresses shaped like those (see [RouteResolver]) are ever looked up, so
 * an ordinary link is never sent to the server.
 */
class LinkOpener @Inject constructor(private val lookup: RemoteLookup) {
    /**
     * @param fromPost the link sits in a post's text. A post's own mentions and hashtags are already
     * known as such, so a profile- or hashtag-shaped web link there is some site's page, not the
     * fediverse's, and the browser takes it; only a post-shaped one opens here.
     * @param handedOver the reader chose "Open in Aloha" for it, so an address of any shape is asked
     * after as a post: they asked for exactly that.
     */
    suspend fun destination(
        reader: SignedInAccount,
        address: String,
        fromPost: Boolean = false,
        handedOver: Boolean = false,
    ): NavKey? = when (val target = RouteResolver.parse(address)) {
        is LinkTarget.Post -> post(reader, target)?.let { ThreadKey(reader.id, it) }

        is LinkTarget.Profile -> AccountKey(reader.id, acct = target.acct).takeUnless { fromPost }

        is LinkTarget.Tag -> TagKey(reader.id, target.name).takeUnless { fromPost }

        LinkTarget.Web -> RouteResolver.browsable(address)?.takeIf { handedOver }?.let { lookup.post(reader, it) }
            ?.let { ThreadKey(reader.id, it) }
    }

    private suspend fun post(reader: SignedInAccount, target: LinkTarget.Post): String? {
        val home = reader.apiBase.toHttpUrlOrNull()?.host?.lowercase()
        val local = target.localId?.takeIf { target.host == home || target.host == reader.host.lowercase() }
        return local ?: lookup.post(reader, target.url)
    }
}
