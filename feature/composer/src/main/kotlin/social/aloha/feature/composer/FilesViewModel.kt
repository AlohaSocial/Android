// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.Collator
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import social.aloha.core.data.AccountRepository
import social.aloha.core.model.ServerLimits
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.nextcloud.NextcloudFile
import social.aloha.core.nextcloud.NextcloudFiles
import social.aloha.core.nextcloud.Preview
import social.aloha.core.sync.FilesUpload
import social.aloha.core.sync.FilesUploadState
import social.aloha.core.sync.FilesUploads

/** How a folder's listing stands. */
internal enum class FilesStatus { Loading, Ready, Offline, SignedOut, Forbidden, Gone, Failed }

internal enum class FileSort { Name, Newest, Largest }

/** Why a file cannot be attached. */
internal sealed interface Unavailable {
    /** End-to-end encrypted: the server cannot read it. */
    data object Encrypted : Unavailable

    /** Shared without the right to read it. */
    data object Unreadable : Unavailable

    /** Not a type the server takes. */
    data object Type : Unavailable

    /** Over the server's ceiling for its kind. */
    data class TooLarge(val limitBytes: Long) : Unavailable
}

/** A file or folder as the browser shows it: what it is, and why it cannot be attached, if it cannot. */
@Immutable
internal data class FileRow(val file: NextcloudFile, val unavailable: Unavailable? = null)

@Immutable
internal data class FilesUiState(
    /** The folder shown, a path from the root of the writer's files; `""` is the root. */
    val folder: String = "",
    val status: FilesStatus = FilesStatus.Loading,
    val rows: List<FileRow> = emptyList(),
    val sort: FileSort = FileSort.Name,
    /** The paths picked, from any folder, in the order they were picked. */
    val selected: List<String> = emptyList(),
    /** How many more attachments the post has room for. */
    val room: Int = 0,
    /** Whether files may be added to the folder shown. */
    val canUpload: Boolean = false,
    /** What is on its way into the folder shown. */
    val uploads: List<FilesUpload> = emptyList(),
    val uploadFailed: Boolean = false,
) {
    val full: Boolean get() = selected.size >= room
}

/** What the Files browser asks for. */
internal interface FilesActions {
    fun onOpen(folder: String)

    fun onUp()

    fun onToggle(path: String)

    fun onSort(sort: FileSort)

    fun onRetry()

    /** Saves the device file at [uri] into the folder shown, then picks it once it is there. */
    fun onUpload(uri: String)

    fun onCancelUpload(upload: FilesUpload)

    fun onUploadFailureShown()
}

/**
 * The writer's Nextcloud Files, to attach from: folders to go through, files to pick, each one the post
 * cannot take shown with why, and a device file saved into the folder shown to be picked from there.
 * What is picked is attached by path, so no bytes of it leave the server.
 */
@HiltViewModel(assistedFactory = FilesViewModel.Factory::class)
internal class FilesViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val files: NextcloudFiles,
    private val uploads: FilesUploads,
) : ViewModel(),
    FilesActions {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): FilesViewModel
    }

    private val state = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = state.asStateFlow()

    private var account: SignedInAccount? = null
    private var listing: Job? = null
    private var watching: Job? = null

    // the uploads asked for here, until they are over: one saved is picked, one that failed is told
    private val asked = mutableSetOf<UUID>()

    /** Opened for a post with [room] for that many more attachments, at the folder last shown, listed afresh. */
    fun onShown(room: Int) {
        state.update { it.copy(room = room, selected = emptyList()) }
        open(state.value.folder)
    }

    /** The server's preview of [file], [edge] pixels across; null where it has none. */
    suspend fun preview(file: NextcloudFile, edge: Int): Preview? = reader()?.let { files.preview(it, file, edge) }

    override fun onOpen(folder: String) = open(folder)

    override fun onUp() = open(state.value.folder.substringBeforeLast('/', missingDelimiterValue = ""))

    override fun onToggle(path: String) = state.update {
        when {
            path in it.selected -> it.copy(selected = it.selected - path)
            it.full -> it
            else -> it.copy(selected = it.selected + path)
        }
    }

    override fun onSort(sort: FileSort) = state.update { it.copy(sort = sort, rows = it.rows.sortedBy(sort)) }

    override fun onRetry() = open(state.value.folder)

    override fun onUpload(uri: String) {
        val folder = state.value.folder
        viewModelScope.launch {
            val reader = reader()
            // asked for is asked for: closing the browser right away must not lose it, nor leave a copy
            // without the upload that deletes it
            val id = reader?.let {
                withContext(NonCancellable + Dispatchers.IO) {
                    context.contentResolver.copyPicked(uri.toUri(), File(context.filesDir, UPLOADS))
                        ?.let { picked -> uploads.enqueue(reader.id, picked.file, fileName(picked.fileName), folder) }
                }
            }
            if (id == null) state.update { it.copy(uploadFailed = true) } else asked += id
        }
    }

    override fun onCancelUpload(upload: FilesUpload) = uploads.cancel(upload.id)

    override fun onUploadFailureShown() = state.update { it.copy(uploadFailed = false) }

    private suspend fun reader(): SignedInAccount? = account ?: accounts.byId(readerId)?.also { account = it }

    private fun open(folder: String) {
        state.update { it.copy(folder = folder, status = FilesStatus.Loading, rows = emptyList(), canUpload = false) }
        load(folder, picking = null)
        watching?.cancel()
        watching = viewModelScope.launch {
            uploads.observe(readerId, folder).collect { runs ->
                state.update { it.copy(uploads = runs.filter { run -> run.state.going }) }
                runs.filter { it.id in asked && !it.state.going }.forEach { ended(it, folder) }
            }
        }
    }

    private fun ended(upload: FilesUpload, folder: String) {
        asked -= upload.id
        when (val over = upload.state) {
            is FilesUploadState.Done -> load(folder, picking = over.path)
            FilesUploadState.Failed -> state.update { it.copy(uploadFailed = true) }
            else -> Unit
        }
    }

    /** Lists [folder], and picks [picking] in it when the post has room and can take it. */
    private fun load(folder: String, picking: String?) {
        listing?.cancel()
        listing = viewModelScope.launch {
            val reader = reader()
            val answer = reader?.let { files.list(it, folder) }
            val limits = reader?.capabilities?.limits ?: ServerLimits.MastodonDefaults
            state.update { current ->
                when (answer) {
                    // the account went while the browser was open
                    null -> current.copy(status = FilesStatus.Failed)

                    is ApiResult.Failure -> current.copy(status = answer.error.status())

                    is ApiResult.Success -> {
                        val rows = answer.value.files.map { row(it, limits) }.sortedBy(current.sort)
                        val pick = picking?.takeIf { path ->
                            !current.full && rows.any { it.file.path == path && it.unavailable == null }
                        }
                        current.copy(
                            status = FilesStatus.Ready,
                            rows = rows,
                            canUpload = answer.value.folder?.canAddFiles == true,
                            selected = current.selected + listOfNotNull(pick?.takeIf { it !in current.selected }),
                        )
                    }
                }
            }
        }
    }

    internal companion object {
        /** Where a device file waits until it is in the Nextcloud; the app's files, so a cleared cache keeps it. */
        const val UPLOADS = "files-uploads"

        /** The name a picker gave a file, as one Nextcloud saves: one path segment, and not `.` or `..`. */
        fun fileName(picked: String): String =
            picked.replace('/', '_').takeUnless { it.isBlank() || it == "." || it == ".." } ?: "upload"

        /** What Social's `media/from-file` would make of [file]: it checks a file as it checks an upload. */
        fun row(file: NextcloudFile, limits: ServerLimits): FileRow {
            val unavailable = when {
                file.isFolder -> null
                file.encrypted -> Unavailable.Encrypted
                !file.readable -> Unavailable.Unreadable
                else -> check(file, limits)
            }
            return FileRow(file, unavailable)
        }

        // the server copies the file as it is: what an upload would have shrunk or converted is refused
        private fun check(file: NextcloudFile, limits: ServerLimits): Unavailable? =
            when (val check = UploadPreflight.check(file.mimeType ?: OCTET_STREAM, file.size, limits)) {
                Preflight.Fits -> null

                is Preflight.TooLarge -> Unavailable.TooLarge(check.limitBytes)

                Preflight.ShrinkPicture if file.size > limits.imageSizeLimit -> Unavailable.TooLarge(
                    limits.imageSizeLimit,
                )

                else -> Unavailable.Type
            }

        /** Folders first, then by [sort]; names as people read them, not by character code. */
        fun List<FileRow>.sortedBy(sort: FileSort): List<FileRow> {
            val collator = Collator.getInstance()
            val byName = Comparator<FileRow> { a, b -> collator.compare(a.file.name, b.file.name) }
            val within = when (sort) {
                FileSort.Name -> byName
                FileSort.Newest -> compareByDescending<FileRow> { it.file.modified }.then(byName)
                FileSort.Largest -> compareByDescending<FileRow> { it.file.size }.then(byName)
            }
            return sortedWith(compareBy<FileRow> { !it.file.isFolder }.then(within))
        }

        private const val OCTET_STREAM = "application/octet-stream"
    }
}

private val FilesUploadState.going: Boolean
    get() = this is FilesUploadState.Queued || this is FilesUploadState.Sending

private fun ApiError.status(): FilesStatus = when (this) {
    is ApiError.Transport -> FilesStatus.Offline
    is ApiError.Unauthorised -> FilesStatus.SignedOut
    is ApiError.Forbidden -> FilesStatus.Forbidden
    ApiError.NotFound -> FilesStatus.Gone
    else -> FilesStatus.Failed
}
