// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import androidx.core.net.toUri

/** Where a post sends the person: its thread, a profile by id or handle, a hashtag, or a reply to it. */
public interface StatusNavigation {
    public fun openThread(statusId: String)

    public fun openProfile(accountId: String?, acct: String?)

    public fun openTag(name: String)

    /** A web address: in the app when it is a post, profile or hashtag the server finds, else the browser. */
    public fun openWeb(url: String)

    /** The composer: a new post, or a reply to [replyToId] when given. */
    public fun openComposer(replyToId: String?)

    /** The composer on the reader's own post [statusId]: edited, or deleted and written again when [redraft]. */
    public fun editPost(statusId: String, redraft: Boolean)
}

/** A delete the person asked for, of [row]; with [redraft] the post is written again after. */
public data class DeleteRequest(val row: StatusRowUi, val redraft: Boolean)

/**
 * The part of a row's actions every screen showing posts shares: links, the thread, profiles, and the
 * menu items that leave the app (share, copy, the browser). A screen supplies what changes a post.
 *
 * @param onCopied the link is on the clipboard, which the screen confirms.
 * @param onDeleteAsked the person asked to delete a post, or to write it again, which the screen
 *   confirms first.
 */
public abstract class RoutedStatusActions(
    private val context: Context,
    private val navigation: () -> StatusNavigation,
    private val onCopied: () -> Unit,
    private val onDeleteAsked: (DeleteRequest) -> Unit,
) : StatusActions {
    override val menu: Set<StatusMenuItem> = setOf(
        StatusMenuItem.Share,
        StatusMenuItem.CopyLink,
        StatusMenuItem.OpenInBrowser,
        StatusMenuItem.MuteConversation,
        StatusMenuItem.Pin,
        StatusMenuItem.Edit,
        StatusMenuItem.Redraft,
        StatusMenuItem.Delete,
    )

    public abstract fun onMute(row: StatusRowUi)

    public abstract fun onPin(row: StatusRowUi)

    override fun onOpen(statusId: String): Unit = navigation().openThread(statusId)

    override fun onProfile(accountId: String): Unit = navigation().openProfile(accountId, null)

    override fun onLink(target: RichLinkTarget): Unit = when (target) {
        is RichLinkTarget.Mention -> navigation().openProfile(target.accountId, target.acct)
        is RichLinkTarget.Hashtag -> navigation().openTag(target.name)
        is RichLinkTarget.Web -> navigation().openWeb(target.url)
    }

    // until there is a media viewer the post itself opens; within its own thread that is where the reader is
    override fun onMedia(row: StatusRowUi, index: Int): Unit = navigation().openThread(row.statusId)

    override fun onReply(row: StatusRowUi): Unit = navigation().openComposer(row.statusId)

    // only a thread's focused post has its reactions fetched, and offers them; its screen reacts
    override fun onReact(row: StatusRowUi, name: String, add: Boolean): Unit = Unit

    override fun onMenu(row: StatusRowUi, item: StatusMenuItem) {
        val url = row.url
        when (item) {
            StatusMenuItem.Share -> url?.let { share(context, it) }

            StatusMenuItem.CopyLink -> url?.let {
                copy(context, it)
                onCopied()
            }

            StatusMenuItem.OpenInBrowser -> url?.let { openInBrowser(context, it) }

            StatusMenuItem.MuteConversation -> onMute(row)

            StatusMenuItem.Pin -> onPin(row)

            StatusMenuItem.Edit -> navigation().editPost(row.statusId, redraft = false)

            StatusMenuItem.Redraft -> onDeleteAsked(DeleteRequest(row, redraft = true))

            StatusMenuItem.Delete -> onDeleteAsked(DeleteRequest(row, redraft = false))

            else -> Unit
        }
    }
}

/**
 * Opens [url] in a Custom Tab, the browser's view inside the app. Only web links open: a post can carry
 * any scheme, and one naming an app's own screen must not reach it from a tap.
 */
public fun openInBrowser(context: Context, url: String) {
    if (!isWeb(url)) return
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
    } catch (_: ActivityNotFoundException) {
        // no browser on the device: nothing can show the page
    }
}

private fun isWeb(url: String): Boolean = url.toUri().scheme?.lowercase() in setOf("http", "https")

private fun share(context: Context, url: String) {
    if (!isWeb(url)) return
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, null))
}

private fun copy(context: Context, url: String) {
    if (!isWeb(url)) return
    context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(url, url))
}

/**
 * Asks before one's own post is deleted, since it cannot be undone: then [onDelete] deletes it, or,
 * for a redraft, [onRedraft] opens the composer that deletes it and writes it again. Either way the
 * dialog then goes, through [onDismiss].
 */
@Composable
public fun DeleteStatusDialog(
    request: DeleteRequest,
    onDelete: (statusId: String) -> Unit,
    onRedraft: (statusId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val redraft = request.redraft
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (redraft) R.string.status_redraft_title else R.string.status_delete_title))
        },
        text = {
            Text(stringResource(if (redraft) R.string.status_redraft_body else R.string.status_delete_body))
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if (redraft) onRedraft(request.row.statusId) else onDelete(request.row.statusId)
            }) {
                Text(stringResource(if (redraft) R.string.status_redraft_confirm else R.string.status_delete_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.status_cancel)) } },
    )
}
