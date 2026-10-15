// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear

import android.app.Application
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import social.aloha.core.network.di.UserAgent
import social.aloha.wear.auth.PhoneBrowser
import social.aloha.wear.auth.RemoteAuthBrowser

@HiltAndroidApp
class WearApplication : Application()

@Module
@InstallIn(SingletonComponent::class)
internal interface WearModule {
    @Binds
    fun phoneBrowser(browser: RemoteAuthBrowser): PhoneBrowser

    companion object {
        @Provides
        @UserAgent
        fun userAgent(): String =
            "AlohaSocial/${BuildConfig.VERSION_NAME} (Wear OS; Android ${Build.VERSION.RELEASE}; +https://aloha.social)"
    }
}
