// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import social.aloha.core.sync.AndroidDeviceConditions
import social.aloha.core.sync.DeviceConditions
import social.aloha.core.sync.NotificationRaiser
import social.aloha.core.sync.PollListener

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
}
