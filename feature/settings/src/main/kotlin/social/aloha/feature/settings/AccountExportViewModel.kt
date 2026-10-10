// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.model.MigrationListKind
import social.aloha.core.model.SignedInAccount

/** What can be taken away: the whole account as an archive when [list] is null, else one of its lists. */
@Immutable
internal data class ExportItem(val list: MigrationListKind? = null) {
    val isArchive: Boolean get() = list == null

    /** The name the file is offered under. */
    fun fileName(handle: String, today: LocalDate): String =
        list?.let { "${it.wire}.csv" } ?: "social-$handle-$today.zip"
}

@Immutable
internal data class AccountExportUiState(
    val status: PageStatus = PageStatus.Loading,
    val handle: String = "",
    val working: ExportItem? = null,
    val saved: ExportItem? = null,
    val failed: Boolean = false,
)

/** What the export page asks for. */
internal interface AccountExportActions {
    /** Writes [item] into the document at [uri], which the reader chose. */
    fun onExport(item: ExportItem, uri: String)

    fun onRetry()

    fun onResultShown()
}

/** [readerId]'s account to keep or take elsewhere, each part saved where the reader chooses. */
@HiltViewModel(assistedFactory = AccountExportViewModel.Factory::class)
internal class AccountExportViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    @param:ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val extras: NextcloudExtras,
) : ViewModel(),
    AccountExportActions {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): AccountExportViewModel
    }

    private val state = MutableStateFlow(AccountExportUiState())
    val uiState: StateFlow<AccountExportUiState> = state.asStateFlow()

    init {
        load()
    }

    override fun onRetry() = load()

    override fun onResultShown() = state.update { it.copy(saved = null, failed = false) }

    override fun onExport(item: ExportItem, uri: String) {
        if (state.value.working != null) return
        state.update { it.copy(working = item) }
        viewModelScope.launch {
            val saved = save(item, uri.toUri())
            state.update { it.copy(working = null, saved = item.takeIf { saved }, failed = !saved) }
        }
    }

    private suspend fun save(item: ExportItem, uri: Uri): Boolean {
        val account = accounts.byId(readerId) ?: return false
        return saveToDocument(context, uri) { write(account, item, it) }
    }

    private suspend fun write(account: SignedInAccount, item: ExportItem, out: OutputStream): Answer<Unit> =
        item.list?.let { extras.exportList(account, it, out) } ?: extras.exportAccount(account, out)

    private fun load() {
        viewModelScope.launch {
            val account = accounts.byId(readerId)
            state.update {
                it.copy(status = pageStatusOf(account) ?: PageStatus.Ready, handle = account?.handle.orEmpty())
            }
        }
    }
}
