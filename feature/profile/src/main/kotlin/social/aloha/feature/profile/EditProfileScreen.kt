// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AccountField
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.EditProfileKey
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.SwitchRow

/** The reader's own profile to edit; [onDone] leaves it, saved or not. */
@Composable
public fun EditProfileRoute(key: EditProfileKey, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<EditProfileViewModel, EditProfileViewModel.Factory>(key = key.toString()) {
        it.create(key)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)
    val snackbars = remember { SnackbarHostState() }
    val message = state.failure?.let { failureText(it) }
    LaunchedEffect(state.saved) { if (state.saved) done() }
    LaunchedEffect(state.failure) {
        if (message != null) {
            viewModel.onFailureShown()
            snackbars.showSnackbar(message)
        }
    }
    EditProfileScreen(state, viewModel::onForm, viewModel::onSave, viewModel::onRetry, { done() }, modifier, snackbars)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileScreen(
    state: EditProfileUiState,
    onForm: (ProfileForm) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    val title = stringResource(R.string.profile_edit_title)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(AlohaIcons.Close, stringResource(R.string.profile_cancel)) }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        enabled = state.changed && !state.saving,
                        modifier = Modifier.padding(end = AlohaSpacing.s),
                    ) { Text(stringResource(R.string.profile_save)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        val form = state.form
        when {
            form != null -> Form(form, onForm, Modifier.padding(padding))

            state.loadFailed -> Column(Modifier.padding(padding).padding(AlohaSpacing.l)) {
                Text(stringResource(R.string.profile_edit_failed))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.profile_retry)) }
            }

            else -> ListProgress(Modifier.padding(padding))
        }
    }
}

@Composable
private fun Form(form: ProfileForm, onForm: (ProfileForm) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = AlohaSpacing.m),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        Pictures(form, onForm)
        OutlinedTextField(
            value = form.displayName,
            onValueChange = { onForm(form.copy(displayName = it)) },
            label = { Text(stringResource(R.string.profile_edit_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.note,
            onValueChange = { onForm(form.copy(note = it)) },
            label = { Text(stringResource(R.string.profile_edit_bio)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.profile_edit_fields),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        form.fields.forEachIndexed { index, field -> FieldRow(index, field, form, onForm) }
        Options(form, onForm)
    }
}

/** The header across, the avatar on it, each changed or removed on its own. */
@Composable
private fun Pictures(form: ProfileForm, onForm: (ProfileForm) -> Unit) {
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onForm(form.copy(avatar = Picture.Picked(it.toString()))) }
    }
    val pickHeader = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onForm(form.copy(header = Picture.Picked(it.toString()))) }
    }
    val image = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    Box(
        Modifier.fillMaxWidth().aspectRatio(HEADER_ASPECT).background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        form.header.shown?.let {
            AsyncImage(
                it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    PictureButtons(
        R.string.profile_edit_header,
        R.string.profile_edit_remove_header,
        form.header !is Picture.Kept || form.header.chosen,
        { pickHeader.launch(image) },
    ) { onForm(form.copy(header = Picture.Removed)) }
    Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.m)) {
        Avatar(form.avatar.shown, AVATAR)
        PictureButtons(
            R.string.profile_edit_avatar,
            R.string.profile_edit_remove_avatar,
            form.avatar !is Picture.Kept || form.avatar.chosen,
            { pickAvatar.launch(image) },
        ) { onForm(form.copy(avatar = Picture.Removed)) }
    }
}

@Composable
private fun PictureButtons(label: Int, remove: Int, removable: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        OutlinedButton(onClick = onPick) { Text(stringResource(label)) }
        if (removable) TextButton(onClick = onRemove) { Text(stringResource(remove)) }
    }
}

@Composable
private fun FieldRow(index: Int, field: AccountField, form: ProfileForm, onForm: (ProfileForm) -> Unit) {
    fun set(changed: AccountField) =
        onForm(form.copy(fields = form.fields.toMutableList().also { it[index] = changed }))
    Row(horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
        OutlinedTextField(
            value = field.name,
            onValueChange = { set(field.copy(name = it)) },
            label = { Text(stringResource(R.string.profile_edit_field_name, index + 1)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = field.value,
            onValueChange = { set(field.copy(value = it)) },
            label = { Text(stringResource(R.string.profile_edit_field_value, index + 1)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Who may follow and find the reader, and how their posts start out. */
@Composable
private fun Options(form: ProfileForm, onForm: (ProfileForm) -> Unit) {
    SwitchRow(stringResource(R.string.profile_edit_locked), form.locked, { onForm(form.copy(locked = it)) })
    SwitchRow(stringResource(R.string.profile_edit_bot), form.bot, { onForm(form.copy(bot = it)) })
    SwitchRow(
        stringResource(R.string.profile_edit_discoverable),
        form.discoverable,
        { onForm(form.copy(discoverable = it)) },
    )
    SwitchRow(stringResource(R.string.profile_edit_indexable), form.indexable, { onForm(form.copy(indexable = it)) })
    SwitchRow(stringResource(R.string.profile_edit_sensitive), form.sensitive, { onForm(form.copy(sensitive = it)) })
    PrivacyMenu(form.privacy) { onForm(form.copy(privacy = it)) }
}

@Composable
private fun PrivacyMenu(privacy: Visibility, onPick: (Visibility) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(R.string.profile_edit_privacy, stringResource(privacyName(privacy))))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(Visibility.Public, Visibility.Unlisted, Visibility.Private).forEach { choice ->
                DropdownMenuItem(
                    text = { Text(stringResource(privacyName(choice))) },
                    onClick = {
                        open = false
                        onPick(choice)
                    },
                )
            }
        }
    }
}

@Composable
private fun failureText(failure: SaveFailure): String = when (failure) {
    SaveFailure.Managed -> stringResource(R.string.profile_edit_managed)
    is SaveFailure.Refused -> failure.message ?: stringResource(R.string.profile_edit_refused)
    SaveFailure.Unreached -> stringResource(R.string.profile_edit_unreached)
}

private fun privacyName(visibility: Visibility): Int = when (visibility) {
    Visibility.Unlisted -> R.string.profile_edit_privacy_unlisted
    Visibility.Private -> R.string.profile_edit_privacy_private
    else -> R.string.profile_edit_privacy_public
}

/** What the picture shows now: the server's, the one picked, or none. */
private val Picture.shown: String?
    get() = when (this) {
        is Picture.Kept -> url
        is Picture.Picked -> uri
        Picture.Removed -> null
    }

private const val HEADER_ASPECT = 3f
private val AVATAR = 72.dp
