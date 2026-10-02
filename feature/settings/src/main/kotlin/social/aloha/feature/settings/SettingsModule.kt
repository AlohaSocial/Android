// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import social.aloha.core.ui.SettingsSection

/** Collects every feature's [SettingsSection]; the settings screen needs none to exist to build. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {
    @Multibinds
    abstract fun sections(): Set<SettingsSection>

    companion object {
        @Provides
        @IntoSet
        fun accounts(): SettingsSection = AccountsSection

        @Provides
        @IntoSet
        fun appearance(): SettingsSection = AppearanceSection

        @Provides
        @IntoSet
        fun reading(): SettingsSection = ReadingSection

        @Provides
        @IntoSet
        fun sound(): SettingsSection = SoundSection

        @Provides
        @IntoSet
        fun privacy(): SettingsSection = PrivacySection

        @Provides
        @IntoSet
        fun media(): SettingsSection = MediaSection

        @Provides
        @IntoSet
        fun storage(): SettingsSection = StorageSection

        @Provides
        @IntoSet
        fun server(): SettingsSection = ServerSection

        @Provides
        @IntoSet
        fun about(): SettingsSection = AboutSection

        @Provides
        @IntoSet
        fun nextcloud(): SettingsSection = NextcloudSection

        @Provides
        @IntoSet
        fun deleteAccount(): SettingsSection = DeleteAccountSection
    }
}
