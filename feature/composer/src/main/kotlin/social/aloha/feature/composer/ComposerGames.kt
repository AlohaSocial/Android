// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

/**
 * The three games a post can play, the same ones Nextcloud Social's web composer offers, so a post
 * says the same thing whichever app wrote it: `/dice` (or `/roll`, with an optional number of sides),
 * `/flip`, and `/pick` followed by its options. Each is replaced by its result just before the post
 * is sent, so what travels is plain text and a reader on any server sees what a reader here sees;
 * nothing can be rolled again afterwards. A command counts only at the start of a line or after
 * whitespace, and only when its name ends where a word would, so a link that happens to contain
 * `/dice` stays a link.
 */
internal object ComposerGames {
    enum class Kind { Dice, Flip, Pick }

    /** How the results read, in the language the post is written in. */
    class Words(val heads: String, val tails: String, val picked: (choice: String, options: String) -> String)

    const val MAX_SIDES = 1000
    const val MAX_OPTIONS = 20

    /** [text] with every game replaced by its result; [random] answers in `[0, 1)`. */
    fun play(text: String, words: Words, random: () -> Double = Math::random): String {
        val out = StringBuilder()
        var from = 0
        for (game in games(text)) {
            out.append(text, from, game.start).append(result(game, words, random))
            from = game.end
        }
        return out.append(text, from, text.length).toString()
    }

    /** The kinds of game [text] plays, in the order they first appear, for the hint under the box. */
    fun kinds(text: String): List<Kind> = games(text).map(Game::kind).distinct()

    /** The options `/pick` chooses from: comma-separated, or split on " or " when there is no comma. */
    fun options(rest: String): List<String> {
        val line = rest.trim()
        if (line.isEmpty()) return emptyList()
        val parts = if (',' in line) line.split(',') else line.split(orSeparator)
        return parts.map(String::trim).filter(String::isNotEmpty).take(MAX_OPTIONS)
    }

    private class Game(
        val kind: Kind,
        val start: Int,
        val end: Int,
        val sides: Int = DEFAULT_SIDES,
        val options: List<String> = emptyList(),
    )

    private val command = Regex("""/(dice|roll|flip|pick)""", RegexOption.IGNORE_CASE)
    private val sidesGiven = Regex("""^\s+[dD]?(\d{1,4})(?!\d)""")
    private val orSeparator = Regex("""\s+or\s+""", RegexOption.IGNORE_CASE)

    private fun result(game: Game, words: Words, random: () -> Double): String = when (game.kind) {
        Kind.Dice -> {
            val face = (random() * game.sides).toInt() + 1
            if (game.sides == DEFAULT_SIDES) "🎲 $face" else "🎲 $face (d${game.sides})"
        }

        Kind.Flip -> "🪙 " + if (random() < HALF) words.heads else words.tails

        Kind.Pick -> {
            val choice = game.options[(random() * game.options.size).toInt().coerceAtMost(game.options.lastIndex)]
            "🎯 " + words.picked(choice, game.options.joinToString(", "))
        }
    }

    /** Every game in [text], in order; a later one never starts inside an earlier one's text. */
    private fun games(text: String): List<Game> = buildList {
        var search = 0
        while (true) {
            val match = command.find(text, search) ?: break
            val start = match.range.first
            val game = if (start == 0 || text[start - 1].isWhitespace()) gameAt(text, match) else null
            if (game != null) add(game)
            search = game?.end ?: (match.range.last + 1)
        }
    }

    private fun gameAt(text: String, match: MatchResult): Game? {
        val start = match.range.first
        val end = match.range.last + 1
        return when (match.groupValues[1].lowercase()) {
            "flip" -> Game(Kind.Flip, start, end).takeIf { endsWord(text, end) }

            "pick" -> {
                val lineEnd = text.indexOf('\n', end).takeIf { it >= 0 } ?: text.length
                val options = options(text.substring(end, lineEnd))
                Game(Kind.Pick, start, lineEnd, options = options).takeIf { endsWord(text, end) && options.size >= 2 }
            }

            else -> {
                val given = sidesGiven.find(text.substring(end))
                val stop = end + (given?.value?.length ?: 0)
                val sides = given?.groupValues?.get(1)?.toInt()?.coerceIn(MIN_SIDES, MAX_SIDES) ?: DEFAULT_SIDES
                Game(Kind.Dice, start, stop, sides = sides).takeIf { endsWord(text, end) && endsWord(text, stop) }
            }
        }
    }

    // `/dice/` or `/flipped` is not a game: the name, and a die's sides, must end where a word would
    private fun endsWord(text: String, end: Int): Boolean =
        end >= text.length || !(text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '/')

    private const val DEFAULT_SIDES = 6
    private const val MIN_SIDES = 2
    private const val HALF = 0.5
}
