// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.mediaviewer

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.VideoSource
import social.aloha.core.model.VideoSources
import social.aloha.core.navigation.MediaViewerKey
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.copyLink
import social.aloha.core.ui.openInBrowser
import social.aloha.core.ui.shareLink

/**
 * The post whose attachments the viewer shows, as the reader's server sees it; null until known.
 * [stored] is the entry as stored, a boost where it was one, which the actions act on.
 */
internal data class ViewedPost(val reader: SignedInAccount, val status: Status, val stored: Status = status)

/** The post behind a viewer: the attachments it shows, and whose they are, for a report. */
@HiltViewModel(assistedFactory = MediaViewerViewModel.Factory::class)
internal class MediaViewerViewModel @AssistedInject constructor(
    @Assisted private val key: MediaViewerKey,
    private val accounts: AccountRepository,
    private val statuses: StatusRepository,
    private val interactions: StatusInteractions,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: MediaViewerKey): MediaViewerViewModel
    }

    private val state = MutableStateFlow<ViewedPost?>(null)
    val post: StateFlow<ViewedPost?> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val reader = accounts.byId(key.readerId) ?: return@launch
            // a viewer opens from a post on screen, which is stored; a toggle here shows as it changes there
            statuses.observe(reader.id, key.statusId).collect { stored ->
                stored?.let { state.value = ViewedPost(reader, it.displayed, it) }
            }
        }
    }

    /** Boosts, favourites or bookmarks the post, or takes it back. */
    fun onToggle(toggle: Toggle) {
        val viewed = state.value ?: return
        viewModelScope.launch { interactions.toggle(viewed.reader, viewed.stored, toggle) }
    }

    fun sources(attachment: MediaAttachment): List<VideoSource> {
        val viewed = state.value ?: return emptyList()
        return VideoSources.ladder(viewed.status, attachment, viewed.reader.capabilities.apiBase)
    }
}

/**
 * The media viewer over whatever was on screen, closed by its button, by back or by dragging the
 * picture away. Saving goes through the system's downloads, which asks for storage only on Android 9 and
 * older, where saving to the shared pictures folder needs it.
 */
@Composable
public fun MediaViewerRoute(
    key: MediaViewerKey,
    navigation: StatusNavigation,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<MediaViewerViewModel, MediaViewerViewModel.Factory>(key = key.toString()) { it.create(key) }
    val post by viewModel.post.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nav by rememberUpdatedState(navigation)
    val close by rememberUpdatedState(onClose)
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saving = stringResource(R.string.viewer_saving)
    val copied = stringResource(R.string.viewer_copied)
    var pending by remember { mutableStateOf<MediaAttachment?>(null) }
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.takeIf { granted }?.let {
            save(context, it)
            scope.launch { snackbars.showSnackbar(saving) }
        }
        pending = null
    }
    BackHandler(enabled = post == null, onBack = onClose)
    val actions = remember(viewModel) {
        object : MediaViewerActions {
            override fun onClose() = close()

            override fun onSave(attachment: MediaAttachment) {
                if (needsStorage(context)) {
                    pending = attachment
                    askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    save(context, attachment)
                    scope.launch { snackbars.showSnackbar(saving) }
                }
            }

            override fun onShare(attachment: MediaAttachment) {
                attachment.url?.let { shareLink(context, it) }
            }

            override fun onCopy(attachment: MediaAttachment) {
                val url = attachment.url ?: return
                copyLink(context, url)
                scope.launch { snackbars.showSnackbar(copied) }
            }

            override fun onOpenInBrowser(attachment: MediaAttachment) {
                attachment.url?.let { openInBrowser(context, it) }
            }

            override fun onReport() {
                val status = post?.status ?: return
                close()
                nav.report(status.account.id, status.account.acct, status.id)
            }

            override fun onReply() {
                val status = post?.status ?: return
                close()
                nav.openComposer(status.id)
            }

            override fun onToggle(toggle: Toggle) = viewModel.onToggle(toggle)
        }
    }
    Box(modifier.fillMaxSize()) {
        val viewed = post
        if (viewed == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            MediaViewer(viewed.status.mediaAttachments, key.index, viewModel::sources, actions, status = viewed.status)
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
    }
}

/** Saving to the shared pictures folder needs storage on Android 9 and older; later it needs nothing. */
private fun needsStorage(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
    PackageManager.PERMISSION_GRANTED

/** Downloads [attachment] into Pictures through the system, which shows its progress and where it went. */
private fun save(context: Context, attachment: MediaAttachment) {
    val (uri, name) = savedAs(attachment) ?: return
    val request = DownloadManager.Request(uri)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Aloha Social/$name")
    context.getSystemService<DownloadManager>()?.enqueue(request)
}

/**
 * Where [attachment] downloads from, and the name it is saved under: its id, and the extension of a
 * picture or video. The server's own file name never names the file, so it cannot reach another
 * folder or pass for another kind of file; an address that is not the web is never downloaded.
 */
internal fun savedAs(attachment: MediaAttachment): Pair<Uri, String>? {
    val uri = attachment.url?.toUri()?.takeIf { it.scheme?.lowercase() in WEB } ?: return null
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.takeIf { it in SAVED_KINDS }
    val id = attachment.id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "media" }
    return uri to "aloha-$id" + extension?.let { ".$it" }.orEmpty()
}

private val WEB = setOf("http", "https")
private val SAVED_KINDS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "avif", "mp4", "webm", "mov", "m4v")
