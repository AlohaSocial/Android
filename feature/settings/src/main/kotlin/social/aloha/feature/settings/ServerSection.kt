// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.server.ServerAbout
import social.aloha.core.data.server.ServerInfo
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.InstanceActivityWeek
import social.aloha.core.model.InstanceDocument
import social.aloha.core.ui.ProvideLinkRouting
import social.aloha.core.ui.RichLinkTarget
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.fullDate
import social.aloha.core.ui.openInBrowser
import social.aloha.core.ui.toAnnotatedString

/** The server of the account in use, and what it publishes about itself; null while it is asked. */
@Immutable
internal data class ServerState(val host: String = "", val about: ServerAbout? = null)

@HiltViewModel
internal class ServerViewModel @Inject constructor(accounts: AccountRepository, info: ServerInfo) : ViewModel() {
    private val loaded = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = loaded.asStateFlow()

    init {
        viewModelScope.launch {
            val reader = accounts.activeAccount.filterNotNull().first()
            loaded.value = ServerState(reader.host)
            loaded.value = ServerState(reader.host, info.about(reader))
        }
    }
}

internal object ServerSection : SettingsSection {
    override val key: String = "server"
    override val order: Int = 990
    override val title: Int = R.string.server_title
    override val icon: ImageVector = AlohaIcons.Rules

    @Composable
    override fun Content() {
        val viewModel: ServerViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        ServerContent(state)
    }
}

/** The pages the server's administrator published; one never written is not listed. */
private enum class ServerPage { Rules, Description, Privacy, Terms, Activity, Peers, Blocks }

@Composable
internal fun ServerContent(state: ServerState) {
    var page by rememberSaveable { mutableStateOf<ServerPage?>(null) }
    val about = state.about
    Column {
        Text(
            stringResource(R.string.server_host, state.host),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
        )
        if (about == null) {
            Text(
                stringResource(R.string.server_loading),
                modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
            )
            return@Column
        }
        pagesOf(about).forEach { (which, title) ->
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) { page = which },
                headlineContent = { Text(stringResource(title)) },
            )
        }
    }
    if (about != null) page?.let { ServerPageView(it, about) { page = null } }
}

/** Each page the server published, with its title. */
private fun pagesOf(about: ServerAbout): List<Pair<ServerPage, Int>> = listOfNotNull(
    about.rules?.let { ServerPage.Rules to R.string.server_rules },
    about.description?.let { ServerPage.Description to R.string.server_description },
    about.privacyPolicy?.let { ServerPage.Privacy to R.string.server_privacy },
    about.termsOfService?.let { ServerPage.Terms to R.string.server_terms },
    about.activity?.let { ServerPage.Activity to R.string.server_activity },
    about.peers?.let { ServerPage.Peers to R.string.server_peers },
    about.domainBlocks?.let { ServerPage.Blocks to R.string.server_blocks },
)

@Composable
private fun ServerPageView(page: ServerPage, about: ServerAbout, onClose: () -> Unit) {
    when (page) {
        ServerPage.Rules -> TextPage(stringResource(R.string.server_rules), onClose) { Rules(about) }

        ServerPage.Description -> DocumentPage(R.string.server_description, about.description, onClose)

        ServerPage.Privacy -> DocumentPage(R.string.server_privacy, about.privacyPolicy, onClose)

        ServerPage.Terms -> DocumentPage(R.string.server_terms, about.termsOfService, onClose)

        ServerPage.Activity -> ListPage(R.string.server_activity, about.activity.orEmpty(), onClose) { Week(it) }

        ServerPage.Peers -> ListPage(R.string.server_peers, about.peers.orEmpty(), onClose) {
            ListItem(headlineContent = { Text(it) })
        }

        ServerPage.Blocks -> ListPage(R.string.server_blocks, about.domainBlocks.orEmpty(), onClose) { block ->
            ListItem(
                headlineContent = { Text(block.domain) },
                supportingContent = {
                    val how = stringResource(
                        if (block.severity ==
                            "silence"
                        ) {
                            R.string.server_block_limited
                        } else {
                            R.string.server_block_blocked
                        },
                    )
                    Text(listOf(how, block.comment).filter { it.isNotBlank() }.joinToString(" · "))
                },
            )
        }
    }
}

@Composable
private fun Rules(about: ServerAbout) {
    Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m)) {
        about.rules.orEmpty().forEachIndexed { index, rule ->
            Column {
                Text("${index + 1}. ${rule.text}", style = MaterialTheme.typography.bodyLarge)
                rule.hint?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A page the administrator wrote, in its own markup; its links open in the browser. */
@Composable
private fun DocumentPage(title: Int, document: InstanceDocument?, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = RichTextColors.fromTheme()
    val text = remember(document, colors) {
        StatusHtmlParser.parse(document?.content.orEmpty()).toAnnotatedString(colors)
    }
    TextPage(stringResource(title), onClose) {
        ProvideLinkRouting(onLink = { target ->
            if (target is RichLinkTarget.Web) openInBrowser(context, target.url)
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m)) {
                document?.updatedAt?.let {
                    Text(
                        stringResource(R.string.server_updated, fullDate(it)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun <T> ListPage(title: Int, items: List<T>, onClose: () -> Unit, row: @Composable (T) -> Unit) {
    AboutPage(stringResource(title), onClose) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            items(items) {
                row(it)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Week(week: InstanceActivityWeek) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.server_week, fullDate(week.startsAt))) },
        supportingContent = {
            Text(
                listOf(
                    pluralStringResource(R.plurals.server_week_posts, week.statuses, week.statuses),
                    pluralStringResource(R.plurals.server_week_logins, week.logins, week.logins),
                    pluralStringResource(R.plurals.server_week_signups, week.registrations, week.registrations),
                ).joinToString(" · "),
            )
        },
    )
}
