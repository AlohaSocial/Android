// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * [values], oldest first, as a line across a small box, the highest at its top; a flat line where all are
 * equal. It says nothing to a screen reader: the numbers it draws are said beside it.
 */
@Composable
public fun Sparkline(
    values: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (values.size < 2) return
    Canvas(modifier.size(WIDTH, HEIGHT)) {
        val stroke = STROKE.toPx()
        val highest = values.max().coerceAtLeast(1)
        val step = (size.width - stroke) / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = stroke / 2 + index * step
            val y = stroke / 2 + (size.height - stroke) * (1f - value.toFloat() / highest)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val WIDTH = 64.dp
private val HEIGHT = 24.dp
private val STROKE = 2.dp
