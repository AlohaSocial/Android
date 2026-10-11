// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync.di

import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import social.aloha.core.sync.AndroidDeviceConditions
import social.aloha.core.sync.DeviceConditions
import social.aloha.core.sync.NoSearchIndex
import social.aloha.core.sync.NotificationRaiser
import social.aloha.core.sync.PlatformSearchIndex
import social.aloha.core.sync.PollListener
import social.aloha.core.sync.SearchIndex

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncModule {
    @Binds
    abstract fun deviceConditions(conditions: AndroidDeviceConditions): DeviceConditions

    /** Whoever acts on a moved unread count joins this set; polling needs none to run. */
    @Multibinds
    abstract fun pollListeners(): Set<PollListener>

    @Binds
    @IntoSet
    abstract fun raiser(raiser: NotificationRaiser): PollListener

    companion object {
        /** The system's index from Android 12, which the launcher's search reads; nothing before. */
        @Provides
        @Singleton
        fun searchIndex(@ApplicationContext context: Context): SearchIndex =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PlatformSearchIndex(context) else NoSearchIndex
    }
}
