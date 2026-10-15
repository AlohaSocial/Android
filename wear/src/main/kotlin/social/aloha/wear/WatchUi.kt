// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/** A screen's title at the top of its list, announced as a heading. */
@Composable
internal fun TransformingLazyColumnItemScope.Heading(spec: TransformationSpec, title: String) {
    ListHeader(
        Modifier.transformedHeight(this, spec).semantics {
            heading()
        },
        transformation = SurfaceTransformation(spec),
    ) {
        Text(title)
    }
}

/** A full-width button that shrinks and fades at the round screen's edge as the list scrolls. */
@Composable
internal fun TransformingLazyColumnItemScope.WideButton(spec: TransformationSpec, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(label)
    }
}

/** Centred text kept off the round screen's rim, where a line's ends would meet the edge and the scroll indicator. */
@Composable
internal fun Centred(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = TEXT_INSET), textAlign = TextAlign.Center)
}

private val TEXT_INSET = 14.dp
