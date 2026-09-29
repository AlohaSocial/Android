// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

@Serializable
public enum class Visibility(override val wire: String) : WireValue {
    Public("public"),
    Unlisted("unlisted"),
    Private("private"),
    Direct("direct"),

    /** A value this app does not know. Treated as the most restrictive, never as public. */
    Unknown("__unknown"),
    ;

    /** How restrictive this is, ascending. A reply is never less restrictive than the post it answers. */
    public val restrictiveness: Int
        get() = when (this) {
            Public -> 0
            Unlisted -> 1
            Private -> 2
            Direct, Unknown -> 3
        }

    public val isUnknown: Boolean get() = this == Unknown

    public companion object {
        public fun fromWire(raw: String?): Visibility = entries.fromWire(raw, Unknown)

        public fun mostRestrictive(lhs: Visibility, rhs: Visibility): Visibility =
            if (lhs.restrictiveness >= rhs.restrictiveness) lhs else rhs
    }
}

@Serializable
public enum class AttachmentKind(override val wire: String) : WireValue {
    Image("image"),
    Video("video"),
    Gifv("gifv"),
    Audio("audio"),

    /** Mastodon's own `unknown`: a file the server accepted but cannot show, such as a PDF. */
    UnsupportedFile("unknown"),

    /** A value this app does not know. */
    Unknown("__unknown"),
    ;

    public val isVisualMedia: Boolean get() = this == Image || this == Video || this == Gifv

    public val isPlayable: Boolean get() = this == Video || this == Gifv || this == Audio

    public companion object {
        /** A missing type is Mastodon's `unknown`; a type this app does not know is [Unknown]. */
        public fun fromWire(raw: String?): AttachmentKind =
            if (raw == null) UnsupportedFile else entries.fromWire(raw, Unknown)
    }
}

/** The notification types Nextcloud Social serves, plus the rest of Mastodon's. */
@Serializable
public enum class NotificationKind(override val wire: String) : WireValue {
    Mention("mention"),
    Reblog("reblog"),
    Favourite("favourite"),
    Follow("follow"),
    FollowRequest("follow_request"),
    Poll("poll"),
    Status("status"),
    Update("update"),
    ModerationWarning("moderation_warning"),
    SeveredRelationships("severed_relationships"),
    AdminSignUp("admin.sign_up"),
    AdminReport("admin.report"),
    AnnualReport("annual_report"),
    Unknown("__unknown"),
    ;

    /**
     * Whether several of these collapse into one row. Mentions never do: two people writing are two
     * things to read. Nothing about a poll, an edit or a moderation decision groups either.
     */
    public val groups: Boolean get() = this == Favourite || this == Reblog || this == Follow

    public val isUnknown: Boolean get() = this == Unknown

    public companion object {
        public fun fromWire(raw: String?): NotificationKind = entries.fromWire(raw, Unknown)
    }
}

@Serializable
public enum class FilterAction(override val wire: String) : WireValue {
    Warn("warn"),
    Hide("hide"),
    Blur("blur"),
    Unknown("__unknown"),
    ;

    public companion object {
        /** A missing action is `warn`, as Mastodon defines it. */
        public fun fromWire(raw: String?): FilterAction = if (raw == null) Warn else entries.fromWire(raw, Unknown)
    }
}

@Serializable
public enum class FilterContext(override val wire: String) : WireValue {
    Home("home"),
    Notifications("notifications"),
    Public("public"),
    Thread("thread"),
    Account("account"),
    Unknown("__unknown"),
    ;

    public companion object {
        public fun fromWire(raw: String?): FilterContext = entries.fromWire(raw, Unknown)
    }
}

/**
 * How sensitive media is shown. Nextcloud Social serves PeerTube's three NSFW policies under the
 * names Mastodon already has for the same states.
 */
@Serializable
public enum class SensitiveMediaPolicy(override val wire: String) : WireValue {
    /** Drawn as is. */
    ShowAll("show_all"),

    /** Covered, blurhash showing, one press away. */
    Blur("default"),

    /** Not drawn and no button to draw it; opening the post is what it takes. */
    HideAll("hide_all"),
    Unknown("__unknown"),
    ;

    public val allowsAutomaticReveal: Boolean get() = this == ShowAll

    public val drawsAtAll: Boolean get() = this != HideAll

    public companion object {
        /**
         * The value that puts an account back to following the instance's policy, which differs from
         * choosing whatever the instance happens to do today.
         */
        public const val FOLLOW_INSTANCE: String = ""

        public fun fromWire(raw: String?): SensitiveMediaPolicy = entries.fromWire(raw, Unknown)
    }
}
