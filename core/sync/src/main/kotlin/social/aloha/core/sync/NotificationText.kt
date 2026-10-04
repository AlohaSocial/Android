// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import android.content.res.Resources
import social.aloha.core.model.NotificationKind

/**
 * What a notification says happened, in one wording for the list in the app and for the one the device
 * raises: "Alice and 34 others favourited your post".
 */
public object NotificationText {
    /**
     * [name] is the newest one to do it, null when the server did not say; [others] how many more did,
     * named as a number unless [withCount] is off, when they are just "others".
     */
    public fun summary(
        resources: Resources,
        kind: NotificationKind,
        name: String?,
        others: Int,
        withCount: Boolean = true,
    ): String {
        val first = name ?: resources.getString(R.string.notifications_someone)
        val who = when {
            others <= 0 -> first
            withCount -> resources.getQuantityString(R.plurals.notifications_and_others, others, first, others)
            else -> resources.getString(R.string.notifications_and_others_plain, first)
        }
        return when (kind) {
            NotificationKind.Mention -> resources.getString(R.string.notifications_mention, who)
            NotificationKind.Reblog -> resources.getString(R.string.notifications_reblog, who)
            NotificationKind.Favourite -> resources.getString(R.string.notifications_favourite, who)
            NotificationKind.Follow -> resources.getString(R.string.notifications_follow, who)
            NotificationKind.FollowRequest -> resources.getString(R.string.notifications_follow_request, who)
            NotificationKind.Poll -> resources.getString(R.string.notifications_poll)
            NotificationKind.Status -> resources.getString(R.string.notifications_status, who)
            NotificationKind.Update -> resources.getString(R.string.notifications_update, who)
            NotificationKind.ModerationWarning -> resources.getString(R.string.notifications_moderation)
            NotificationKind.SeveredRelationships -> resources.getString(R.string.notifications_severed)
            NotificationKind.AdminSignUp -> resources.getString(R.string.notifications_admin_sign_up, who)
            NotificationKind.AdminReport -> resources.getString(R.string.notifications_admin_report, who)
            NotificationKind.AnnualReport -> resources.getString(R.string.notifications_annual_report)
            NotificationKind.Unknown -> who
        }
    }
}
