// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.DeliveryInstance
import social.aloha.core.model.DeliveryReport
import social.aloha.core.model.DeliveryState
import social.aloha.core.model.NextcloudTheme
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OrNullSerializer
import social.aloha.core.network.decoding.StringListSerializer

/**
 * Nextcloud's OCS capabilities envelope, unwrapped only as far as `theming` and the notifications app's
 * `push` list; the rest of `capabilities` belongs to other apps.
 */
@Serializable
internal data class OcsCapabilitiesDto(@Serializable(with = OcsOrNull::class) val ocs: OcsDto? = null) {
    val theming: NextcloudThemeDto? get() = ocs?.data?.capabilities?.theming
}

@Serializable
internal data class OcsDto(@Serializable(with = OcsDataOrNull::class) val data: OcsDataDto? = null)

@Serializable
internal data class OcsDataDto(
    @Serializable(with = CapabilitiesOrNull::class) val capabilities: CapabilitiesDto? = null,
)

@Serializable
internal data class CapabilitiesDto(
    @Serializable(with = ThemingOrNull::class) val theming: NextcloudThemeDto? = null,
    @Serializable(with = NotificationsCapabilityOrNull::class) val notifications: NotificationsCapabilityDto? = null,
)

/** Present only to a signed-in request: an anonymous one never sees the notifications app. */
@Serializable
internal data class NotificationsCapabilityDto(
    @Serializable(with = StringListSerializer::class) val push: List<String> = emptyList(),
)

@Serializable
internal data class NextcloudThemeDto(
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @Serializable(with = LenientTextSerializer::class) val slogan: String? = null,
    @Serializable(with = LenientTextSerializer::class) val color: String? = null,
    @SerialName("color-element-bright") @Serializable(with = LenientTextSerializer::class)
    val colorElementBright: String? = null,
    @SerialName("color-element-dark") @Serializable(with = LenientTextSerializer::class)
    val colorElementDark: String? = null,
    @SerialName("color-text") @Serializable(with = LenientTextSerializer::class) val colorText: String? = null,
)

/** Where one post got to. Author only. */
@Serializable
internal data class DeliveryReportDto(
    @Serializable(with = LenientIntSerializer::class) val total: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val delivered: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val sending: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val waiting: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val failing: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val abandoned: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val retention: Int = DeliveryReport.DEFAULT_RETENTION,
    @Serializable(with = LossyListSerializer::class) val instances: List<DeliveryInstanceDto> = emptyList(),
)

/** One server a post was sent to; `last` is seen both as Unix seconds and as an ISO date. */
@Serializable
internal data class DeliveryInstanceDto(
    val host: String = "",
    val state: String? = null,
    @Serializable(with = LenientIntSerializer::class) val tries: Int = 0,
    @Serializable(with = LenientInstantSerializer::class) val last: Instant? = null,
)

internal object OcsOrNull : KSerializer<OcsDto?> by OrNullSerializer(OcsDto.serializer())

internal object OcsDataOrNull : KSerializer<OcsDataDto?> by OrNullSerializer(OcsDataDto.serializer())

internal object CapabilitiesOrNull : KSerializer<CapabilitiesDto?> by OrNullSerializer(CapabilitiesDto.serializer())

internal object ThemingOrNull : KSerializer<NextcloudThemeDto?> by OrNullSerializer(NextcloudThemeDto.serializer())

internal object NotificationsCapabilityOrNull :
    KSerializer<NotificationsCapabilityDto?> by OrNullSerializer(NotificationsCapabilityDto.serializer())

internal fun NextcloudThemeDto.toDomain(): NextcloudTheme = NextcloudTheme(
    name = name.orEmpty(),
    slogan = slogan.orEmpty(),
    colourHex = NextcloudTheme.normalise(color),
    elementBrightHex = NextcloudTheme.normalise(colorElementBright),
    elementDarkHex = NextcloudTheme.normalise(colorElementDark),
    textHex = NextcloudTheme.normalise(colorText),
)

internal fun DeliveryReportDto.toDomain(): DeliveryReport = DeliveryReport(
    total = total,
    delivered = delivered,
    sending = sending,
    waiting = waiting,
    failing = failing,
    abandoned = abandoned,
    retention = retention,
    instances = instances.map { DeliveryInstance(it.host, DeliveryState.fromWire(it.state), it.tries, it.last) },
)
