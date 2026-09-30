// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import social.aloha.core.model.TimelineSource

/**
 * One account's reading settings. Every field has a default, so a file written before a field existed
 * reads as that default. A file from a later build reads too: a field this build does not know is
 * dropped at the next write, and a choice it does not know reads as the default. A file that cannot be
 * read at all is replaced by defaults for every account.
 *
 * @property homeSource what the home screen showed last: the people followed, this server, or everyone.
 * @property recentTags the hashtags this account used last, newest first, offered first when writing.
 */
@Serializable
public data class AccountSettings(
    val showBoosts: Boolean = true,
    val showReplies: Boolean = true,
    val homeSource: TimelineSource = TimelineSource.Home,
    val recentTags: List<String> = emptyList(),
)

/**
 * Every account's [AccountSettings], in one typed file keyed by account id. The same idea as a Proto
 * DataStore, with kotlinx.serialization as the format the rest of the app already uses, so no protocol
 * buffer toolchain is needed. Removing an account removes its settings.
 */
public class AccountSettingsStore(private val store: DataStore<Map<String, AccountSettings>>) {
    public fun settings(accountId: String): Flow<AccountSettings> =
        store.data.map { it[accountId] ?: AccountSettings() }.distinctUntilChanged()

    public suspend fun update(accountId: String, change: (AccountSettings) -> AccountSettings) {
        store.updateData { all -> all + (accountId to change(all[accountId] ?: AccountSettings())) }
    }

    public suspend fun forget(accountId: String) {
        store.updateData { it - accountId }
    }
}

internal object AccountSettingsSerializer : Serializer<Map<String, AccountSettings>> {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }
    private val format = MapSerializer(String.serializer(), AccountSettings.serializer())

    override val defaultValue: Map<String, AccountSettings> = emptyMap()

    override suspend fun readFrom(input: InputStream): Map<String, AccountSettings> = try {
        json.decodeFromString(format, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("unreadable account settings", e)
    }

    override suspend fun writeTo(t: Map<String, AccountSettings>, output: OutputStream) {
        output.write(json.encodeToString(format, t).encodeToByteArray())
    }
}
