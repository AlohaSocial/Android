// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import java.time.Instant
import social.aloha.core.data.timeline.FilterEvaluator
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.html.RichTextCache
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Account
import social.aloha.core.model.FeedMode
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterContext
import social.aloha.core.model.Relationship
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

        ProfileTab.Collections, ProfileTab.Stories -> null
    }

    fun header(account: Account, cache: RichTextCache, colors: RichTextColors, isSelf: Boolean): ProfileHeader {
        val mapper = StatusRowMapper(cache, colors)
        return ProfileHeader(
            author = mapper.author(account),
            emojis = account.emojis,
            headerUrl = account.header?.takeUnless { it.endsWith("/missing.png") },
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
            followers = account.followersCount,
            locked = account.locked,
            url = account.url,
            isSelf = isSelf,
        )
    }

    fun relation(relationship: Relationship): Relation = Relation(
        following = relationship.following,
        requested = relationship.requested,
        followedBy = relationship.followedBy,
        muting = relationship.muting,
        blocking = relationship.blocking,
        blockedBy = relationship.blockedBy,
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
        row: (Status, List<String>?) -> StatusRowUi,
        loadingGaps: Set<String>,
    ): List<ProfileItem> = stored.mapNotNull { stored ->
        when (stored) {
            is TimelineRow.Gap -> ProfileItem.Gap(stored.id, loading = stored.id in loadingGaps)

            is TimelineRow.Post -> when (val decision = decide(stored.status)) {
                FilterEvaluator.Decision.Hide -> null
                is FilterEvaluator.Decision.Warn -> ProfileItem.Post(row(stored.status, decision.titles))
                FilterEvaluator.Decision.Show -> ProfileItem.Post(row(stored.status, null))
            }
        }
    }
}
