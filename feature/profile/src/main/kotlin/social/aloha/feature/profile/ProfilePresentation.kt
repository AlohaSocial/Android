// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import java.time.Instant
import social.aloha.core.data.timeline.FilterEvaluator
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.html.RichTextCache
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Account
import social.aloha.core.model.FeaturedTag
import social.aloha.core.model.FeedMode
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterContext
import social.aloha.core.model.ProfileLayout
import social.aloha.core.model.Relationship
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.toAnnotatedString

/** How an account, and the reader's relation to it, become what the profile draws. */
internal object ProfilePresentation {
    /** The timeline behind a posts tab; the others have none. Videos narrow the media tab on the device. */
    fun timelineOf(accountId: String, tab: ProfileTab): TimelineKey? = when (tab) {
        ProfileTab.Posts -> TimelineKey(
            FeedMode.Home,
            TimelineSource.Account(accountId, includeReplies = false, onlyMedia = false),
        )

        ProfileTab.Replies -> TimelineKey(
            FeedMode.Home,
            TimelineSource.Account(accountId, includeReplies = true, onlyMedia = false),
        )

        ProfileTab.Media -> TimelineKey(
            FeedMode.Home,
            TimelineSource.Account(accountId, includeReplies = false, onlyMedia = true),
        )

        ProfileTab.Videos -> TimelineKey(
            FeedMode.Video,
            TimelineSource.Account(accountId, includeReplies = false, onlyMedia = true),
        )

        ProfileTab.Featured, ProfileTab.Collections, ProfileTab.Stories -> null
    }

    /**
     * [realFollowers] false where the server sent a reader who hides the numbers 0 followers whatever the
     * account had (see [ServerCapabilities.realFollowerCounts]): a 0 there is not known.
     */
    fun header(
        account: Account,
        cache: RichTextCache,
        colors: RichTextColors,
        isSelf: Boolean,
        realFollowers: Boolean = true,
    ): ProfileHeader {
        val mapper = StatusRowMapper(cache, colors)
        return ProfileHeader(
            author = mapper.author(account),
            emojis = account.emojis,
            headerUrl = account.header,
            note = StatusHtmlParser.parse(account.note, emojis = account.emojis).toAnnotatedString(colors),
            fields = account.fields.map { field ->
                ProfileHeader.Field(
                    name = StatusHtmlParser.parseText(field.name, account.emojis).toAnnotatedString(colors),
                    value = StatusHtmlParser.parse(field.value, emojis = account.emojis).toAnnotatedString(colors),
                    verified = field.isVerified,
                )
            },
            posts = account.statusesCount,
            following = account.followingCount,
            followers = account.followersCount.takeIf { it > 0 || realFollowers },
            locked = account.locked,
            url = account.url,
            isSelf = isSelf,
            // the reader's server names its own accounts without one
            domain = account.acct.substringAfter('@', "").ifEmpty { null },
            joined = account.createdAt,
            movedTo = account.moved?.let { ProfileHeader.Moved(it.id, it.qualifiedHandle) },
            memorial = account.memorial,
        )
    }

    /**
     * The three timelines, Featured where the account features something, Videos, then what the server has;
     * Media, Videos and Collections only where the account's [layout] offers them or the reader is on them
     * ([current]), as Nextcloud Social's web app keeps the tab open when a refreshed layout hides it.
     */
    fun tabs(
        capabilities: ServerCapabilities,
        featured: Boolean,
        layout: ProfileLayout?,
        current: ProfileTab,
    ): List<ProfileTab> = buildList {
        fun offered(tab: ProfileTab, name: String) = tab == current || layout?.shows(name) != false
        addAll(listOf(ProfileTab.Posts, ProfileTab.Replies))
        if (offered(ProfileTab.Media, ProfileLayout.PHOTOS)) add(ProfileTab.Media)
        if (featured) add(ProfileTab.Featured)
        if (offered(ProfileTab.Videos, ProfileLayout.VIDEOS)) add(ProfileTab.Videos)
        if (capabilities.collections && offered(ProfileTab.Collections, ProfileLayout.COLLECTIONS)) {
            add(ProfileTab.Collections)
        }
        if (capabilities.stories) add(ProfileTab.Stories)
    }

    /** The tab the account's profile opens on: its photos are the Media timeline. */
    fun firstTab(layout: ProfileLayout?): ProfileTab = when (layout?.defaultTab) {
        ProfileLayout.PHOTOS -> ProfileTab.Media
        ProfileLayout.VIDEOS -> ProfileTab.Videos
        else -> ProfileTab.Posts
    }

    /**
     * What the account features, its posts drawn by [row]; none when it features nothing. A hashtag opens
     * the account's posts with it where the server narrows them (see [ServerCapabilities.accountPostsNarrowed]).
     */
    fun featured(
        pinned: List<Status>,
        tags: List<FeaturedTag>,
        capabilities: ServerCapabilities,
        row: (Status) -> StatusRowUi,
    ): FeaturedUi? = FeaturedUi(pinned.map(row), tags, capabilities.accountPostsNarrowed).takeIf {
        it.posts.isNotEmpty() ||
            it.tags.isNotEmpty()
    }

    fun relation(relationship: Relationship): Relation = Relation(
        following = relationship.following,
        requested = relationship.requested,
        followedBy = relationship.followedBy,
        muting = relationship.muting,
        blocking = relationship.blocking,
        blockedBy = relationship.blockedBy,
        showingReblogs = relationship.showingReblogs,
        notifying = relationship.notifying,
        domainBlocking = relationship.domainBlocking,
        note = relationship.note,
    )

    /**
     * What the reader's filters decide for a post on a profile, as of [now]; each post's text is parsed
     * once, in [cache].
     */
    fun decider(filters: List<Filter>, now: Instant, cache: RichTextCache): (Status) -> FilterEvaluator.Decision {
        val evaluator = FilterEvaluator(filters, FilterContext.Account, now)
        return { status -> evaluator.decision(status, cache.plainText(status) + " " + status.displayed.spoilerText) }
    }

    /** A profile's posts, filtered by [decide]. */
    fun items(
        stored: List<TimelineRow>,
        decide: (Status) -> FilterEvaluator.Decision,
        row: (Status, FilterEvaluator.Decision.Warn?) -> StatusRowUi,
        loadingGaps: Set<String>,
    ): List<ProfileItem> = stored.mapNotNull { stored ->
        when (stored) {
            is TimelineRow.Gap -> ProfileItem.Gap(stored.id, loading = stored.id in loadingGaps)

            is TimelineRow.Post -> when (val decision = decide(stored.status)) {
                FilterEvaluator.Decision.Hide -> null
                is FilterEvaluator.Decision.Warn -> ProfileItem.Post(row(stored.status, decision))
                FilterEvaluator.Decision.Show -> ProfileItem.Post(row(stored.status, null))
            }
        }
    }
}
