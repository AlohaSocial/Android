// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

/**
 * Nothing else starts here: every other component initialises on first use, off the startup path. The
 * image loader is handed to Coil as a provider, so it is built when the first image loads.
 */
@HiltAndroidApp
class AlohaApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var imageLoader: Provider<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
