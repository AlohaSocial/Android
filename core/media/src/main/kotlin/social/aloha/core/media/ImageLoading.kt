// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.media

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal object ImageLoadingModule {
    /**
     * One loader for the app, over the app's own OkHttp client, so images are fetched with the same
     * user-trusted certificates, client certificate and user agent as the API. Animated GIF and WebP
     * play; no SVG decoder is installed, since remote content is never SVG. Lists do not cross-fade.
     */
    @Provides
    @Singleton
    fun imageLoader(@ApplicationContext context: Context, http: OkHttpClient): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { http }))
                add(
                    if (Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.P
                    ) {
                        AnimatedImageDecoder.Factory()
                    } else {
                        GifDecoder.Factory()
                    },
                )
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_SHARE).build() }
            .diskCache {
                DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(DISK_BYTES).build()
            }
            .crossfade(false)
            .build()

    private const val MEMORY_SHARE = 0.25
    private const val DISK_BYTES = 512L * 1024 * 1024
}

/**
 * Loads what the next rows will show before they are on screen: the images of the ten rows from the
 * one in view are kept in flight, and a request whose row left that window is cancelled, so a fast
 * scroll does not queue up images nobody will see. Cancelling one that already finished costs nothing.
 */
@Singleton
public class ImagePrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: ImageLoader,
) {
    private val inFlight = HashMap<String, Disposable>()

    /** Called as the row at [index] comes into view, with each row's image addresses in list order. */
    @Synchronized
    public fun visible(index: Int, rows: List<List<String>>, size: Size) {
        val window = rows.drop(index).take(DISTANCE).flatten().toSet()
        inFlight.keys.filterNot(window::contains).forEach { inFlight.remove(it)?.dispose() }
        window.filterNot(inFlight::containsKey).forEach { url ->
            inFlight[url] = loader.enqueue(ImageRequest.Builder(context).data(url).size(size).build())
        }
    }

    private companion object {
        const val DISTANCE = 10
    }
}
