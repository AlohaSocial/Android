// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import com.google.mlkit.common.MlKit
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import social.aloha.core.data.di.ApplicationScope
import social.aloha.core.intelligence.Drafted
import social.aloha.core.intelligence.LanguageModel
import social.aloha.core.intelligence.ModelAvailability
import social.aloha.core.model.LogArea
import timber.log.Timber

/** ML Kit started on first use rather than at launch: with every feature off, it does nothing at all. */
internal object MlKitStart {
    @Volatile
    private var started = false

    fun ensure(context: Context) {
        if (started) return
        synchronized(this) {
            if (!started) MlKit.initialize(context)
            started = true
        }
    }
}

/** Gemini Nano through ML Kit's Prompt API, on a device that runs it: the model and what it reads stay there. */
@Singleton
internal class GeminiNano @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : LanguageModel {
    private val client by lazy {
        MlKitStart.ensure(context)
        Generation.getClient()
    }
    private var fetching: Job? = null

    override suspend fun availability(): ModelAvailability = when (status()) {
        FeatureStatus.AVAILABLE -> ModelAvailability.Available
        FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> ModelAvailability.NotReady
        else -> ModelAvailability.NotEligible
    }

    override fun prepare() {
        if (fetching?.isActive == true) return
        fetching = scope.launch {
            try {
                client.download().collect { status ->
                    if (status is DownloadStatus.DownloadFailed) failed(status.e)
                }
            } catch (e: GenAiException) {
                failed(e)
            }
        }
    }

    override suspend fun generate(instructions: String, prompt: String): Drafted = try {
        val request = if (client.isSystemPromptAvailable()) {
            GenerateContentRequest.Builder(SystemInstruction(instructions), TextPart(prompt)).build()
        } else {
            GenerateContentRequest.Builder(TextPart(instructions + "\n\n" + prompt)).build()
        }
        val answer = client.generateContent(request).candidates.firstOrNull()
        when {
            answer?.text.isNullOrBlank() -> Drafted.Refused

            // cut off where the model ran out of room: the end of the post would be lost
            answer.finishReason == Candidate.FinishReason.MAX_TOKENS -> Drafted.TooLong

            else -> Drafted.Text(answer.text.trim())
        }
    } catch (e: GenAiException) {
        Timber.tag(LogArea.App.name).w("On-device model answered nothing: %d", e.errorCode)
        // what the model would not write comes back as a failure to generate
        if (e.errorCode == GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR) Drafted.Refused else Drafted.Failed
    }

    override suspend fun name(): String? = try {
        client.getBaseModelName().takeIf { it.isNotBlank() }
    } catch (_: GenAiException) {
        null
    }

    private suspend fun status(): Int = try {
        client.checkStatus()
    } catch (_: GenAiException) {
        FeatureStatus.UNAVAILABLE
    }

    private fun failed(e: GenAiException) {
        Timber.tag(LogArea.App.name).w("On-device model not fetched: %d", e.errorCode)
    }
}

/** The Play build's Google part of the on-device features; the generic build binds none. */
@Module
@InstallIn(SingletonComponent::class)
internal interface OnDeviceModule {
    @Binds
    fun model(nano: GeminiNano): LanguageModel
}
