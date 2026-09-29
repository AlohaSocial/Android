// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import social.aloha.core.designsystem.AlohaSpacing

/**
 * One section of the settings. A feature contributes its own with Hilt's `@IntoSet`, so the settings
 * screen lists whatever sections the app holds without knowing them; [order] places it, lowest first.
 */
public interface SettingsSection {
    /** Stable and unique: it names the section in navigation and state. */
    public val key: String

    public val order: Int

    @get:StringRes
    public val title: Int

    public val icon: ImageVector

    /** The section's rows; they read and write through the DataStores themselves. */
    @Composable
    public fun Content()
}

/**
 * A setting that is on or off. The whole row is the control: one element for a screen reader, which
 * reads the title, the summary and the state, and one target for a tap.
 */
@Composable
public fun SwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit, summary: String? = null) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

/** One setting chosen among a few, each a row of its own under a heading. */
@Composable
public fun <T> ChoiceRows(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics {
                heading()
            }.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
        )
        options.forEach { (value, label) ->
            ListItem(
                modifier = Modifier.selectable(selected = value == selected, role = Role.RadioButton) {
                    onSelect(value)
                },
                headlineContent = { Text(label) },
                leadingContent = { RadioButton(selected = value == selected, onClick = null) },
            )
        }
    }
}
