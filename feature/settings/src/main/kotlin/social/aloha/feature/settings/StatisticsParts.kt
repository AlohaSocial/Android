// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.NamedCount

/** One section of the statistics: a heading, when it has one, over what it counts. */
@Composable
internal fun StatisticsCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(AlohaSpacing.m), verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            content()
        }
    }
}

/** Numbers side by side, as many to a row as fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Tiles(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) { content() }
}

/** One number and what it counts, read out as one. */
@Composable
internal fun Tile(value: String, label: String) {
    Column(
        Modifier.widthIn(min = TILE)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .padding(AlohaSpacing.s)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A bar a month, in [series] stacked bottom up with their colours, and the first and last month under
 * them. A screen reader hears [spoken] instead, which says the numbers.
 */
@Composable
internal fun MonthBars(months: List<String>, series: List<Pair<Color, Map<String, Double>>>, spoken: String) {
    val most = months.maxOfOrNull { month -> series.sumOf { it.second[month] ?: 0.0 } }?.takeIf { it > 0 } ?: return
    Column(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Row(
            Modifier.fillMaxWidth().height(CHART),
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEach { month ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    series.asReversed().forEach { (colour, values) ->
                        val share = ((values[month] ?: 0.0) / most).toFloat()
                        // a height of the chart's own, not a share of what the segments below left over
                        if (share > 0f) Box(Modifier.fillMaxWidth().height(CHART * share).background(colour))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = AlohaSpacing.xxs), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(monthLabel(months.first()), style = MaterialTheme.typography.labelSmall)
            Text(monthLabel(months.last()), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** What the colours of stacked bars stand for. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Legend(entries: List<Pair<Color, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.m)) {
        entries.forEach { (colour, name) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(KEY).background(colour, MaterialTheme.shapes.extraSmall))
                Text(name, Modifier.padding(start = AlohaSpacing.xs), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A name and how often, one to a line, the most first; at most [limit] of them. */
@Composable
internal fun CountRows(rows: List<NamedCount>, prefix: String = "", limit: Int = NAMED_ROWS) {
    rows.take(limit).forEach { row -> ValueRow(prefix + row.name, whole(row.count.toDouble())) }
}

/** A line with a name at the start and its number at the end, read out together. */
@Composable
internal fun ValueRow(name: String, value: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

internal fun whole(value: Double): String = NumberFormat.getIntegerInstance().format(Math.round(value))

internal fun oneDecimal(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
    minimumFractionDigits = 1
}.format(value)

/** [value] as the server sends a share, 0 to 100, written as a percentage. */
internal fun percent(value: Double): String =
    NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(value / HUNDRED)

/** `2026-03` as the reader's language writes a month and year; anything else as it came. */
internal fun monthLabel(key: String): String = try {
    YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMM yy"))
} catch (_: DateTimeParseException) {
    key
}

private val TILE = 120.dp
private val CHART = 96.dp
private val KEY = 10.dp
private const val NAMED_ROWS = 12
private const val HUNDRED = 100.0
