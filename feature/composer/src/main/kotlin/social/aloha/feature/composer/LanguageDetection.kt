// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The language a text reads as, by the device's own text classifier, on the device; null where it is less
 * than [SURE] of it, the text is too short to tell, or the device has no classifier for it.
 */
internal class LanguageDetection(private val context: Context) {
    suspend fun detect(text: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || text.length < SHORTEST) return null
        return withContext(Dispatchers.Default) {
            val classifier = context.getSystemService(TextClassificationManager::class.java)?.textClassifier
            val found = classifier?.detectLanguage(TextLanguage.Request.Builder(text).build())
            // hypotheses come most likely first
            found?.takeIf { it.localeHypothesisCount > 0 }?.getLocale(0)
                ?.takeIf { found.getConfidenceScore(it) >= SURE }?.language?.ifEmpty { null }
        }
    }

    private companion object {
        const val SHORTEST = 20
        const val SURE = 0.75f
    }
}
