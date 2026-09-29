// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import social.aloha.core.model.MigrationAnnouncement
import social.aloha.core.model.MigrationEntry
import social.aloha.core.model.MigrationLine
import social.aloha.core.model.MigrationLookup
import social.aloha.core.model.MigrationReport
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.OptionalBoolSerializer
import social.aloha.core.network.decoding.decodeOrRecord
import social.aloha.core.network.decoding.lossy

private fun decodeOnly(): Nothing = throw SerializationException("wire DTOs are decode-only")

@Serializable
internal data class MigrationAnnouncementDto(
    @Serializable(with = LenientTextSerializer::class) val handle: String? = null,
    @Serializable(with = LenientTextSerializer::class) val address: String? = null,
)

internal fun MigrationAnnouncementDto.toDomain(): MigrationAnnouncement =
    MigrationAnnouncement(handle.orEmpty(), address.orEmpty())

@Serializable
internal data class MigrationRawEntryDto(
    @Serializable(with = LenientTextSerializer::class) val handle: String? = null,
    @Serializable(with = LenientTextSerializer::class) val acct: String? = null,
    @Serializable(with = OptionalBoolSerializer::class) val found: Boolean? = null,
    @Serializable(with = AccountOrNull::class) val account: AccountDto? = null,
)

/**
 * Which handles exist somewhere. The server has sent this in two shapes: `{results: [...]}` with one
 * entry per handle, and `{found, accounts, missing}` where `found` holds accounts or bare handles.
 * Both are read; a handle is listed once, first mention wins.
 */
internal object MigrationLookupSerializer : KSerializer<MigrationLookup> {
    override val descriptor: SerialDescriptor = MigrationRawEntryDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): MigrationLookup {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val root = json.decodeJsonElement() as? JsonObject ?: return MigrationLookup()
        val results = json.json.lossy(MigrationRawEntryDto.serializer(), root["results"]).mapNotNull { raw ->
            val account = raw.account?.toDomain()
            val handle = raw.handle ?: raw.acct ?: account?.acct
            handle?.takeIf { it.isNotEmpty() }?.let { MigrationEntry(it, raw.found ?: (account != null), account) }
        }
        val found = (root["found"] as? JsonArray).orEmpty().mapIndexedNotNull { index, item ->
            foundEntry(json, item, index)
        }
        val accounts = json.json.lossy(AccountDto.serializer(), root["accounts"]).map { it.toDomain() }
            .map { MigrationEntry(it.acct, found = true, account = it) }
        val missing = (root["missing"] as? JsonArray).orEmpty().mapNotNull {
            it.text()
        }.map { MigrationEntry(it, found = false) }
        return MigrationLookup((results + found + accounts + missing).distinctBy { it.handle })
    }

    private fun foundEntry(json: JsonDecoder, item: JsonElement, index: Int): MigrationEntry? = when (item) {
        is JsonPrimitive -> item.text()?.let { MigrationEntry(it, found = true) }

        is JsonObject -> json.json.decodeOrRecord(AccountDto.serializer(), item, index)?.toDomain()
            ?.let { MigrationEntry(it.acct, found = true, account = it) }

        else -> null
    }

    override fun serialize(encoder: Encoder, value: MigrationLookup): Unit = decodeOnly()
}

private fun JsonElement.text(): String? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.takeIf { it.isNotEmpty() }

/**
 * An import report: every key kept as it came, numbers and yes/no as text, a list as its length,
 * and `log` as the lines of the log. Sorted by key.
 */
internal object MigrationReportSerializer : KSerializer<MigrationReport> {
    override val descriptor: SerialDescriptor = MigrationRawEntryDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): MigrationReport {
        val root = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonObject ?: return MigrationReport()
        val log = (root["log"] as? JsonArray).orEmpty().mapNotNull { it.text() }
        val lines = root.filterKeys { it != "log" }.toSortedMap().mapNotNull { (key, value) ->
            value.reportValue()?.let { MigrationLine(key, it) }
        }
        return MigrationReport(lines, log)
    }

    private fun JsonElement.reportValue(): String? = when (this) {
        is JsonArray -> size.toString()
        is JsonNull -> null
        is JsonPrimitive -> primitiveValue()
        else -> null
    }

    private fun JsonPrimitive.primitiveValue(): String {
        val number = longOrNull
        val flag = booleanOrNull
        return when {
            isString -> content
            number != null -> number.toString()
            flag == true -> "yes"
            flag == false -> "no"
            else -> content
        }
    }

    override fun serialize(encoder: Encoder, value: MigrationReport): Unit = decodeOnly()
}
