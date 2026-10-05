// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.intelligence

import android.content.Context
import androidx.annotation.StringRes
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Whether the device's language model can be used now. */
public enum class ModelAvailability {
    Available,

    /** The device can run it and it is still to come down; [Intelligence.prepare] fetches it. */
    NotReady,

    /** No model on this device or in this build: rewriting and summaries are not offered at all. */
    NotEligible,
}

/** What a generation came to: the text, or why there is none. */
public sealed interface Drafted {
    public data class Text(val text: String) : Drafted

    /** The model declined; said plainly, never asked again. */
    public data object Refused : Drafted

    /** The result would not fit where it is to go. */
    public data object TooLong : Drafted

    public data object Failed : Drafted
}

/** The device's language model, where the build has one. */
public interface LanguageModel {
    public suspend fun availability(): ModelAvailability

    /** Starts fetching the model where the device can fetch it, and returns at once. */
    public fun prepare()

    /** The model's answer to [prompt] under [instructions]; [Drafted.Refused] or [Drafted.Failed] without one. */
    public suspend fun generate(instructions: String, prompt: String): Drafted

    /** Which model the device runs, as it names it; null when it does not say. */
    public suspend fun name(): String? = null
}

/** Reads a picture on the device. */
public interface PictureReader {
    /** What [file] shows; null when it cannot be read. */
    public suspend fun observe(file: File): Observations?
}

/** What a picture shows as far as the device can tell: deliberately coarse, and people only as a count. */
public data class Observations(val subjects: List<String> = emptyList(), val people: Int = 0) {
    val isEmpty: Boolean get() = subjects.isEmpty() && people == 0
}

/** What a build says, in the privacy statement, about the on-device features it has and who learns of them. */
public class PrivacyNotice(@param:StringRes public val text: Int)

/**
 * The on-device features: alt text drafted from what a picture shows, in every build; a draft rewritten
 * and a thread summarised, where the build has a language model. None sends what it reads anywhere.
 */
@Singleton
public class Intelligence @Inject constructor(
    @ApplicationContext private val context: Context,
    private val model: Optional<LanguageModel>,
    private val reader: Optional<PictureReader>,
    private val notice: Optional<PrivacyNotice> = Optional.empty(),
) {
    /** Whether the build offers any of it: the settings section and the privacy note show only then. */
    public val offered: Boolean get() = model.isPresent || reader.isPresent

    public val describesPictures: Boolean get() = reader.isPresent

    /** Whether the build has a language model at all; whether this device can run it is [availability]. */
    public val hasModel: Boolean get() = model.isPresent

    /** The build's own paragraph for the privacy statement. */
    @get:StringRes
    public val privacy: Int? get() = notice.getOrNull()?.text

    /** What alt text drafted here ends with while it reads as drafted; the writer's first edit drops it. */
    public val altTextMark: String get() = " " + context.getString(R.string.intelligence_generated)

    public suspend fun availability(): ModelAvailability =
        model.getOrNull()?.availability() ?: ModelAvailability.NotEligible

    /**
     * Whether a feature that needs the model can be offered: while [on] is false the model is not asked
     * anything; once it is, each collection asks it again, so a model fetched since counts.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun ready(on: Flow<Boolean>): Flow<Boolean> = on.distinctUntilChanged().flatMapLatest { wanted ->
        if (wanted) flow { emit(availability() == ModelAvailability.Available) } else flowOf(false)
    }

    public fun prepare() {
        model.getOrNull()?.prepare()
    }

    /** The name the device gives its language model; ask only once a feature that needs it is on. */
    public suspend fun modelName(): String? = model.getOrNull()?.name()

    /** Alt text for [file], from what the device recognises in it, ending with [altTextMark]. */
    public suspend fun describe(file: File): Drafted {
        val seen = reader.getOrNull()?.observe(file)?.takeUnless { it.isEmpty } ?: return Drafted.Failed
        return Drafted.Text(AltText.assemble(seen, context.resources) + altTextMark)
    }

    /** [text] rewritten in [style], its mentions, hashtags and links unchanged, and only what [fits]. */
    public suspend fun rewrite(text: String, style: RewriteStyle, limit: Int, fits: (String) -> Boolean): Drafted {
        val model = model.getOrNull() ?: return Drafted.Failed
        return with(Rewriting) { model.rewrite(text, style, limit, fits) }
    }

    /** A few neutral sentences on [passages], each a post with its author, in order. */
    public suspend fun summarise(passages: List<String>): Drafted {
        val model = model.getOrNull() ?: return Drafted.Failed
        return model.generate(Rewriting.SUMMARY, Rewriting.within(passages))
    }
}

/** Every build reads pictures with the open reader; it binds a [LanguageModel] and [PrivacyNotice] if it has one. */
@Module
@InstallIn(SingletonComponent::class)
internal interface IntelligenceModule {
    @BindsOptionalOf
    fun model(): LanguageModel

    @BindsOptionalOf
    fun reader(): PictureReader

    @BindsOptionalOf
    fun notice(): PrivacyNotice

    @Binds
    fun pictures(open: OnDevicePictures): PictureReader
}
