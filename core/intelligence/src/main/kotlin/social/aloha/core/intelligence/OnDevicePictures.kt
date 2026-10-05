// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.intelligence

import android.content.Context
import android.graphics.Bitmap
import android.media.FaceDetector
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import social.aloha.core.media.decodeScaled

/**
 * What a picture shows, read on the device by open software that sends nothing anywhere: what is in it
 * from an open image classifier (EfficientNet-Lite0 on LiteRT), and how many faces from Android's own
 * detector, never whose.
 */
@Singleton
internal class OnDevicePictures @Inject constructor(@ApplicationContext private val context: Context) : PictureReader {
    private val classifying = Mutex()
    private val interpreter by lazy { Interpreter(asset(MODEL)) }
    private val labels by lazy { context.assets.open(LABELS).bufferedReader().use { it.readLines() } }

    override suspend fun observe(file: File): Observations? = withContext(Dispatchers.Default) {
        val picture = decodeScaled(file, EDGE) ?: return@withContext null
        Observations(subjects(picture), faces(picture))
    }

    private suspend fun subjects(picture: Bitmap): List<String> = classifying.withLock {
        try {
            val side = interpreter.getInputTensor(0).shape()[1]
            val output = Array(1) { ByteArray(labels.size) }
            interpreter.run(pixels(picture.scale(side, side)), output)
            val quantised = interpreter.getOutputTensor(0).quantizationParams()
            val scores = FloatArray(labels.size) {
                ((output[0][it].toInt() and BYTE) - quantised.zeroPoint) *
                    quantised.scale
            }
            likely(scores, labels)
        } catch (_: IllegalArgumentException) {
            // a device the classifier cannot run on gets no subjects, and the rest of the description stands
            emptyList()
        }
    }

    private fun faces(picture: Bitmap): Int {
        // the platform's detector wants an even width and 16-bit pixels
        val width = minOf(picture.width, FACE_EDGE) / 2 * 2
        val height = picture.height * width / picture.width
        if (width < 2 || height < 2) return 0
        val faces = picture.scale(width, height).copy(Bitmap.Config.RGB_565, false)
        return FaceDetector(width, height, MAX_FACES).findFaces(faces, arrayOfNulls(MAX_FACES))
    }

    private fun asset(name: String): ByteBuffer = try {
        val bytes = context.assets.open(name).use { it.readBytes() }
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
    } catch (e: IOException) {
        throw IllegalArgumentException("Model $name missing", e)
    }

    private companion object {
        const val MODEL = "efficientnet_lite0.tflite"
        const val LABELS = "efficientnet_lite0_labels.txt"
        const val EDGE = 2048
        const val FACE_EDGE = 1024
        const val MAX_FACES = 20
        const val BYTE = 0xFF
    }
}

/** The picture's pixels as the classifier takes them: red, green and blue bytes, row after row. */
private fun pixels(picture: Bitmap): ByteBuffer {
    val colours = IntArray(picture.width * picture.height)
    picture.getPixels(colours, 0, picture.width, 0, 0, picture.width, picture.height)
    val buffer = ByteBuffer.allocateDirect(colours.size * CHANNELS).order(ByteOrder.nativeOrder())
    colours.forEach { colour ->
        buffer.put((colour shr RED).toByte()).put((colour shr GREEN).toByte()).put(colour.toByte())
    }
    return buffer.also { it.rewind() }
}

/** The labels the classifier is at least fairly sure of, surest first. */
internal fun likely(scores: FloatArray, labels: List<String>): List<String> = scores.indices
    .filter { scores[it] >= CONFIDENCE }
    .sortedByDescending { scores[it] }
    .take(SUBJECTS)
    .map { labels[it] }

private const val CHANNELS = 3
private const val RED = 16
private const val GREEN = 8
private const val CONFIDENCE = 0.35f
private const val SUBJECTS = 6
