// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import social.aloha.core.intelligence.PrivacyNotice

/** The privacy statement's paragraph on the on-device features, which each build words for what it holds. */
@Module
@InstallIn(SingletonComponent::class)
internal object PrivacyNoticeModule {
    @Provides
    fun notice(): PrivacyNotice = PrivacyNotice(R.string.intelligence_privacy)
}
