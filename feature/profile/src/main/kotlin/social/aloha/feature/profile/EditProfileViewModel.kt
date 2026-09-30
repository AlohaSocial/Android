// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.profile.OwnProfile
import social.aloha.core.model.Account
import social.aloha.core.model.AccountField
import social.aloha.core.model.Visibility
import social.aloha.core.navigation.EditProfileKey
import social.aloha.core.network.ApiError
import social.aloha.core.network.endpoints.CredentialsUpdate
import social.aloha.core.network.endpoints.ProfilePicture
import social.aloha.core.ui.copyTo
import social.aloha.core.ui.extensionFor

/** A picture of the profile: as it is on the server, one picked on the phone, or none. */
@Immutable
internal sealed interface Picture {
    data class Kept(val url: String?) : Picture

    data class Picked(val uri: String) : Picture

    data object Removed : Picture
}

/** The profile as the form holds it, from what the server keeps as written. */
@Immutable
internal data class ProfileForm(
    val displayName: String = "",
    val note: String = "",
    val fields: List<AccountField> = List(FIELDS) { AccountField("", "") },
    val locked: Boolean = false,
    val bot: Boolean = false,
    val discoverable: Boolean = true,
    val indexable: Boolean = true,
    val privacy: Visibility = Visibility.Public,
    val sensitive: Boolean = false,
    val avatar: Picture = Picture.Kept(null),
    val header: Picture = Picture.Kept(null),
) {
    companion object {
        /** Mastodon's number of profile fields; a server with fewer refuses the rest. */
        const val FIELDS = 4

        fun of(account: Account): ProfileForm {
            val source = account.source
            val fields = (source?.fields ?: account.fields).take(FIELDS)
            return ProfileForm(
                displayName = account.displayName,
                // the note as written; the account's own note is the server's HTML of it
                note = source?.note.orEmpty(),
                fields = fields + List(FIELDS - fields.size) { AccountField("", "") },
                locked = account.locked,
                bot = account.bot,
                discoverable = account.discoverable,
                indexable = account.indexable,
                privacy = source?.privacy ?: Visibility.Public,
                sensitive = source?.sensitive == true,
                avatar = Picture.Kept(account.avatar),
                header = Picture.Kept(account.header),
            )
        }
    }
}

/** Why a save did not go through. */
internal sealed interface SaveFailure {
    /** The server keeps the name and picture itself, from a directory it signs people in with. */
    data object Managed : SaveFailure

    data class Refused(val message: String?) : SaveFailure

    data object Unreached : SaveFailure
}

@Immutable
internal data class EditProfileUiState(
    val form: ProfileForm? = null,
    /** The profile as the server has it, which the form is saved against. */
    val original: ProfileForm? = null,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val failure: SaveFailure? = null,
    val saved: Boolean = false,
) {
    /** Whether saving would change anything. */
    val changed: Boolean get() = form != null && form != original
}

/** The reader's own profile, edited and saved in one go; only what changed is sent. */
@HiltViewModel(assistedFactory = EditProfileViewModel.Factory::class)
internal class EditProfileViewModel @AssistedInject constructor(
    @Assisted private val key: EditProfileKey,
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val profile: OwnProfile,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: EditProfileKey): EditProfileViewModel
    }

    private val state = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = state.asStateFlow()
    init {
        onRetry()
    }

    fun onRetry() {
        state.update { it.copy(loadFailed = false) }
        viewModelScope.launch {
            val answer = reader()?.let { profile.load(it) }
            if (answer is Answer.Got) {
                val form = ProfileForm.of(answer.value)
                state.update { it.copy(form = form, original = form) }
            } else {
                state.update { it.copy(loadFailed = true) }
            }
        }
    }

    fun onForm(form: ProfileForm) = state.update { it.copy(form = form) }

    fun onFailureShown() = state.update { it.copy(failure = null) }

    fun onSave() {
        val form = state.value.form ?: return
        val before = state.value.original ?: return
        state.update { it.copy(saving = true, failure = null) }
        viewModelScope.launch {
            val reader = reader() ?: return@launch
            val avatar = (form.avatar as? Picture.Picked)?.let { picture(it.uri, AVATAR) }
            val header = (form.header as? Picture.Picked)?.let { picture(it.uri, HEADER) }
            // the copies go whatever becomes of the save, a cancelled one too
            val answer = try {
                profile.save(
                    reader,
                    changes(before, form, avatar, header),
                    removeAvatar = form.avatar == Picture.Removed,
                    removeHeader = form.header == Picture.Removed,
                )
            } finally {
                listOfNotNull(avatar, header).forEach { it.file.delete() }
            }
            state.update {
                when (answer) {
                    is Answer.Got -> it.copy(saving = false, saved = true)
                    is Answer.Missed -> it.copy(saving = false, failure = failureOf(answer.error, before, form))
                }
            }
        }
    }

    private suspend fun reader() = accounts.byId(key.readerId)

    /** The picture at [uri], copied where the upload can stream it from. */
    private suspend fun picture(uri: String, name: String): ProfilePicture? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parsed = uri.toUri()
        val mime = resolver.getType(parsed) ?: "image/jpeg"
        val file = File(context.cacheDir, name + extensionFor(mime))
        ProfilePicture(file, file.name, mime).takeIf { resolver.copyTo(parsed, file) }
    }

    private companion object {
        const val AVATAR = "profile-avatar"
        const val HEADER = "profile-header"
    }
}

/** What [edited] changes about [original]: only that goes, with the pictures picked. */
internal fun changes(
    original: ProfileForm,
    edited: ProfileForm,
    avatar: ProfilePicture?,
    header: ProfilePicture?,
): CredentialsUpdate {
    fun <T> changed(pick: (ProfileForm) -> T): T? = pick(edited).takeIf { it != pick(original) }
    return CredentialsUpdate(
        displayName = changed { it.displayName },
        note = changed { it.note },
        avatar = avatar,
        header = header,
        // every field goes when one changed: an empty one is how a field is removed
        fields = changed { it.fields },
        locked = changed { it.locked },
        discoverable = changed { it.discoverable },
        indexable = changed { it.indexable },
        bot = changed { it.bot },
        privacy = changed { it.privacy },
        sensitive = changed { it.sensitive },
    )
}

/**
 * Why [error] stopped the save. A refusal of a new name or picture from a server that signs people in
 * through a directory (LDAP, SAML) is that server keeping them itself, not a mistake in the form.
 */
internal fun failureOf(error: ApiError, original: ProfileForm, edited: ProfileForm): SaveFailure = when {
    error !is ApiError.Unprocessable -> SaveFailure.Unreached
    edited.displayName != original.displayName || edited.avatar != original.avatar -> SaveFailure.Managed
    else -> SaveFailure.Refused(error.message)
}
