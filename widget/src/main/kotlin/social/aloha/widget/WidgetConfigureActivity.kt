// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.designsystem.AlohaTheme
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineSource

/**
 * Where a widget being placed is told whose it is, and, for Latest posts, which timeline. Leaving
 * without a choice places nothing.
 */
public class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className
        val latest = provider == LatestPostsWidgetReceiver::class.java.name
        enableEdgeToEdge()
        lifecycleScope.launch {
            val accounts = widgetEntryPoint().accounts().all()
            setContent {
                AlohaTheme { ConfigureScreen(accounts, latest) { account, source -> save(id, account, source) } }
            }
        }
    }

    private fun save(appWidgetId: Int, accountId: String, source: TimelineSource?) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigureActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigureActivity, glanceId) { settings ->
                settings[WidgetKeys.ACCOUNT] = accountId
                source?.let { settings[WidgetKeys.SOURCE] = it.storageKey }
            }
            widgetEntryPoint().widgets().redraw()
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigureScreen(
    accounts: List<SignedInAccount>,
    chooseSource: Boolean,
    onDone: (accountId: String, source: TimelineSource?) -> Unit,
) {
    var chosen by remember { mutableStateOf(accounts.singleOrNull()?.id) }
    var source by remember { mutableStateOf<TimelineSource>(TimelineSource.Home) }
    val title = stringResource(R.string.widget_configure_title)
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Heading(stringResource(R.string.widget_configure_account))
            Column(Modifier.selectableGroup()) {
                accounts.forEach { account ->
                    Choice(
                        account.displayName.ifBlank { account.qualifiedHandle },
                        account.qualifiedHandle,
                        chosen == account.id,
                    ) {
                        chosen = account.id
                    }
                }
            }
            if (chooseSource) {
                Heading(stringResource(R.string.widget_configure_timeline))
                Column(Modifier.selectableGroup()) {
                    WIDGET_SOURCES.forEach { option ->
                        Choice(stringResource(option.label), null, source == option) { source = option }
                    }
                }
            }
            Button(
                onClick = { chosen?.let { onDone(it, if (chooseSource) source else null) } },
                enabled = chosen != null,
                modifier = Modifier.fillMaxWidth().padding(AlohaSpacing.m),
            ) { Text(stringResource(R.string.widget_configure_done)) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s).semantics { heading() },
    )
}

@Composable
private fun Choice(title: String, summary: String?, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
    )
}

private val TimelineSource.label: Int
    get() = when (this) {
        TimelineSource.Local -> R.string.widget_source_local
        TimelineSource.Federated -> R.string.widget_source_federated
        else -> R.string.widget_source_home
    }
