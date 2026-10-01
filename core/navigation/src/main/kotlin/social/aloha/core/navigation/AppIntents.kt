// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * How what lives outside the app's screens opens them: a notification, a widget, a launcher shortcut.
 * The intent names the account, or none for the account in use (the static launcher shortcuts); what it
 * opens is looked up in the app, never trusted as given.
 */
public object AppIntents {
    /** Opens the post [EXTRA_STATUS], else the profile [EXTRA_PROFILE], else the notifications. */
    public const val ACTION_OPEN: String = "social.aloha.action.OPEN"

    /** Opens the composer on a new post. */
    public const val ACTION_COMPOSE: String = "social.aloha.action.COMPOSE"

    /** Opens search. */
    public const val ACTION_SEARCH: String = "social.aloha.action.SEARCH"

    public const val EXTRA_ACCOUNT: String = "account"
    public const val EXTRA_STATUS: String = "status"
    public const val EXTRA_PROFILE: String = "profile"

    /**
     * Opens the app on [statusId] of [accountId], else on [profileId], else on its notifications. Each
     * intent is told apart by its data, so two taps never share one pending intent and its extras.
     */
    public fun open(context: Context, accountId: String, statusId: String?, profileId: String? = null): Intent =
        launch(context, ACTION_OPEN, "open/$accountId/${statusId.orEmpty()}/${profileId.orEmpty()}")
            .putExtra(EXTRA_ACCOUNT, accountId)
            .putExtra(EXTRA_STATUS, statusId)
            .putExtra(EXTRA_PROFILE, profileId)

    /**
     * [action] as the account in use, for the launcher's shortcuts: the app opens the notifications for
     * [ACTION_OPEN], a new post for [ACTION_COMPOSE] and search for [ACTION_SEARCH].
     */
    public fun asActiveAccount(context: Context, action: String): Intent = launch(context, action, "active/$action")

    /** Opens the composer on a new post by [accountId]. */
    public fun compose(context: Context, accountId: String): Intent =
        launch(context, ACTION_COMPOSE, "compose/$accountId").putExtra(EXTRA_ACCOUNT, accountId)

    private fun launch(context: Context, action: String, path: String): Intent = (
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        )
        .setAction(action)
        .setData(Uri.parse("aloha-intent:$path"))
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
