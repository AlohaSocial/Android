// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.AppLockSettings
import social.aloha.core.data.sync.WidgetUpdates
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineSource

/** What a widget reads; it is made by the system, outside Hilt's reach, so it asks for these. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun accounts(): AccountRepository

    fun timelines(): TimelineRepository

    fun widgets(): WidgetUpdates

    fun lock(): AppLockSettings
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** A post or mention as a widget lists it; a tap opens [statusId]. */
internal data class WidgetRow(val statusId: String, val name: String, val text: String)

/** A widget's own settings, chosen when it was placed. */
internal object WidgetKeys {
    val ACCOUNT = stringPreferencesKey("account")
    val SOURCE = stringPreferencesKey("source")
}

/** The timelines a Latest posts widget can show, by the key its settings store. */
internal val WIDGET_SOURCES: List<TimelineSource> =
    listOf(TimelineSource.Home, TimelineSource.Local, TimelineSource.Federated)

internal fun sourceOf(stored: String?): TimelineSource =
    WIDGET_SOURCES.firstOrNull { it.storageKey == stored } ?: TimelineSource.Home

/** The account [id] was set up for, if it is still signed in here. */
internal suspend fun Context.widgetAccount(id: GlanceId): Pair<SignedInAccount?, Preferences> {
    val settings = getAppWidgetState(this, PreferencesGlanceStateDefinition, id)
    val account = settings[WidgetKeys.ACCOUNT]?.let { widgetEntryPoint().accounts().byId(it) }
    return account to settings
}
