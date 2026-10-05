// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.Visibility

/** An action's icon that also answers a long press, the size of an icon button. */
@Composable
internal fun PressableIcon(icon: ImageVector, tint: Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(TARGET)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactions,
                indication = ripple(),
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .squish(interactions),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint) }
}

/** The other accounts a post can be acted on as, where the app offers acting as another one. */
@Composable
private fun others(row: StatusRowUi): Pair<ActAs, List<AccountChoice>>? {
    val actAs = LocalActAs.current ?: return null
    if (row.url == null) return null
    return (actAs to actAs.accounts.filter { it.id != actAs.current }).takeIf { it.second.isNotEmpty() }
}

/** [content], whose long press does [act] as another account, chosen in a sheet titled [title]. */
@Composable
internal fun ActAsAction(
    row: StatusRowUi,
    act: PostAct,
    title: Int,
    content: @Composable (onLongClick: (() -> Unit)?) -> Unit,
) {
    val (actAs, accounts) = others(row) ?: return content(null)
    var picking by remember { mutableStateOf(false) }
    content { picking = true }
    if (picking) {
        AccountPicker(stringResource(title), accounts, onPick = {
            picking = false
            actAs.act(it.id, row.url.orEmpty(), act)
        }, onDismiss = { picking = false })
    }
}

/**
 * A boost's long press: boosted for whom (the account's default checked), undone where it is boosted,
 * boosted as another account, or quoted.
 */
@Composable
internal fun BoostWithMenu(row: StatusRowUi, actions: StatusActions, open: Boolean, onClose: () -> Unit) {
    val actAs = LocalActAs.current ?: return
    val url = row.url ?: return
    val others = others(row)?.second.orEmpty()
    var picking by remember { mutableStateOf(false) }
    DropdownMenu(expanded = open, onDismissRequest = onClose) {
        // boosted already, it can only be undone
        if (!row.state.boosted) BoostFor(actAs, url, onClose)
        if (row.state.boosted) {
            MenuItem(R.string.status_action_unboost, AlohaIcons.Boosted) {
                onClose()
                actions.onBoost(row)
            }
        }
        if (others.isNotEmpty()) {
            MenuItem(R.string.status_boost_as, AlohaIcons.Boost) {
                onClose()
                picking = true
            }
        }
        if (actions.quotes && row.quoteAccess != QuoteAccess.Denied) {
            MenuItem(quoteLabel(row.quoteAccess), AlohaIcons.Quote) {
                onClose()
                actions.onQuote(row)
            }
        }
    }
    if (picking) {
        AccountPicker(stringResource(R.string.status_boost_as), others, onPick = {
            picking = false
            actAs.act(it.id, url, PostAct.Boost())
        }, onDismiss = { picking = false })
    }
}

/** For whom a boost goes out, the account's default checked, each one boosting at once. */
@Composable
private fun BoostFor(actAs: ActAs, url: String, onClose: () -> Unit) {
    Text(
        stringResource(R.string.status_boost_with),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
    )
    BOOSTS.forEach { (visibility, label) ->
        val chosen = visibility == actAs.defaultVisibility
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            leadingIcon = { RadioButton(chosen, onClick = null) },
            modifier = Modifier.semantics { selected = chosen },
            onClick = {
                onClose()
                actAs.act(actAs.current.orEmpty(), url, PostAct.Boost(visibility))
            },
        )
    }
    HorizontalDivider()
}

/** What a screen reader asked to do as another account: the picker's title, and the act once one is picked. */
internal data class AskedAs(val title: Int, val act: PostAct)

/**
 * A screen reader's way to what the action bar's long presses do: boosting for a narrower audience at once,
 * and replying, boosting, favouriting or bookmarking as another account, whose picker [onAsk] opens.
 */
@Composable
internal fun actAsActions(row: StatusRowUi, onAsk: (AskedAs) -> Unit): List<CustomAccessibilityAction> {
    val actAs = LocalActAs.current ?: return emptyList()
    val url = row.url ?: return emptyList()
    val narrower = if (row.state.boosted) {
        emptyList()
    } else {
        NARROWER.map { (visibility, label) ->
            CustomAccessibilityAction(stringResource(label)) {
                actAs.act(actAs.current.orEmpty(), url, PostAct.Boost(visibility))
                true
            }
        }
    }
    val asOthers = if (others(row) == null) {
        emptyList()
    } else {
        AS_OTHERS.map { (label, asked) ->
            CustomAccessibilityAction(stringResource(label)) {
                onAsk(asked)
                true
            }
        }
    }
    return narrower + asOthers
}

/** The accounts [asked] can be done as, until one is picked or the picker is put away. */
@Composable
internal fun ActAsPicker(row: StatusRowUi, asked: AskedAs, onDone: () -> Unit) {
    val (actAs, accounts) = others(row) ?: return
    AccountPicker(stringResource(asked.title), accounts, onPick = {
        onDone()
        actAs.act(it.id, row.url.orEmpty(), asked.act)
    }, onDismiss = onDone)
}

private val NARROWER = listOf(
    Visibility.Unlisted to R.string.status_boost_for_unlisted,
    Visibility.Private to R.string.status_boost_for_followers,
)

private val AS_OTHERS = listOf(
    R.string.status_reply_as_other to AskedAs(R.string.status_reply_as, PostAct.Reply),
    R.string.status_boost_as to AskedAs(R.string.status_boost_as, PostAct.Boost()),
    R.string.status_favourite_as_other to AskedAs(R.string.status_favourite_as, PostAct.Favourite),
    R.string.status_bookmark_as_other to AskedAs(R.string.status_bookmark_as, PostAct.Bookmark),
)

@Composable
private fun MenuItem(label: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

private val BOOSTS = listOf(
    Visibility.Public to R.string.status_boost_public,
    Visibility.Unlisted to R.string.status_boost_unlisted,
    Visibility.Private to R.string.status_boost_private,
)

private val TARGET = 48.dp
