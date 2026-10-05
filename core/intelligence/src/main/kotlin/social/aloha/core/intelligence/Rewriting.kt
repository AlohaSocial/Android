// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.intelligence

import androidx.annotation.StringRes

/** How a draft is rewritten: corrected, made shorter, said another way, or in another tone. */
public enum class RewriteStyle(@param:StringRes public val label: Int, internal val instruction: String) {
    Proofread(
        R.string.intelligence_proofread,
        "Fix spelling, grammar and punctuation. Change nothing else: not tone, not word choice, not length.",
    ),
    Shorten(R.string.intelligence_shorten, "Reduce the length to under %d characters, keeping the meaning."),
    Rephrase(R.string.intelligence_rephrase, "Say the same thing in different words."),
    Friendlier(R.string.intelligence_friendlier, "Make the tone warmer and friendlier without changing the meaning."),
    Formal(R.string.intelligence_formal, "Make the tone more formal without changing the meaning."),
    Concise(R.string.intelligence_concise, "Make it more concise without losing anything the writer said."),
}

internal object Rewriting {
    const val SUMMARY = "Summarise a conversation for someone deciding whether to read it. " +
        "Two or three sentences. Neutral. Do not invent anything that is not there."

    // a thread past this is summarised from its start; summaries of its parts would reach further
    private const val SUMMARY_CHARACTERS = 6_000

    suspend fun LanguageModel.rewrite(
        text: String,
        style: RewriteStyle,
        limit: Int,
        fits: (String) -> Boolean,
    ): Drafted {
        val shield = EntityShield()
        if (!shield.accepts(text)) return Drafted.Failed
        val masked = shield.mask(text)
        val instructions = "You rewrite social media posts. " + style.instruction.format(limit) + "\n" +
            "Tokens like ⟦0⟧ stand for mentions, hashtags and links: reproduce every one of them exactly, " +
            "unchanged, and add no others.\nAnswer with the rewritten text alone and nothing else."
        val answer = generate(instructions, masked)
        val restored = (answer as? Drafted.Text)?.text?.takeIf(shield::isIntact)?.let(shield::restore)
        return when {
            answer !is Drafted.Text -> answer

            // a mention, hashtag or link written out in plain text by the model counts as much as a lost one
            restored == null || EntityShield.entities(restored) != EntityShield.entities(text) -> Drafted.Failed

            !fits(restored) -> Drafted.TooLong

            else -> Drafted.Text(restored)
        }
    }

    /** The passages joined, as many from the start as the model is given. */
    fun within(passages: List<String>): String =
        passages.take(reach(passages)).joinToString("\n\n").take(SUMMARY_CHARACTERS)

    /** How many passages from the start fit what the model is given; the first always does, cut short. */
    fun reach(passages: List<String>): Int {
        var length = 0
        return passages.takeWhile { passage ->
            length += passage.length + 2
            length <= SUMMARY_CHARACTERS
        }.size.coerceAtLeast(minOf(1, passages.size))
    }
}
