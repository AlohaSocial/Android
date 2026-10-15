// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv

import social.aloha.core.media.ShortSource
import social.aloha.core.model.VideoChapter
import social.aloha.tv.home.HomeLinks
import social.aloha.tv.home.HomeUiState
import social.aloha.tv.home.PersonCardUi
import social.aloha.tv.home.Shelf
import social.aloha.tv.home.ShelfKind
import social.aloha.tv.home.VideoCardUi
import social.aloha.tv.watch.CommentUi
import social.aloha.tv.watch.ReportReason
import social.aloha.tv.watch.TvShortUi
import social.aloha.tv.watch.TvShortsUiState
import social.aloha.tv.watch.WatchActions
import social.aloha.tv.watch.WatchUiState

/** Cards, shelves and pages as the screens get them, without a server or images. */
internal object Samples {
    fun card(id: String, title: String, covered: Boolean = false, progress: Float? = null) = VideoCardUi(
        id = id,
        title = title,
        author = "Bob",
        thumbnail = null,
        covered = covered,
        coverColour = null,
        short = false,
        progress = progress,
        preview = emptyList(),
    )

    val people = listOf(PersonCardUi("2", "Bob", "bob", null), PersonCardUi("3", "Jane", "jane@tube.example", null))

    val home = HomeUiState(
        shelves = listOf(
            Shelf(ShelfKind.ContinueWatching, listOf(card("c1", "The harbour tour", progress = 0.4f))),
            Shelf(
                ShelfKind.HomeVideo,
                listOf(card("v1", "Sailing at dawn"), card("v2", "Night market", covered = true), card("v3", "Reef")),
            ),
            Shelf(ShelfKind.Following, people = people),
            Shelf(ShelfKind.Trending, listOf(card("t1", "Volcano walk"), card("t2", "Ukulele lesson"))),
        ),
        loadedOnce = true,
    )

    val watch = WatchUiState(
        title = "The harbour tour",
        author = "Bob",
        description = "A walk round the harbour at sunrise, from the pier to the lighthouse.",
        chapters = listOf(
            VideoChapter(0.0, "Start"),
            VideoChapter(90.0, "The pier"),
            VideoChapter(200.0, "Lighthouse"),
        ),
        comments = listOf(CommentUi("c1", "Jane", "Lovely light."), CommentUi("c2", "Alice", "Where is this?")),
        following = false,
        loading = false,
    )

    val shorts = TvShortsUiState(
        shorts = listOf(
            TvShortUi(card("s1", "Wave"), ShortSource("s1", emptyList())),
            TvShortUi(card("s2", "Gecko", covered = true), ShortSource("s2", emptyList())),
        ),
        loadedOnce = true,
    )

    val nowhere = HomeLinks(onVideo = {}, onShort = {}, onPerson = {}, onSearch = {}, onAccount = {})

    fun actions(onReport: (ReportReason) -> Unit = {}) =
        WatchActions(onReveal = {}, onSeek = {}, onFollow = {}, onReport = onReport, onRetry = {})
}
