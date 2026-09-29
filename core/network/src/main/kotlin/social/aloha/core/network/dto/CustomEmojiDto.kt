// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.CustomEmoji
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.OptionalBoolSerializer

@Serializable
internal data class CustomEmojiDto(
    val shortcode: String,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("static_url") @Serializable(with = LenientUrlSerializer::class) val staticUrl: String? = null,
    @SerialName("visible_in_picker") @Serializable(with = OptionalBoolSerializer::class)
    val visibleInPicker: Boolean? = null,
    val category: String? = null,
)

internal fun CustomEmojiDto.toDomain(): CustomEmoji = CustomEmoji(
    shortcode = shortcode,
    url = url,
    staticUrl = staticUrl,
    visibleInPicker = visibleInPicker ?: true,
    category = category,
)
