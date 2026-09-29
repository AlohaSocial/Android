// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import java.io.File
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import social.aloha.core.model.TimelineFilters
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.ApiClient
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult
import social.aloha.core.network.Credentials
import social.aloha.core.network.DecodingFailure
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.endpoints.AnnualReportEndpoints
import social.aloha.core.network.endpoints.BlockEndpoints
import social.aloha.core.network.endpoints.CollectionEndpoints
import social.aloha.core.network.endpoints.ComposeEndpoints
import social.aloha.core.network.endpoints.DirectoryOrder
import social.aloha.core.network.endpoints.DiscoverMedia
import social.aloha.core.network.endpoints.DiscoveryEndpoints
import social.aloha.core.network.endpoints.FeaturedTagEndpoints
import social.aloha.core.network.endpoints.FilterEndpoints
import social.aloha.core.network.endpoints.FollowRequestEndpoints
import social.aloha.core.network.endpoints.GifEndpoints
import social.aloha.core.network.endpoints.InstanceEndpoints
import social.aloha.core.network.endpoints.InterestEndpoints
import social.aloha.core.network.endpoints.ListEndpoints
import social.aloha.core.network.endpoints.MarkerEndpoints
import social.aloha.core.network.endpoints.MediaEndpoints
import social.aloha.core.network.endpoints.NotificationEndpoints
import social.aloha.core.network.endpoints.PlaceEndpoints
import social.aloha.core.network.endpoints.PollEndpoints
import social.aloha.core.network.endpoints.ProfileEndpoints
import social.aloha.core.network.endpoints.SafetyEndpoints
import social.aloha.core.network.endpoints.SearchEndpoints
import social.aloha.core.network.endpoints.StatusAction
import social.aloha.core.network.endpoints.StatusEndpoints
import social.aloha.core.network.endpoints.StatusExtraEndpoints
import social.aloha.core.network.endpoints.StatusPost
import social.aloha.core.network.endpoints.StoryEndpoints
import social.aloha.core.network.endpoints.TagEndpoints
import social.aloha.core.network.endpoints.TimelineEndpoints
import social.aloha.core.network.endpoints.VideoEndpoints

/**
 * Every body the dev instance answered, decoded through the endpoint factory that asks for it: the
 * request succeeds and lossy decoding drops nothing. A fixture that is neither mapped here nor listed
 * as not decodable fails [every fixture is decoded or explained], so a new capture cannot slip by.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorpusDecodingTest {
    private lateinit var server: MockSocialServer
    private lateinit var client: ApiClient
    private val dropped = mutableListOf<Pair<String, DecodingFailure>>()
    private val upload = File.createTempFile("upload", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    private val status = "1790637085797595891"
    private val video = "1790637104958214061"
    private val link = "1790637105561356153"
    private val photos = "1790637096330770789"
    private val edited = "1790637112034926731"

    private val decoded: Map<String, ApiRequest<*>> by lazy {
        mapOf(
            "api/accounts-alice-featured-tags.json" to ProfileEndpoints.featuredTags("6"),
            "api/accounts-alice-followers.json" to AccountEndpoints.followers("6"),
            "api/accounts-alice-following.json" to AccountEndpoints.following("6"),
            "api/accounts-alice-highlights.json" to ProfileEndpoints.highlights("6"),
            "api/accounts-alice-statuses-media.json" to
                TimelineEndpoints.timeline(TimelineSource.Account("6", includeReplies = true, onlyMedia = true)),
            "api/accounts-alice-statuses-pinned.json" to ProfileEndpoints.pinnedStatuses("6"),
            "api/accounts-alice-statuses.json" to
                TimelineEndpoints.timeline(TimelineSource.Account("6", includeReplies = true, onlyMedia = false)),
            "api/accounts-alice.json" to AccountEndpoints.account("6"),
            "api/accounts-jane-locked.json" to AccountEndpoints.account("8"),
            "api/accounts-lookup-bob.json" to AccountEndpoints.lookup("bob"),
            "api/accounts-relationships.json" to AccountEndpoints.relationships(listOf("7", "8", "5")),
            "api/accounts-search.json" to AccountEndpoints.search("ja"),
            "api/accounts-verify-credentials.json" to AccountEndpoints.verifyCredentials(),
            "api/announcements.json" to SafetyEndpoints.announcements(),
            "api/annual-reports.json" to AnnualReportEndpoints.all(),
            "api/blocks.json" to BlockEndpoints.blocks(),
            "api/bookmarks-bob.json" to TimelineEndpoints.timeline(TimelineSource.Bookmarks),
            "api/collection-items.json" to CollectionEndpoints.items("1"),
            "api/collection.json" to CollectionEndpoints.collection("1"),
            "api/collections-account-alice.json" to CollectionEndpoints.forAccount("6"),
            "api/collections-alice.json" to CollectionEndpoints.all(),
            "api/conversations-bob.json" to TimelineEndpoints.conversations(),
            "api/conversations-unread-count.json" to TimelineEndpoints.conversationsUnreadCount(),
            "api/custom-emojis.json" to InstanceEndpoints.customEmojis(),
            "api/directory.json" to SearchEndpoints.directory(DirectoryOrder.Active),
            "api/discover-posts.json" to DiscoveryEndpoints.discoverPosts(DiscoverMedia.Images),
            "api/favourites-bob.json" to TimelineEndpoints.timeline(TimelineSource.Favourites),
            "api/filters-v2-jane.json" to FilterEndpoints.all(),
            "api/follow-requests-jane.json" to FollowRequestEndpoints.all(),
            "api/followed-tags.json" to TagEndpoints.followed(),
            "api/gifs.json" to GifEndpoints.library("cat"),
            "api/instance-rules.json" to InstanceEndpoints.rules(),
            "api/instance-translation-languages.json" to InstanceEndpoints.translationLanguages(),
            "api/interests.json" to InterestEndpoints.state(),
            "api/list-accounts-jane.json" to ListEndpoints.accounts("1"),
            "api/lists-jane.json" to ListEndpoints.all(),
            "api/markers.json" to MarkerEndpoints.read(),
            "api/mutes.json" to BlockEndpoints.mutes(),
            "api/notifications-policy-v2.json" to NotificationEndpoints.policy(v2 = true),
            "api/notifications-requests.json" to NotificationEndpoints.requests(),
            "api/notifications-v1-jane.json" to NotificationEndpoints.flat(),
            "api/notifications-v1-unread-count.json" to NotificationEndpoints.unreadCount(grouped = false),
            "api/notifications-v1.json" to NotificationEndpoints.flat(),
            "api/notifications-v2-unread-count.json" to NotificationEndpoints.unreadCount(grouped = true),
            "api/notifications-v2.json" to NotificationEndpoints.grouped(),
            "api/places-search.json" to PlaceEndpoints.search("Ber"),
            "api/poll.json" to PollEndpoints.poll("1790637087495743511"),
            "api/preferences.json" to InstanceEndpoints.preferences(),
            "api/scheduled-statuses.json" to ComposeEndpoints.scheduled(),
            "api/search-account.json" to SearchEndpoints.search("bob", type = "accounts"),
            "api/search-aloha.json" to SearchEndpoints.search("aloha"),
            "api/search-hashtags.json" to SearchEndpoints.search("alo", type = "hashtags"),
            "api/starter-packs.json" to DiscoveryEndpoints.starterPacks(),
            "api/status-audio.json" to StatusEndpoints.status("1790637105549523409"),
            "api/status-bob.json" to StatusEndpoints.status("1790637117344364158"),
            "api/status-cw.json" to StatusEndpoints.status("1790637086177868305"),
            "api/status-direct.json" to StatusEndpoints.status("1790637086268689343"),
            "api/status-edited-history.json" to StatusEndpoints.history(edited),
            "api/status-edited-source.json" to StatusEndpoints.source(edited),
            "api/status-edited.json" to StatusEndpoints.status(edited),
            "api/status-link-card.json" to StatusEndpoints.card(link),
            "api/status-link.json" to StatusEndpoints.status(link),
            "api/status-photos-reblogged-by.json" to StatusEndpoints.rebloggedBy(photos),
            "api/status-photos.json" to StatusEndpoints.status(photos),
            "api/status-place.json" to StatusEndpoints.status("1790637112216295042"),
            "api/status-poll.json" to StatusEndpoints.status("1790637087495743511"),
            "api/status-private.json" to StatusEndpoints.status("1790637086257046711"),
            "api/status-public-card-none.json" to StatusEndpoints.card(status),
            "api/status-public-context.json" to StatusEndpoints.context(status),
            "api/status-public-delivery.json" to StatusExtraEndpoints.delivery(status),
            "api/status-public-favourited-by.json" to StatusEndpoints.favouritedBy(status),
            "api/status-public-quotes.json" to StatusExtraEndpoints.quotes(status),
            "api/status-public-reactions.json" to StatusExtraEndpoints.reactions(status),
            "api/status-public.json" to StatusEndpoints.status(status),
            "api/status-quote.json" to StatusEndpoints.status("1790637116374990863"),
            "api/status-reply.json" to StatusEndpoints.status("1790637115351395147"),
            "api/status-reply_2.json" to StatusEndpoints.status("1790637115422878628"),
            "api/status-sensitive_no_alt.json" to StatusEndpoints.status("1790637103267719986"),
            "api/status-short.json" to StatusEndpoints.status("1790637104870044831"),
            "api/status-unlisted.json" to StatusEndpoints.status("1790637086680883443"),
            "api/status-video.json" to StatusEndpoints.status(video),
            "api/stories-account-alice.json" to StoryEndpoints.forAccount("6"),
            "api/stories-carousel-bob.json" to StoryEndpoints.carousel(),
            "api/stories-self-alice.json" to StoryEndpoints.own(),
            "api/stories-v12-carousel-bob.json" to StoryEndpoints.carouselV2(),
            "api/suggestions-v2.json" to SearchEndpoints.suggestions(),
            "api/timeline-home-bob-sees-boost.json" to TimelineEndpoints.timeline(TimelineSource.Home),
            "api/timeline-home-jane-filtered.json" to TimelineEndpoints.timeline(TimelineSource.Home),
            "api/timeline-home-page.json" to TimelineEndpoints.timeline(TimelineSource.Home, limit = 3),
            "api/timeline-home.json" to TimelineEndpoints.timeline(TimelineSource.Home),
            "api/timeline-interests.json" to InterestEndpoints.timeline(limit = 10),
            "api/timeline-list-jane.json" to TimelineEndpoints.timeline(TimelineSource.List("1")),
            "api/timeline-public-local.json" to TimelineEndpoints.timeline(TimelineSource.Local),
            "api/timeline-public-only-media.json" to
                TimelineEndpoints.timeline(TimelineSource.Federated, TimelineFilters(onlyMedia = true)),
            "api/timeline-public-only-news.json" to
                TimelineEndpoints.timeline(TimelineSource.Federated, TimelineFilters(onlyNews = true)),
            "api/timeline-public-only-video.json" to
                TimelineEndpoints.timeline(TimelineSource.Federated, TimelineFilters(onlyVideo = true)),
            "api/timeline-public.json" to TimelineEndpoints.timeline(TimelineSource.Federated),
            "api/timeline-tag-aloha.json" to TimelineEndpoints.timeline(TimelineSource.Hashtag("aloha")),
            "api/trends-links.json" to SearchEndpoints.trendingLinks(),
            "api/trends-statuses.json" to DiscoveryEndpoints.trendingStatuses(),
            "api/trends-tags.json" to SearchEndpoints.trendingTags(),
            "writes/favourite-returns-status.json" to
                StatusEndpoints.action("1790637117344364158", StatusAction.Favourite),
            "writes/idempotent-post-1.json" to ComposeEndpoints.post(StatusPost("aloha")),
            "writes/idempotent-post-2.json" to ComposeEndpoints.post(StatusPost("aloha")),
            "writes/media-upload-v2.json" to
                MediaEndpoints.upload(upload, "photo.jpg", "image/jpeg", description = null),
            "writes/videos-continue-bob.json" to VideoEndpoints.continueWatching(),
            "writes/watched-bob.json" to
                VideoEndpoints.reportWatched(video, positionSeconds = 30.0, durationSeconds = 60.0),
        )
    }

    /** Fixtures a factory is not the reader of, each with the reason. */
    private val notDecoded = mapOf(
        "api/apps-register.json" to "read by the OAuth client, tested with it",
        "api/apps-verify-credentials.json" to "no factory: the app does not check its own registration",
        "api/oauth-token.json" to "read by the OAuth client, tested with it",
        "api/oauth-userinfo.json" to "read by the OAuth client, tested with it",
        "api/instance-privacy-policy.json" to "a 404 on this instance, checked below",
        "api/pixelfed-compose-settings.json" to
            "Pixelfed composer settings; the composer reads the server limits instead",
    )

    @BeforeAll
    fun start() {
        server = MockSocialServer(MockServerConfiguration.NextcloudWithoutRewrite).start()
        client = ApiClient(
            server.apiBase,
            { Credentials(MockCredentials.ACCESS_TOKEN) },
            OkHttpClient(),
            RateLimiter(nowMillis = Clock.systemUTC()::millis),
            Dispatchers.IO,
            failureListener = { path, failures ->
                synchronized(dropped) { failures.forEach { dropped += path to it } }
            },
        )
    }

    @AfterAll
    fun stop() {
        server.close()
        upload.delete()
    }

    @TestFactory
    fun `every captured body decodes through its factory and drops nothing`(): List<DynamicTest> =
        decoded.map { (name, request) ->
            DynamicTest.dynamicTest(name) {
                server.pinFixture(name)
                dropped.clear()
                val result = runBlocking { client.execute(request) }
                assertTrue(result is ApiResult.Success, "$name: $result")
                assertEquals(emptyList<Pair<String, DecodingFailure>>(), dropped, name)
            }
        }

    @Test
    fun `every fixture is decoded or explained`() {
        val captured = FixtureCorpus.nextcloudSocial.fixtures.map { it.name }.filter {
            it.startsWith("api/") ||
                it.startsWith("writes/")
        }
        val unexplained = captured - decoded.keys - notDecoded.keys
        assertEquals(emptySet<String>(), unexplained.toSet())
    }

    @Test
    fun `this instance serves no privacy policy, and says so with a 404`() {
        server.pinFixture("api/instance-privacy-policy.json")
        assertEquals(
            ApiError.NotFound,
            runBlocking {
                client.execute(InstanceEndpoints.privacyPolicy())
            }.let { (it as ApiResult.Failure).error },
        )
    }
}
