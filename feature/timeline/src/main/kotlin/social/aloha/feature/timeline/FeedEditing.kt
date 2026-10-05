// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.PinnedFeed
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.SwitchRow
import social.aloha.core.ui.dragHandle
import social.aloha.core.ui.moves
import social.aloha.core.ui.rememberHaptics
import social.aloha.core.ui.rememberReordering
import social.aloha.core.ui.reorderItem

/**
 * The feeds in order. A feed's handle drags it up and down, and it is kept where it is let go; a screen
 * reader moves it with the row's actions instead. A swipe removes it, where [onRemove] allows one, and the
 * feed is gone at once; Following, which Home always holds, cannot be removed.
 */
@Composable
internal fun FeedList(
    feeds: List<PinnedFeed>,
    modifier: Modifier,
    onOrder: (List<PinnedFeed>) -> Unit,
    onRemove: ((PinnedFeed) -> Unit)?,
    onEdit: (PinnedFeed) -> Unit,
) {
    val reordering = rememberReordering(feeds, key = { it.id }, onOrder = onOrder)
    // gone as soon as it is swiped away, not when the stored feeds catch up
    var gone by remember(feeds) { mutableStateOf(emptySet<String>()) }
    val removing = onRemove?.let { remove ->
        { feed: PinnedFeed ->
            gone = gone + feed.id
            remove(feed)
        }
    }
    // read here, not in the list's content, which is built again only when what it reads changes
    val order = reordering.order.filterNot { it.id in gone }
    LazyColumn(modifier.fillMaxSize()) {
        itemsIndexed(order, key = { _, feed -> feed.id }) { index, feed ->
            val moves = reordering.moves(index)
            val removable = removing.takeUnless { feed.kind == PinnedFeed.Kind.Following }
            // a swipe's way for a screen reader
            val remove = removable?.let { removing ->
                CustomAccessibilityAction(stringResource(R.string.feeds_remove)) {
                    removing(feed)
                    true
                }
            }
            Column(Modifier.reorderItem(reordering, feed)) {
                RemovableRow(feed, removable) {
                    FeedRow(
                        feed,
                        Modifier.semantics { customActions = moves + listOfNotNull(remove) },
                        Modifier.dragHandle(reordering, feed),
                        onEdit,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * [content], swiped either way to remove [feed], a trash can showing under it and a tick under the finger
 * once it is far enough, as a post's swipe ticks; as it is where it cannot be removed.
 */
@Composable
private fun RemovableRow(feed: PinnedFeed, onRemove: ((PinnedFeed) -> Unit)?, content: @Composable () -> Unit) {
    if (onRemove == null) return content()
    // not saved with the list: a feed put back would come back swiped away, and be removed again
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val swipe = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val haptics = rememberHaptics()
    LaunchedEffect(swipe) {
        snapshotFlow { swipe.targetValue != SwipeToDismissBoxValue.Settled }
            .distinctUntilChanged()
            .filter { it }
            .collect { haptics(HapticFeedbackType.GestureThresholdActivate) }
    }
    SwipeToDismissBox(
        swipe,
        backgroundContent = {
            val end = swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(AlohaSpacing.m),
                contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Icon(AlohaIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
        onDismiss = { onRemove(feed) },
    ) { content() }
}

/** A feed's row: its handle, its icon and name, what kind of feed it is. */
@Composable
private fun FeedRow(feed: PinnedFeed, modifier: Modifier, handle: Modifier, onEdit: (PinnedFeed) -> Unit) {
    ListItem(
        modifier = modifier.clickable(onClickLabel = stringResource(R.string.feeds_change)) { onEdit(feed) },
        leadingContent = {
            // the row's Move up and Move down say what the handle does
            Icon(AlohaIcons.Reorder, contentDescription = null, handle.minimumInteractiveComponentSize())
        },
        headlineContent = { Text(feedName(feed)) },
        supportingContent = if (feed.name != null) ({ Text(kindName(feed.kind)) }) else null,
        trailingContent = { Icon(feedIcon(feed), contentDescription = null) },
    )
}

/**
 * A feed's name and icon, and for a hashtag its tag and, under Advanced, the tags it reads with it.
 * [feed] with an empty tag is a hashtag being added.
 */
@Composable
internal fun FeedDialog(feed: PinnedFeed, onDismiss: () -> Unit, onSave: (PinnedFeed) -> Unit) {
    var name by rememberSaveable { mutableStateOf(feed.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(feed.icon) }
    val tags = (feed.kind as? PinnedFeed.Kind.Hashtag)?.tags
    var tag by rememberSaveable { mutableStateOf(tags?.let { TagQuery.of(it) }) }
    val adding = tags?.name?.isEmpty() == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (adding) R.string.feeds_add_tag else R.string.feeds_change)) },
        text = {
            Column {
                tag?.let { query -> TagFields(query, adding) { tag = it } }
                OutlinedTextField(
                    name,
                    { name = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.feeds_name)) },
                    placeholder = if (adding) null else ({ Text(kindName(feed.kind)) }),
                    singleLine = true,
                )
                IconPicker(icon) { icon = it }
            }
        },
        confirmButton = {
            val kind = tag?.let { PinnedFeed.Kind.Hashtag(it.source()) } ?: feed.kind
            TextButton(
                onClick = { onSave(PinnedFeed(kind, name.trim().ifEmpty { null }, icon)) },
                enabled = tag?.name?.isNotBlank() ?: true,
            ) { Text(stringResource(R.string.feeds_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeds_cancel)) } },
    )
}

/** The icons a feed can take; a tap on the chosen one goes back to the kind's own. */
@Composable
private fun IconPicker(icon: String?, onIcon: (String?) -> Unit) {
    FlowRow(Modifier.padding(top = AlohaSpacing.s)) {
        FeedIcons.forEach { (key, image) ->
            IconToggleButton(checked = icon == key, onCheckedChange = { onIcon(key.takeIf { key != icon }) }) {
                Icon(image, stringResource(iconLabel(key)), Modifier.alpha(if (icon == key) 1f else DIM))
            }
        }
    }
}

/** The bar's +: the feeds that could still be pinned, by group, and a hashtag not followed. */
@Composable
internal fun AddMenu(
    addable: List<Pair<Int, List<PinnedFeed>>>,
    onAdd: (PinnedFeed) -> Unit,
    onOtherTag: () -> Unit,
    onServer: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AlohaIcons.Add, stringResource(R.string.feeds_add)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            addable.forEach { (group, feeds) ->
                Text(
                    stringResource(group),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
                )
                feeds.forEach { feed ->
                    DropdownMenuItem(
                        text = { Text(feedName(feed)) },
                        leadingIcon = { Icon(feedIcon(feed), contentDescription = null) },
                        onClick = {
                            open = false
                            onAdd(feed)
                        },
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feeds_add_tag)) },
                leadingIcon = { Icon(AlohaIcons.Hashtag, contentDescription = null) },
                onClick = {
                    open = false
                    onOtherTag()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feeds_add_server)) },
                leadingIcon = { Icon(AlohaIcons.Language, contentDescription = null) },
                onClick = {
                    open = false
                    onServer()
                },
            )
        }
    }
}

/** A hashtag feed's tags as typed: words split on spaces and commas, a leading # dropped. */
internal data class TagQuery(
    val name: String,
    val any: String = "",
    val all: String = "",
    val none: String = "",
    val localOnly: Boolean = false,
) : java.io.Serializable {
    fun source(): TimelineSource.Hashtag =
        TimelineSource.Hashtag(name.trim().removePrefix("#"), words(any), words(all), words(none), localOnly)

    companion object {
        fun of(tags: TimelineSource.Hashtag): TagQuery = TagQuery(
            tags.name,
            tags.any.joinToString(" "),
            tags.all.joinToString(" "),
            tags.none.joinToString(" "),
            tags.localOnly,
        )

        fun words(typed: String): List<String> =
            typed.split(' ', ',').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
    }
}

/** The tag a feed reads and, behind Advanced, the tags it reads with it and whether this server alone. */
@Composable
private fun TagFields(query: TagQuery, editableName: Boolean, onChange: (TagQuery) -> Unit) {
    var advanced by rememberSaveable { mutableStateOf(query.any + query.all + query.none != "" || query.localOnly) }
    if (editableName) {
        OutlinedTextField(
            query.name,
            { onChange(query.copy(name = it)) },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feeds_tag)) },
            singleLine = true,
        )
    }
    TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.feeds_advanced)) }
    if (!advanced) return
    listOf(
        R.string.feeds_tags_any to query.any,
        R.string.feeds_tags_all to query.all,
        R.string.feeds_tags_none to query.none,
    ).forEach { (label, value) ->
        OutlinedTextField(
            value,
            { typed ->
                onChange(
                    when (label) {
                        R.string.feeds_tags_any -> query.copy(any = typed)
                        R.string.feeds_tags_all -> query.copy(all = typed)
                        else -> query.copy(none = typed)
                    },
                )
            },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(label)) },
            supportingText = { Text(stringResource(R.string.feeds_tags_hint)) },
            singleLine = true,
        )
    }
    SwitchRow(stringResource(R.string.feeds_local_only), query.localOnly, { onChange(query.copy(localOnly = it)) })
}

/** Another server's own public posts as a feed, asked for by its address. */
@Composable
internal fun ServerDialog(onDismiss: () -> Unit, onAdd: (PinnedFeed) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    val domain = domainOf(address)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_add_server)) },
        text = {
            OutlinedTextField(
                address,
                { address = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.feeds_server)) },
                supportingText = { Text(stringResource(R.string.feeds_server_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { domain?.let { onAdd(PinnedFeed(PinnedFeed.Kind.Remote(it))) } },
                enabled =
                    domain != null,
            ) {
                Text(stringResource(R.string.feeds_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeds_cancel)) } },
    )
}

/** The domain in what was typed: an address or a bare name, without its scheme, path or an @ handle. */
internal fun domainOf(typed: String): String? = typed.trim().substringAfter("://").substringBefore('/')
    .substringAfterLast('@').lowercase().takeIf { it.contains('.') && ' ' !in it }

private const val DIM = 0.5f
