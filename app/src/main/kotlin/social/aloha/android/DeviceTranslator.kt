// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.icu.util.ULocale
import android.os.Build
import android.os.CancellationSignal
import android.view.translation.TranslationCapability
import android.view.translation.TranslationContext
import android.view.translation.TranslationManager
import android.view.translation.TranslationRequest
import android.view.translation.TranslationRequestValue
import android.view.translation.TranslationResponse
import android.view.translation.TranslationSpec
import androidx.annotation.RequiresApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import social.aloha.core.data.translation.primaryLanguage

/** Translation on the device itself, where the text goes nowhere. */
interface DeviceTranslator {
    /** Each language pair (primary subtags, from → into) the device can translate, and whether it is ready. */
    suspend fun pairs(): Map<Pair<String, String>, Boolean>

    /** [text] from [from] into [into]; null when the device could not. */
    suspend fun translate(text: String, from: String, into: String): String?

    /** Opens the system's settings where translation languages are downloaded. */
    fun openLanguages()
}

/**
 * The system's own on-device translation, from Android 12: a device's translation service (on Pixels,
 * Android System Intelligence) serves it, with no library in the app and nothing sent off the device.
 * Where there is no service, or before Android 12, it can translate nothing.
 */
@Singleton
class SystemTranslator @Inject constructor(@ApplicationContext private val context: Context) : DeviceTranslator {
    private val system: Api31? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Api31(context) else null

    override suspend fun pairs(): Map<Pair<String, String>, Boolean> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) system?.pairs().orEmpty() else emptyMap()
    }

    override suspend fun translate(text: String, from: String, into: String): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) system?.translate(text, from, into) else null

    override fun openLanguages() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) system?.openLanguages()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class Api31(private val context: Context) {
        private val manager = context.getSystemService(TranslationManager::class.java)
        private val executor: Executor = Dispatchers.Default.asExecutor()

        fun pairs(): Map<Pair<String, String>, Boolean> = try {
            manager?.getOnDeviceTranslationCapabilities(TEXT, TEXT)
                .orEmpty()
                .filter { it.state != TranslationCapability.STATE_NOT_AVAILABLE }
                .associate {
                    val from = primaryLanguage(it.sourceSpec.locale.language)
                    (from to primaryLanguage(it.targetSpec.locale.language)) to
                        (it.state == TranslationCapability.STATE_ON_DEVICE)
                }
        } catch (_: SecurityException) {
            // a translation service that refuses this app offers it nothing
            emptyMap()
        }

        suspend fun translate(text: String, from: String, into: String): String? {
            val manager = manager ?: return null
            val spec = { language: String ->
                TranslationSpec(ULocale.forLanguageTag(language), TEXT)
            }
            val translation = TranslationContext.Builder(spec(from), spec(into)).build()
            return suspendCancellableCoroutine { done ->
                manager.createOnDeviceTranslator(translation, executor) { translator ->
                    if (translator == null) {
                        done.resume(null)
                        return@createOnDeviceTranslator
                    }
                    val request = TranslationRequest.Builder()
                        .setTranslationRequestValues(listOf(TranslationRequestValue.forText(text)))
                        .build()
                    val cancel = CancellationSignal()
                    done.invokeOnCancellation {
                        cancel.cancel()
                        translator.destroy()
                    }
                    translator.translate(request, cancel, executor) { response ->
                        translator.destroy()
                        done.resume(response.text())
                    }
                }
            }
        }

        fun openLanguages() {
            val settings = manager?.onDeviceTranslationSettingsActivityIntent ?: return
            try {
                settings.send(context, 0, null, null, null, null, options())
            } catch (_: PendingIntent.CanceledException) {
                // the translation service withdrew its settings page: there is nowhere to send the reader
            }
        }

        private fun TranslationResponse.text(): String? = translationResponseValues
            .takeIf { translationStatus == TranslationResponse.TRANSLATION_STATUS_SUCCESS }
            ?.get(0)
            ?.text
            ?.toString()

        // the settings page is the translation service's, opened while the reader looks at the app; Android
        // 14 and 15 know only the broader "allowed", which Android 16 deprecates for "allow if visible"
        @Suppress("DEPRECATION")
        private fun options() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
                startMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                startMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)

            else -> null
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun startMode(mode: Int) =
            ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle()

        private companion object {
            const val TEXT = TranslationSpec.DATA_FORMAT_TEXT
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface DeviceTranslatorModule {
    @Binds
    fun deviceTranslator(system: SystemTranslator): DeviceTranslator
}
