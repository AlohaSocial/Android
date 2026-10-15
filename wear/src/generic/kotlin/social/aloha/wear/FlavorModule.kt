// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import social.aloha.core.model.HandedOverAccount
import social.aloha.wear.auth.PhoneAccounts

@Module
@InstallIn(SingletonComponent::class)
internal object FlavorModule {
    /** Without Google's Data Layer the watch never hears from the phone app: it signs in on its own. */
    @Provides
    fun phoneAccounts(): PhoneAccounts = object : PhoneAccounts {
        override suspend fun reachable(): Boolean = false

        override suspend fun account(): HandedOverAccount? = null
    }
}
