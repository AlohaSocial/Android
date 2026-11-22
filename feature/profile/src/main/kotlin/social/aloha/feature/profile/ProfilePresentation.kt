// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import java.time.Instant
import social.aloha.core.data.stories.StoryReel
import social.aloha.core.data.timeline.FilterEvaluator
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.html.RichTextCache
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Account
import social.aloha.core.model.FeaturedTag
import social.aloha.core.model.FeedMode
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterContext
import social.aloha.core.model.Relationship
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Story
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.core.navigation.OwnProfilePlace
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

        // the account's photos: `media_type=image` where the server narrows to them, else narrowed here
        ProfileTab.Photos -> TimelineKey(
            FeedMode.Photos,
            TimelineSource.Account(accountId, includeReplies = false, onlyMedia = true),
        )

        ProfileTab.Videos -> TimelineKey(
            FeedMode.Video,
            TimelineSource.Account(accountId, includeReplies = false, onlyMedia = true),
        )

        ProfileTab.Tagged, ProfileTab.Collections, ProfileTab.Saved, ProfileTab.Drafts, ProfileTab.Statistics -> null
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
            author = mapper.author(account).copy(avatarUrl = account.avatar),
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
     * The tabs every profile has, in the web app's order: Posts (with replies or without), Photos and
     * Videos, Tagged where Aloha Social keeps it, and Collections where the server has them. Every
     * profile shows them all, whatever tabs the account hid on the web, as the web app now does.
     */
    fun tabs(capabilities: ServerCapabilities): List<ProfileTab> = buildList {
        addAll(listOf(ProfileTab.Posts, ProfileTab.Replies, ProfileTab.Photos, ProfileTab.Videos))
        if (capabilities.isNextcloudSocial) add(ProfileTab.Tagged)
        if (capabilities.collections) add(ProfileTab.Collections)
    }

    /** The tab a profile opens on: the one of the reader's own tabs it was opened on ([open]), else Posts. */
    fun firstTab(open: OwnProfilePlace? = null): ProfileTab = when (open) {
        OwnProfilePlace.Favorites, OwnProfilePlace.Bookmarks -> ProfileTab.Saved
        OwnProfilePlace.Drafts -> ProfileTab.Drafts
        OwnProfilePlace.Statistics -> ProfileTab.Statistics
        null -> ProfileTab.Posts
    }

    /**
     * The tabs the reader's own profile adds after the others, as the web app does: Saved and Drafts, and
     * Statistics where Aloha Social counts them.
     */
    fun ownTabs(capabilities: ServerCapabilities): List<ProfileTab> =
        listOf(ProfileTab.Saved, ProfileTab.Drafts) + listOf(ProfileTab.Statistics).filter {
            capabilities.isNextcloudSocial
        }

    /** [account]'s [stories] still up, as its avatar plays them; none without any. */
    fun reel(account: Account, stories: List<Story>?, reader: SignedInAccount): StoryReel? =
        stories?.takeIf { it.isNotEmpty() }?.let { StoryReel(account, it, own = account.id == reader.serverAccountId) }

    /** [tabs], and on the reader's own profile ([reader]'s account is [account]) [ownTabs] after them. */
    fun allTabs(reader: SignedInAccount, account: Account?): List<ProfileTab> = tabs(reader.capabilities) +
        ownTabs(reader.capabilities).takeIf { account?.id == reader.serverAccountId }.orEmpty()

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
