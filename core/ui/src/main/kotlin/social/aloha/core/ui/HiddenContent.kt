// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.WarningReveal

/** The edge of a card that hides something: warning stripes for a content warning, dots for a filter. */
public enum class HiddenEdge { Warning, Filter }

/**
 * A card standing in for what it hides: [text] with an [icon], and [action] at its end, with a 5 dp
 * textured edge on both sides so it never reads as an ordinary row.
 */
@Composable
internal fun HiddenCard(
    edge: HiddenEdge,
    icon: ImageVector,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.hiddenEdges(edge).padding(horizontal = EDGE + AlohaSpacing.s, vertical = AlohaSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(SMALL_ICON))
            text()
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Draws [edge]'s texture down both sides, [EDGE] wide. */
@Composable
internal fun Modifier.hiddenEdges(edge: HiddenEdge): Modifier {
    val ink = when (edge) {
        HiddenEdge.Warning -> MaterialTheme.colorScheme.tertiary
        HiddenEdge.Filter -> MaterialTheme.colorScheme.outline
    }
    return drawBehind {
        val width = EDGE.toPx()
        listOf(0f, size.width - width).forEach { left ->
            clipRect(left, 0f, left + width, size.height) {
                when (edge) {
                    HiddenEdge.Warning -> stripes(left, width, ink)
                    HiddenEdge.Filter -> dots(left, width, ink)
                }
            }
        }
    }
}

private fun DrawScope.stripes(left: Float, width: Float, ink: Color) {
    val step = width * 2
    var y = -width
    while (y < size.height + width) {
        val band = Path().apply {
            moveTo(left, y + width)
            lineTo(left + width, y)
            lineTo(left + width, y + width)
            lineTo(left, y + 2 * width)
            close()
        }
        drawPath(band, ink)
        y += step
    }
}

private fun DrawScope.dots(left: Float, width: Float, ink: Color) {
    val radius = width / DOT_FRACTION
    var y = width / 2
    while (y < size.height) {
        drawCircle(ink, radius, Offset(left + width / 2, y))
        y += width
    }
}

/**
 * Content warnings opened in a thread, so another post behind the same warning opens with them, as
 * Reading's [WarningReveal] allows: never, for the same author, or for anyone. "re: " prefixes and case
 * do not make two warnings different.
 */
@Stable
public class WarningReveals(private val mode: WarningReveal) {
    private val opened = mutableStateListOf<Pair<String, String>>()

    public fun isOpen(authorId: String, warning: String): Boolean {
        val text = normalised(warning)
        return when (mode) {
            WarningReveal.Never -> false
            WarningReveal.SameAuthor -> (authorId to text) in opened
            WarningReveal.Everyone -> opened.any { it.second == text }
        }
    }

    public fun opened(authorId: String, warning: String) {
        if (mode != WarningReveal.Never) opened += authorId to normalised(warning)
    }

    public fun closed(authorId: String, warning: String) {
        val text = normalised(warning)
        opened.removeAll { it.second == text && (mode == WarningReveal.Everyone || it.first == authorId) }
    }

    private companion object {
        val RE = Regex("^(\\s*re:\\s*)+", RegexOption.IGNORE_CASE)

        fun normalised(warning: String) = warning.replace(RE, "").trim().lowercase()
    }
}

/** The thread's shared warnings; null outside a thread, where every post opens on its own. */
public val LocalWarningReveals: ProvidableCompositionLocal<WarningReveals?> = staticCompositionLocalOf { null }

/** [body] with every case-insensitive occurrence of [words] painted in [style]: a filtered post shown anyway. */
internal fun highlighted(body: AnnotatedString, words: List<String>, style: SpanStyle): AnnotatedString {
    val ranges = words.filter { it.isNotBlank() }.flatMap { word ->
        Regex(Regex.escape(word), RegexOption.IGNORE_CASE).findAll(body.text).map { it.range }.toList()
    }
    if (ranges.isEmpty()) return body
    return buildAnnotatedString {
        append(body)
        ranges.forEach { addStyle(style, it.first, it.last + 1) }
    }
}

/**
 * [content] at its full height, or, while [collapsed] and taller than [COLLAPSE_ABOVE], clipped to
 * [COLLAPSED_HEIGHT] with its last [FADE] fading out, and [toggle] under it once it is that tall: both
 * settled in one layout pass, so the row never grows a frame after it appears. [onTall] says whether it
 * is that tall, and [tall] is what it said last; only a tall, collapsed text pays for the fade.
 */
@Composable
internal fun Collapsible(
    collapsed: Boolean,
    tall: Boolean,
    onTall: (Boolean) -> Unit,
    content: @Composable () -> Unit,
    toggle: @Composable () -> Unit,
) {
    val fading = if (tall && collapsed) Modifier.fadingBottom() else Modifier
    // the text's own measure tells, within the same pass, whether it is tall
    var isTall = false
    val clipped = Modifier.clipToBounds().then(fading).layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
        isTall = placeable.height > COLLAPSE_ABOVE.roundToPx()
        val height = if (isTall && collapsed) COLLAPSED_HEIGHT.roundToPx() else placeable.height
        layout(placeable.width, height) { placeable.place(0, 0) }
    }
    Layout(contents = listOf({ Box(clipped) { content() } }, toggle)) { (text, button), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val shown = text.first().measure(loose)
        if (isTall != tall) onTall(isTall)
        val under = if (isTall) button.first().measure(loose) else null
        layout(maxOf(shown.width, under?.width ?: 0), shown.height + (under?.height ?: 0)) {
            shown.place(0, 0)
            under?.place(0, shown.height)
        }
    }
}

/** The last [FADE] of what is drawn fading out, where a collapsed text is cut. */
private fun Modifier.fadingBottom(): Modifier = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = FADE.toPx()
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fade),
            size = Size(size.width, fade),
            blendMode = BlendMode.DstIn,
        )
    }

/** A long post's text: whether it may collapse, whether it is tall enough to, and whether it is open. */
internal data class Collapse(
    val collapsible: Boolean = false,
    val tall: Boolean = false,
    val onTall: (Boolean) -> Unit = {},
    val expanded: Boolean = false,
    val onExpand: () -> Unit = {},
)

/** What a card holds back, and how the reader shows or hides it again. */
internal data class Hiding(
    val filterRevealed: Boolean,
    val onFilterReveal: () -> Unit,
    val onFilterHide: () -> Unit,
    val spoilerRevealed: Boolean,
    val onSpoiler: () -> Unit,
    val collapse: Collapse,
) {
    /** The header's eye, once the reader has opened what was hidden: hides it again. */
    fun rehide(row: StatusRowUi): (() -> Unit)? = when {
        row.spoiler != null && spoilerRevealed -> onSpoiler
        row.filterWarning != null -> onFilterHide
        else -> null
    }
}

/**
 * The card's filter, content warning and collapse, each kept through scrolling and refresh by the row's
 * id; a content warning also opens as the thread's [LocalWarningReveals] says.
 */
@Composable
internal fun rememberHiding(row: StatusRowUi, focused: Boolean): Hiding {
    var filterRevealed by rememberSaveable(row.rowId) { mutableStateOf(false) }
    var ownSpoiler by rememberSaveable(row.rowId) { mutableStateOf(false) }
    var expanded by rememberSaveable(row.rowId) { mutableStateOf(false) }
    var tall by remember(row.rowId) { mutableStateOf(false) }
    val reveals = LocalWarningReveals.current
    val warning = row.spoiler?.text
    val spoilerRevealed = ownSpoiler || (warning != null && reveals?.isOpen(row.author.id, warning) == true)
    return Hiding(
        filterRevealed = filterRevealed,
        onFilterReveal = { filterRevealed = true },
        onFilterHide = { filterRevealed = false },
        spoilerRevealed = spoilerRevealed,
        onSpoiler = {
            ownSpoiler = !spoilerRevealed
            if (warning != null) {
                if (spoilerRevealed) {
                    reveals?.closed(
                        row.author.id,
                        warning,
                    )
                } else {
                    reveals?.opened(row.author.id, warning)
                }
            }
        },
        collapse = Collapse(
            collapsible = LocalReadingStyle.current.collapseLong && row.spoiler == null && !focused,
            tall = tall,
            onTall = { if (it != tall) tall = it },
            expanded = expanded,
            onExpand = { expanded = !expanded },
        ),
    )
}

internal val EDGE = 5.dp
private const val DOT_FRACTION = 4
private val COLLAPSE_ABOVE = 220.dp
private val COLLAPSED_HEIGHT = 145.dp
private val FADE = 36.dp
