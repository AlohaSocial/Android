// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Provider
import social.aloha.core.data.CleartextAllowed
import social.aloha.core.data.RedirectUriProvider
import social.aloha.core.data.diagnostics.AppBuild
import social.aloha.core.data.diagnostics.LogBuffer
import social.aloha.core.network.di.UserAgent
import social.aloha.core.network.oauth.OAuthIdentity
import timber.log.Timber

/** Logging as on the phone: to logcat in a debuggable build, to the in-memory [LogBuffer] in a release build. */
@HiltAndroidApp
class TvApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var imageLoader: Provider<ImageLoader>

    @Inject lateinit var logBuffer: LogBuffer

    override fun onCreate() {
        super.onCreate()
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else logBuffer)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}

@Module
@InstallIn(SingletonComponent::class)
internal object TvModule {
    @Provides
    @UserAgent
    fun userAgent(): String =
        "AlohaSocial/${BuildConfig.VERSION_NAME} (Android TV; Android ${Build.VERSION.RELEASE}; +https://aloha.social)"

    /** Debug builds may sign in to the plain-HTTP dev instance. */
    @Provides
    @CleartextAllowed
    fun cleartextAllowed(): Boolean = BuildConfig.DEBUG

    @Provides
    fun appBuild(): AppBuild = AppBuild(BuildConfig.VERSION_NAME, BuildConfig.FLAVOR)

    /** A TV has no browser to come back from: the server shows the code on its page, and the person types it. */
    @Provides
    fun redirectUriProvider(): RedirectUriProvider = object : RedirectUriProvider {
        override fun redirectUri(): String = OAuthIdentity.OUT_OF_BAND_REDIRECT

        override fun registeredRedirects(): String = OAuthIdentity.OUT_OF_BAND_REDIRECT
    }
}
