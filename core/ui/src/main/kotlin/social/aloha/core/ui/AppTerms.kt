// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import social.aloha.core.designsystem.AlohaSpacing

/** The app's terms of use, as accepted before the first account and read again from Settings, About. */
@Composable
public fun AppTerms(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AlohaSpacing.l)) {
        listOf(R.string.app_terms_body, R.string.app_terms_conduct, R.string.app_terms_tools).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
