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
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import social.aloha.core.network.di.UserAgent
import social.aloha.wear.auth.PhoneBrowser
import social.aloha.wear.auth.RemoteAuthBrowser

@HiltAndroidApp
class WearApplication : Application()

/** A scope as long as the process, for work a short-lived service starts and must not wait on. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WatchScope

@Module
@InstallIn(SingletonComponent::class)
internal interface WearModule {
    @Binds
    fun phoneBrowser(browser: RemoteAuthBrowser): PhoneBrowser

    companion object {
        @Provides
        @Singleton
        @WatchScope
        fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @UserAgent
        fun userAgent(): String =
            "AlohaSocial/${BuildConfig.VERSION_NAME} (Wear OS; Android ${Build.VERSION.RELEASE}; +https://aloha.social)"
    }
}
