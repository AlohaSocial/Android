// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import social.aloha.core.data.Answer
import social.aloha.core.data.compose.MediaRepository
import social.aloha.core.data.compose.mimeTypeOf
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.SignedInAccount
import social.aloha.core.sync.LocalMedia
import social.aloha.core.sync.MediaUploads
import social.aloha.core.sync.UploadState

/** The focal point as Mastodon defines it: `x,y` in −1…1 from the centre, `y` pointing up. */
@Immutable
internal data class Focus(val x: Float, val y: Float)

/** A picture, video or file attached to one post of the thread, and where its upload stands. */
@Immutable
internal data class Attachment(
    val id: String,
    /** The app's copy of the file; none for one the server made itself, a GIF or a Nextcloud file. */
    val file: File?,
    val fileName: String,
    val mimeType: String,
    val upload: UploadState = UploadState.Queued,
    val description: String = "",
    val focus: Focus? = null,
    /** What the server has, so only what changed is sent before posting. */
    val sentDescription: String = "",
    val sentFocus: Focus? = null,
    /** The picture as it was picked, kept on disk so a second filter starts from it, not the first. */
    val original: File? = null,
    val filter: PhotoFilter = PhotoFilter.Original,
    /** A video over the server's ceiling, not uploaded until trimmed or scaled under it. */
    val oversizedLimit: Long? = null,
    /** A trim, scale or filter is being made; the upload follows. */
    val preparing: Boolean = false,
    /**
     * Part of the post being edited already: its description and focus go with the edit, as the
     * server changes attached media no other way.
     */
    val attached: Boolean = false,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")

    /** What was picked, before any filter or trim: where an edit starts from, when it is on this device. */
    val source: File? get() = original ?: file

    /** The server's own preview, for an attachment that never was a file here. */
    val previewUrl: String? get() = (upload as? UploadState.Done)?.previewUrl

    /** A GIF or animated WebP takes no filter; drawn through one it would lose its motion. */
    val filterable: Boolean get() = file != null && isPicture && mimeType !in MediaPreparation.UNFILTERABLE

    val isPicture: Boolean get() = mimeType.startsWith("image/")
    val mediaId: String? get() = (upload as? UploadState.Done)?.mediaId
}

/** Why an attachment could not be added. */
internal sealed interface AttachFailure {
    data class Unsupported(val mimeType: String) : AttachFailure

    data class TooLarge(val limitBytes: Long) : AttachFailure

    /** The file could not be read from where it was picked. */
    data object Unreadable : AttachFailure

    /** The server could not make the GIF or find the Nextcloud file asked for. */
    data object NotFound : AttachFailure

    /** The clipboard holds no picture or video. */
    data object NothingToPaste : AttachFailure
}

/**
 * The attachments of each post in the thread. A picked file is copied into the app, checked against
 * the server's limits and uploaded in the background at once, so it is usually there by the time the
 * text is; a description or focal point set meanwhile is sent before the post that carries it.
 */
internal class Attachments(
    private val uploads: MediaUploads,
    private val media: MediaRepository,
    private val preparation: MediaPreparation,
    private val scope: CoroutineScope,
) {
    private val all = MutableStateFlow<List<List<Attachment>>>(listOf(emptyList()))
    val byPost: StateFlow<List<List<Attachment>>> = all.asStateFlow()

    /** Why the last attachment could not be added; the composer clears it once said. */
    val failure = MutableStateFlow<AttachFailure?>(null)

    /** Whether the media carry a warning of their own, whatever the text says. */
    val sensitive = MutableStateFlow(false)

    /** Who the attachments upload as; the composer's current author. */
    var account: SignedInAccount? = null

    private val watching = HashMap<String, Job>()
    private val works = HashMap<String, UUID>()

    /** Attaches [uris] to post [segment] of the thread, up to [room] of them. */
    fun add(segment: Int, uris: List<Uri>, room: Int) {
        val account = account ?: return
        scope.launch {
            uris.take(room).forEach { uri ->
                val limits = account.capabilities.limits
                val copied = withContext(Dispatchers.IO) { preparation.copy(uri) }
                val prepared = copied?.let { preparation.prepare(it, limits) }
                val check = prepared?.check
                when {
                    prepared == null -> failure.value = AttachFailure.Unreadable

                    check == Preflight.Fits -> start(account, segment, prepared.picked)

                    // too long or too large a video waits in the strip for the writer to trim it
                    check is Preflight.TooLarge && prepared.picked.mimeType.startsWith("video/") -> all.update {
                        it.appended(
                            segment,
                            Attachment(
                                UUID.randomUUID().toString(),
                                prepared.picked.file,
                                prepared.picked.fileName,
                                prepared.picked.mimeType,
                                oversizedLimit = check.limitBytes,
                            ),
                        )
                    }

                    // refused, the copy goes: nothing else would ever delete it
                    else -> {
                        prepared.picked.file.delete()
                        failure.value = check?.toFailure() ?: AttachFailure.Unreadable
                    }
                }
            }
        }
    }

    /**
     * Puts [attachment] at the end of post [segment] as it is: one on the server already (a GIF, a
     * Nextcloud file, one a draft kept), or one whose file is uploaded now.
     */
    fun put(segment: Int, attachment: Attachment) {
        val account = account
        val file = attachment.file
        if (attachment.mediaId != null) {
            all.update { it.appended(segment, attachment) }
        } else if (account != null && file != null && file.exists()) {
            start(account, segment, Picked(file, attachment.fileName, attachment.mimeType), attachment, isNew = true)
        }
    }

    /** The thread gained or lost a post: [count] lists, the removed post's files let go. */
    fun resize(count: Int, removed: Int? = null) {
        removed?.let { index -> all.value.getOrNull(index)?.forEach { release(it, forGood = true) } }
        all.update { lists ->
            val kept = if (removed == null) lists else lists.filterIndexed { i, _ -> i != removed }
            kept.take(count) + List((count - kept.size).coerceAtLeast(0)) { emptyList() }
        }
    }

    fun remove(id: String) {
        get(id)?.let { release(it, forGood = true) }
        all.update { lists -> lists.map { list -> list.filterNot { it.id == id } } }
    }

    fun describe(id: String, description: String, focus: Focus?) = change(id) {
        it.copy(description = description.take(DESCRIPTION_LIMIT), focus = focus)
    }

    /**
     * Uploads [picked] in [id]'s place, keeping its place in the strip, and changes it with [change]:
     * no route replaces the bytes behind a media id, so an edit after uploading is a second upload,
     * and the first is let go for the server's own sweep of unattached media. The file [id] had is
     * deleted unless [keepFile]. The description goes again, before posting.
     */
    fun replace(id: String, picked: Picked, keepFile: Boolean, change: (Attachment) -> Attachment) {
        val account = account ?: return
        val attachment = get(id) ?: return
        val segment = all.value.indexOfFirst { list -> list.any { it.id == id } }
        release(attachment, keepFile = keepFile)
        val next = change(attachment).copy(
            file = picked.file,
            fileName = picked.fileName,
            mimeType = picked.mimeType,
            sentDescription = "",
            oversizedLimit = null,
            preparing = false,
        )
        start(account, segment, picked, next)
    }

    /** Attaches [picked], already fit for the server, to post [segment] with [description]; its id. */
    fun addPrepared(segment: Int, picked: Picked, description: String): String? {
        val account = account ?: return null
        val attachment = Attachment(
            UUID.randomUUID().toString(),
            picked.file,
            picked.fileName,
            picked.mimeType,
            description = description,
            sentDescription = description,
        )
        start(account, segment, picked, attachment, isNew = true)
        return attachment.id
    }

    /** Uploads [id] again, after a failure. */
    fun retry(id: String) {
        val account = account ?: return
        val attachment = get(id) ?: return
        val file = attachment.file ?: return
        val segment = all.value.indexOfFirst { list -> list.any { it.id == id } }
        release(attachment, keepFile = true)
        val again = attachment.copy(sentDescription = "")
        start(account, segment, Picked(file, attachment.fileName, attachment.mimeType), again)
    }

    /**
     * Sends every description and focal point the server does not have yet, as [account]; false when
     * something is not uploaded yet or one could not be sent, which leaves the post unsent rather than
     * posted without it or with the wrong text.
     */
    suspend fun sync(account: SignedInAccount): Boolean = all.value.flatten().all { it.mediaId != null } &&
        all.value.flatten().filter(::unsent).all { attachment ->
            val sent = media.describe(
                account,
                checkNotNull(attachment.mediaId),
                attachment.description,
                attachment.focus?.let { it.x to it.y },
            ) is Answer.Got
            if (sent) change(attachment.id) { it.copy(sentDescription = it.description, sentFocus = it.focus) }
            sent
        }

    /** Lets go of everything: the uploads still running and, unless a draft [keepFiles], the files. */
    fun clear(keepFiles: Boolean = false) {
        all.value.flatten().forEach { release(it, keepFile = keepFiles, forGood = !keepFiles) }
        all.value = listOf(emptyList())
    }

    /**
     * Uploads [picked] as [before] (a new attachment when null), in its place when it is being
     * uploaded again, at the end of post [segment] when [isNew]; a description it already has goes
     * with the upload.
     */
    private fun start(
        account: SignedInAccount,
        segment: Int,
        picked: Picked,
        before: Attachment? = null,
        isNew: Boolean = before == null,
    ) {
        val attachment = before?.copy(upload = UploadState.Queued, sentFocus = null)
            ?: Attachment(UUID.randomUUID().toString(), picked.file, picked.fileName, picked.mimeType)
        val description = attachment.sentDescription.takeIf { isNew && it.isNotEmpty() }
        val work = uploads.enqueue(account, LocalMedia(picked.file, picked.fileName, picked.mimeType, description))
        works[attachment.id] = work
        // one uploaded again keeps its place in the strip; a new one goes at the end
        if (isNew) {
            all.update { it.appended(segment, attachment) }
        } else {
            all.update { lists -> lists.map { list -> list.map { if (it.id == attachment.id) attachment else it } } }
        }
        watching[attachment.id] = scope.launch {
            uploads.observe(work).collect { state -> change(attachment.id) { it.copy(upload = state) } }
        }
    }

    /**
     * Lets go of [attachment]: its upload while unfinished and, unless [keepFile], its file; [forGood]
     * also deletes the original a filter started from.
     */
    private fun release(attachment: Attachment, keepFile: Boolean = false, forGood: Boolean = false) {
        watching.remove(attachment.id)?.cancel()
        works.remove(attachment.id)?.let { if (attachment.mediaId == null) uploads.cancel(it) }
        if (!keepFile) attachment.file?.delete()
        if (forGood) attachment.original?.takeIf { it != attachment.file }?.delete()
    }

    operator fun get(id: String): Attachment? = all.value.flatten().firstOrNull { it.id == id }

    fun change(id: String, change: (Attachment) -> Attachment) {
        all.update { lists -> lists.map { list -> list.map { if (it.id == id) change(it) else it } } }
    }

    companion object {
        /** Mastodon's own limit on a description. */
        const val DESCRIPTION_LIMIT = 1500

        /** [bytes] as a person reads a size: "40 MB". */
        fun size(bytes: Long): String = String.format(Locale.getDefault(), "%.0f MB", bytes / MEGABYTE)

        private const val MEGABYTE = 1024.0 * 1024.0
    }
}

/** Whether [attachment] has a description or focal point the server does not have yet. */
private fun unsent(attachment: Attachment) = attachment.mediaId != null && !attachment.attached &&
    (attachment.description != attachment.sentDescription || attachment.focus != attachment.sentFocus)

private fun Preflight.toFailure(): AttachFailure = when (this) {
    is Preflight.Unsupported -> AttachFailure.Unsupported(mimeType)
    is Preflight.TooLarge -> AttachFailure.TooLarge(limitBytes)
    else -> AttachFailure.Unreadable
}

/** [media] the server made itself from a GIF or a Nextcloud file, named [name]. */
internal fun remoteAttachment(media: MediaAttachment, name: String) = Attachment(
    UUID.randomUUID().toString(),
    file = null,
    fileName = name,
    mimeType = mimeTypeOf(media),
    upload = UploadState.Done(media.id, media.previewUrl ?: media.url),
    description = media.description.orEmpty(),
    sentDescription = media.description.orEmpty(),
)

/** [attachment] at the end of post [segment]'s list; the first post's when there is no such post. */
private fun List<List<Attachment>>.appended(segment: Int, attachment: Attachment): List<List<Attachment>> =
    mapIndexed { index, list -> if (index == segment.coerceAtLeast(0)) list + attachment else list }
