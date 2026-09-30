// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.compose

import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.data.map
import social.aloha.core.data.timeline.RefreshPlan
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.Account
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.GifLibrary
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Preferences
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineKey
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.endpoints.ComposeEndpoints
import social.aloha.core.network.endpoints.GifEndpoints
import social.aloha.core.network.endpoints.InstanceEndpoints
import social.aloha.core.network.endpoints.MediaEndpoints
import social.aloha.core.network.endpoints.SearchEndpoints
import social.aloha.core.network.endpoints.StatusEndpoints
import social.aloha.core.network.endpoints.StatusPost

/**
 * What writing a post needs from the reader's server: posting, the post being answered, the
 * completions for `@`, `#` and `:`, and the reader's posting defaults. A post the server accepts is
 * stored at once, and the home timeline fetched what is new, so it is there when the writer is back.
 */
@Singleton
public class ComposeRepository @Inject constructor(
    private val clients: ClientFactory,
    private val statuses: StatusRepository,
    private val timelines: TimelineRepository,
    private val settings: AccountSettingsStore,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val emojiLock = Mutex()
    private val emojis = HashMap<String, Pair<Long, List<CustomEmoji>>>()

    /** Posts [post] as [reader]; the idempotency key it carries makes a retry after a timeout safe. */
    public suspend fun post(reader: SignedInAccount, post: StatusPost): Answer<Status> =
        clients.answer(reader, ComposeEndpoints.post(post)).also { answer ->
            if (answer is Answer.Got) statuses.save(reader.id, answer.value)
        }

    /**
     * Brings the home timeline up to date after [reader] posted, so the post is at its top. It runs
     * beyond the composer, which closes the moment posting ends; a home never loaded waits for its
     * first fetch instead.
     */
    public fun refreshHome(reader: SignedInAccount) {
        scope.launch {
            val home = TimelineKey.home()
            if (timelines.lastFetched(reader, home) == null) return@launch
            val newest = timelines.observe(reader, home).first().firstOrNull { it is TimelineRow.Post }?.id
            timelines.refresh(reader, home, RefreshPlan.of(true, newest))
        }
    }

    /** The post a reply answers: the stored copy where there is one, else the server's. */
    public suspend fun status(reader: SignedInAccount, id: String): Answer<Status> =
        statuses.get(reader.id, id)?.let { Answer.Got(it) } ?: clients.answer(reader, StatusEndpoints.status(id))

    /** Accounts for a `@` completion; `accounts/search`, the route a composer is meant to use. */
    public suspend fun accounts(reader: SignedInAccount, query: String): Answer<List<Account>> =
        clients.answer(reader, AccountEndpoints.search(query, limit = COMPLETIONS, resolve = false))

    /** Hashtags for a `#` completion, by name. */
    public suspend fun hashtags(reader: SignedInAccount, query: String): Answer<List<String>> =
        clients.answer(reader, SearchEndpoints.search(query, type = "hashtags", limit = COMPLETIONS))
            .map { results -> results.hashtags.map { it.name } }

    /** The server's custom emoji, fetched at most once a day per server; none when it cannot say. */
    public suspend fun emojis(reader: SignedInAccount): List<CustomEmoji> = emojiLock.withLock {
        val now = clock.millis()
        emojis[reader.host]?.takeIf { (at, _) -> now - at < EMOJI_TTL }?.let { return it.second }
        val answer = clients.answer(reader, InstanceEndpoints.customEmojis())
        val list = (answer as? Answer.Got)?.value ?: return emptyList()
        emojis[reader.host] = now to list
        list
    }

    /** The reader's posting defaults; null when the server does not say. */
    public suspend fun preferences(reader: SignedInAccount): Preferences? =
        (clients.answer(reader, InstanceEndpoints.preferences()) as? Answer.Got)?.value

    /** The server's own GIF library, searched by [query] when there is one, a page from [offset]. */
    public suspend fun gifs(reader: SignedInAccount, query: String?, offset: Int): Answer<GifLibrary> =
        clients.answer(reader, GifEndpoints.library(query, offset = offset))

    /** Attaches GIF [slug] as the server's own copy; nothing is downloaded or uploaded. */
    public suspend fun attachGif(reader: SignedInAccount, slug: String, description: String?): Answer<MediaAttachment> =
        clients.answer(reader, MediaEndpoints.fromGif(slug, description))

    /**
     * Attaches the file at [path] in [reader]'s own Nextcloud files, copied server-side, so a picture
     * already on the Nextcloud never travels to the phone and back. The path is remembered.
     */
    public suspend fun attachFile(reader: SignedInAccount, path: String): Answer<MediaAttachment> =
        clients.answer(reader, MediaEndpoints.fromNextcloudFile(path, null)).also { answer ->
            if (answer is Answer.Got) {
                settings.update(reader.id) { current ->
                    current.copy(
                        recentFilePaths = (listOf(path) + current.recentFilePaths).distinct().take(RECENT_PATHS),
                    )
                }
            }
        }

    /** The Nextcloud files [reader] attached last, by path, newest first. */
    public suspend fun recentPaths(reader: SignedInAccount): List<String> =
        settings.settings(reader.id).first().recentFilePaths

    /** The hashtags [reader] used most recently, newest first, offered before the server's. */
    public suspend fun recentTags(reader: SignedInAccount): List<String> =
        settings.settings(reader.id).first().recentTags

    /** Remembers [tags] as the most recent, keeping the newest [RECENT_TAGS]. */
    public suspend fun rememberTags(reader: SignedInAccount, tags: List<String>) {
        if (tags.isEmpty()) return
        settings.update(reader.id) { current ->
            val recent = (tags + current.recentTags).distinctBy(String::lowercase).take(RECENT_TAGS)
            current.copy(recentTags = recent)
        }
    }

    private companion object {
        const val COMPLETIONS = 8
        const val RECENT_TAGS = 20
        const val RECENT_PATHS = 8
        val EMOJI_TTL = Duration.ofHours(24).toMillis()
    }
}
