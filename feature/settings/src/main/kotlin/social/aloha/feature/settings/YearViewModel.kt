// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.Trouble
import social.aloha.core.data.previewText
import social.aloha.core.data.trouble
import social.aloha.core.data.year.YearInReview
import social.aloha.core.model.AnnualReport
import social.aloha.core.model.WrappedAnnualReports
import social.aloha.core.network.ApiError

/** One of the year's top posts, as the page shows it: why it is there, and what it said. */
@Immutable
internal data class TopPost(val why: TopKind, val statusId: String, val text: String)

internal enum class TopKind { Boosts, Replies, Favourites }

/**
 * The years the server has, the one shown, and its top posts. [none] when the server keeps no years
 * (a 404, as Mastodon before 4.3 answers), [trouble] when it could not be asked.
 */
@Immutable
internal data class YearState(
    val loading: Boolean = true,
    val reports: List<AnnualReport> = emptyList(),
    val shown: Int? = null,
    val posts: Map<Int, List<TopPost>> = emptyMap(),
    val none: Boolean = false,
    val trouble: Trouble? = null,
) {
    val report: AnnualReport? get() = reports.firstOrNull { it.year == shown }
}

@HiltViewModel
internal class YearViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val years: YearInReview,
) : ViewModel() {
    private val current = MutableStateFlow(YearState())
    val state: StateFlow<YearState> = current.asStateFlow()

    init {
        load()
    }

    fun load() {
        val reader = accounts.activeAccount.value ?: return
        current.update { it.copy(loading = true, trouble = null) }
        viewModelScope.launch {
            current.value = when (val answer = years.reports(reader)) {
                is Answer.Got -> loaded(answer.value)

                is Answer.Missed -> YearState(
                    loading = false,
                    none = answer.error == ApiError.NotFound,
                    trouble = answer.error.trouble.takeUnless { answer.error == ApiError.NotFound },
                )
            }
            current.value.shown?.let { years.markRead(reader, it) }
        }
    }

    fun show(year: Int) {
        current.update { it.copy(shown = year) }
        accounts.activeAccount.value?.let { reader -> viewModelScope.launch { years.markRead(reader, year) } }
    }

    private fun loaded(wrapped: WrappedAnnualReports) = YearState(
        loading = false,
        reports = wrapped.annualReports,
        shown = wrapped.annualReports.firstOrNull()?.year,
        posts = wrapped.annualReports.associate { report ->
            val top = report.data.topStatuses
            report.year to listOfNotNull(
                post(wrapped, TopKind.Boosts, top.byReblogs),
                post(wrapped, TopKind.Replies, top.byReplies),
                post(wrapped, TopKind.Favourites, top.byFavourites),
            )
        },
        none = wrapped.annualReports.isEmpty(),
    )

    private fun post(wrapped: WrappedAnnualReports, why: TopKind, id: String?): TopPost? =
        wrapped.status(id)?.let { TopPost(why, it.id, it.previewText().orEmpty()) }
}
