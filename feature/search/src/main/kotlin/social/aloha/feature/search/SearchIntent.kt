// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import social.aloha.core.data.search.Searches
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing

/** What a query could mean, offered above the results while it is typed. */
internal sealed interface SearchIntent {
    data class OpenUrl(val url: String) : SearchIntent

    data class Tag(val name: String) : SearchIntent

    data class Person(val handle: String) : SearchIntent

    data class Posts(val query: String) : SearchIntent

    data class Accounts(val query: String) : SearchIntent
}

/**
 * The intents [query] reads as: an address opens, a handle goes to its person, a hashtag or a single word
 * opens the posts tagged so, and anything else searches posts or accounts for it.
 */
internal fun intentsFor(query: String): List<SearchIntent> {
    val typed = query.trim()
    val tag = TAG.matchEntire(typed.removePrefix("#"))?.value?.let(SearchIntent::Tag)
    return when {
        typed.isEmpty() -> emptyList()

        Searches.isAddress(typed) -> listOf(SearchIntent.OpenUrl(typed))

        Searches.isHandle(typed) || LOCAL_HANDLE.matches(typed) ->
            listOf(SearchIntent.Person("@" + typed.removePrefix("@")), SearchIntent.Accounts(typed))

        typed.startsWith('#') -> listOfNotNull(tag, SearchIntent.Posts(typed))

        else -> listOfNotNull(tag, SearchIntent.Posts(typed), SearchIntent.Accounts(typed))
    }
}

private val LOCAL_HANDLE = Regex("""^@[\w.-]+$""")
private val TAG = Regex("""[\p{L}\p{N}_]*[\p{L}_][\p{L}\p{N}_]*""")

/** The intents as one rounded group of rows above the results. */
@Composable
internal fun Intents(intents: List<SearchIntent>, onIntent: (SearchIntent) -> Unit) {
    if (intents.isEmpty()) return
    Column(Modifier.padding(horizontal = AlohaSpacing.s, vertical = AlohaSpacing.xs)) {
        intents.forEachIndexed { index, intent ->
            Grouped(index, intents.size) {
                val (icon, label) = look(intent)
                ListItem(
                    leadingContent = { Icon(icon, contentDescription = null) },
                    headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { onIntent(intent) },
                )
            }
        }
    }
}

@Composable
private fun look(intent: SearchIntent): Pair<ImageVector, String> = when (intent) {
    is SearchIntent.OpenUrl -> AlohaIcons.NewWindow to stringResource(R.string.search_intent_open, intent.url)
    is SearchIntent.Tag -> AlohaIcons.Hashtag to stringResource(R.string.search_intent_tag, intent.name)
    is SearchIntent.Person -> AlohaIcons.Profile to stringResource(R.string.search_intent_person, intent.handle)
    is SearchIntent.Posts -> AlohaIcons.Search to stringResource(R.string.search_intent_posts, intent.query)
    is SearchIntent.Accounts -> AlohaIcons.Search to stringResource(R.string.search_intent_accounts, intent.query)
}
