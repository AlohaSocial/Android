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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import social.aloha.core.model.WidgetFeed

/**
 * Every account's [WidgetFeed], keyed by account id, in one file the widgets read. It holds mentions,
 * which can be private, so it lives where no backup reaches.
 */
public class WidgetFeedStore(private val store: DataStore<Map<String, WidgetFeed>>) {
    public fun feed(accountId: String): Flow<WidgetFeed> =
        store.data.map { it[accountId] ?: WidgetFeed() }.distinctUntilChanged()

    /** Changes [accountId]'s feed; true when that changed anything. */
    public suspend fun update(accountId: String, change: (WidgetFeed) -> WidgetFeed): Boolean {
        var changed = false
        store.updateData { all ->
            val before = all[accountId] ?: WidgetFeed()
            val after = change(before)
            changed = after != before
            if (changed) all + (accountId to after) else all
        }
        return changed
    }

    public suspend fun forget(accountId: String) {
        store.updateData { it - accountId }
    }
}

internal object WidgetFeedSerializer : Serializer<Map<String, WidgetFeed>> {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val format = MapSerializer(String.serializer(), WidgetFeed.serializer())

    override val defaultValue: Map<String, WidgetFeed> = emptyMap()

    override suspend fun readFrom(input: InputStream): Map<String, WidgetFeed> = try {
        json.decodeFromString(format, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("unreadable widget feed", e)
    }

    override suspend fun writeTo(t: Map<String, WidgetFeed>, output: OutputStream) {
        output.write(json.encodeToString(format, t).encodeToByteArray())
    }
}
