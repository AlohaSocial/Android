// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import social.aloha.core.model.CharacterCount
import social.aloha.core.sync.UploadState

/** A short post as a card: whether it is on, which background, and how it looks. */
@Immutable
internal data class CardUi(
    val on: Boolean = false,
    val background: Int = 0,
    val backgrounds: List<Int> = emptyList(),
    val preview: ImageBitmap? = null,
)

/**
 * Draws a short post big on colour: a square picture of the words, centred and as large as they fit.
 * One function draws both the preview and the picture that is uploaded, only at different sizes and
 * everything measured from the size, so the two cannot look different.
 */
internal object CardRenderer {
    fun render(text: String, background: Int, size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = textOn(background)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val margin = size * MARGIN
        val width = (size - 2 * margin).toInt()
        val room = size - 2 * margin
        var textSize = size * LARGEST
        var layout: StaticLayout
        do {
            paint.textSize = textSize
            layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, SPACING)
                .build()
            textSize *= SHRINK
        } while (layout.height > room && textSize > size * SMALLEST)
        canvas.translate(margin, (size - layout.height) / 2f)
        layout.draw(canvas)
        return bitmap
    }

    /** White or near black, whichever reads better on [background]. */
    private fun textOn(background: Int): Int {
        val white = android.graphics.Color.WHITE
        val dark = DARK_TEXT
        return if (ColorUtils.calculateContrast(white, background) >= ColorUtils.calculateContrast(dark, background)) {
            white
        } else {
            dark
        }
    }

    private const val MARGIN = 0.1f
    private const val LARGEST = 0.12f
    private const val SMALLEST = 0.04f
    private const val SHRINK = 0.92f
    private const val SPACING = 1.1f
    private const val DARK_TEXT = 0xFF1B1B1F.toInt()
}

/**
 * The card a short post can go out as. It is drawn and uploaded as an ordinary picture, not invented
 * as a new kind of post, so it federates as what it looks like; its words travel as the post, as the
 * picture's description and in the picture, so a server that only sees text and a screen reader
 * both get the post. A card that cannot be drawn stops the post rather than quietly posting plain
 * text the writer did not choose.
 */
internal class TextCards(
    private val attachments: Attachments,
    private val directory: File,
    private val scope: CoroutineScope,
) {
    private val card = MutableStateFlow(CardUi(backgrounds = FIXED))
    val state: StateFlow<CardUi> = card.asStateFlow()

    private var previewJob: Job? = null
    private var attached: Pair<String, Int>? = null
    private var attachedId: String? = null

    /** The writer's own hue, which the first background takes. */
    fun onHue(argb: Int) = card.update { it.copy(backgrounds = listOf(argb) + FIXED) }

    fun onCard(on: Boolean) {
        card.update { it.copy(on = on) }
        // turned off, the card drawn for it is no longer part of the post
        if (!on) {
            attachedId?.let(attachments::remove)
            attachedId = null
            attached = null
        }
    }

    /** The attachment the card became, which the media strip does not show as one of its own. */
    val attachmentId: String? get() = attachedId

    fun onBackground(index: Int) = card.update { it.copy(background = index.coerceIn(it.backgrounds.indices)) }

    /** Draws the preview of [text] a moment after typing stops, off the main thread. */
    fun onText(text: String) {
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(PREVIEW_DELAY_MILLIS)
            val colour = current()
            val preview = withContext(Dispatchers.Default) { CardRenderer.render(text, colour, PREVIEW) }
            card.update { it.copy(preview = preview.asImageBitmap()) }
        }
    }

    /**
     * Draws [text] full size and attaches it to the first post, waiting until the server has it;
     * false when it could not be drawn or uploaded. The same card is not uploaded twice.
     */
    suspend fun attach(text: String): Boolean {
        val wanted = text to current()
        val id = if (attached == wanted) attachedId else drawAndAttach(text, wanted)
        return id != null && settled(id) is UploadState.Done
    }

    /** Draws the card for [wanted] in place of any drawn before, and attaches it; its id. */
    private suspend fun drawAndAttach(text: String, wanted: Pair<String, Int>): String? {
        attachedId?.let(attachments::remove)
        attachedId = null
        attached = null
        val file = withContext(Dispatchers.IO) { draw(text, wanted.second) }
        val id = file?.let { attachments.addPrepared(0, Picked(it, CARD_NAME, "image/png"), text) }
        if (id != null) {
            attachedId = id
            attached = wanted
        }
        return id
    }

    /** How [id]'s upload ended, once it has: done, refused or failed. */
    private suspend fun settled(id: String): UploadState? = attachments.byPost
        .map { lists -> lists.flatten().firstOrNull { it.id == id }?.upload }
        .first { it == null || it is UploadState.Done || it is UploadState.Failed || it is UploadState.Refused }

    private fun current(): Int = card.value.let { it.backgrounds.getOrElse(it.background) { FIXED.first() } }

    private fun draw(text: String, colour: Int): File? = runCatching {
        directory.mkdirs()
        val file = File(directory, UUID.randomUUID().toString() + ".png")
        val bitmap = CardRenderer.render(text, colour, FULL)
        // PNG is lossless; the quality it is given is ignored
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 0, it) }
        file
    }.getOrNull()

    companion object {
        const val MAX_CHARACTERS = 120

        /** Whether [text] can go out as a card: short, one post, nothing attached and no games to play. */
        fun fits(text: String, segments: Int, attached: Int) = segments == 1 && attached == 0 &&
            text.isNotBlank() && CharacterCount.graphemes(text) <= MAX_CHARACTERS && ComposerGames.kinds(text).isEmpty()
        private const val FULL = 1080
        private const val PREVIEW = 720
        private const val PREVIEW_DELAY_MILLIS = 150L
        private const val CARD_NAME = "card.png"

        /** Five backgrounds after the writer's own hue; each deep enough for white words to read. */
        private val FIXED = listOf(
            0xFF00605A.toInt(),
            0xFF3A3F9F.toInt(),
            0xFF8A3A12.toInt(),
            0xFF7A2152.toInt(),
            0xFF2E3A40.toInt(),
        )
    }
}
