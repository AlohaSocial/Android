// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import social.aloha.core.data.diagnostics.LogBuffer
import timber.log.Timber

/**
 * Logging is planted here: to logcat in a debuggable build, to the in-memory [LogBuffer] in a release
 * build. Nothing else starts here: every other component initialises on first use, off the startup
 * path. The image loader is handed to Coil as a provider, so it is built when the first image loads,
 * and WorkManager starts with the first upload, with Hilt's factory so its workers get what they need.
 */
@HiltAndroidApp
class AlohaApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    @Inject lateinit var imageLoader: Provider<ImageLoader>

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var logBuffer: LogBuffer

    override fun onCreate() {
        super.onCreate()
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else logBuffer)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
