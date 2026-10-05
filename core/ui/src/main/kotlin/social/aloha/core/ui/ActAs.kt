// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Visibility

/** What a post's long press does as one of the reader's accounts. */
public sealed interface PostAct {
    public data object Reply : PostAct

    public data object Favourite : PostAct

    public data object Bookmark : PostAct

    /** A boost seen by [visibility], or by the account's default where null. */
    public data class Boost(val visibility: Visibility? = null) : PostAct
}

/** One signed-in account as a sheet choosing one lists it, with its unread notifications. */
@Immutable
public data class AccountChoice(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String?,
    val unread: Int = 0,
)

/**
 * Acting on a post as one of the reader's accounts, [current] or another of [accounts]: the app finds
 * the post on that account's server first. A boost goes out by [defaultVisibility] unless chosen.
 */
public interface ActAs {
    public val accounts: List<AccountChoice>
    public val current: String?
    public val defaultVisibility: Visibility get() = Visibility.Public

    public fun act(accountId: String, url: String, act: PostAct)
}

/** The app's way of acting as another account; null where a screen offers none, such as a preview. */
public val LocalActAs: ProvidableCompositionLocal<ActAs?> = staticCompositionLocalOf { null }

/** The accounts [title] chooses among, each with its face, name, handle and a dot for unread notifications. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AccountPicker(
    title: String,
    accounts: List<AccountChoice>,
    onPick: (AccountChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s).semantics { heading() },
        )
        val unread = stringResource(R.string.accounts_unread)
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(accounts, key = { it.id }) { account ->
                ListItem(
                    modifier = Modifier
                        .clickable { onPick(account) }
                        .semantics { if (account.unread > 0) stateDescription = unread },
                    leadingContent = { Avatar(account.avatarUrl, FACE) },
                    headlineContent = { Text(account.name) },
                    supportingContent = { Text(account.handle) },
                    trailingContent = if (account.unread > 0) ({ Badge() }) else null,
                )
            }
        }
    }
}

private val FACE = 40.dp
