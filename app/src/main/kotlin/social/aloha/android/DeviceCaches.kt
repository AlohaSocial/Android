// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import coil3.ImageLoader
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import social.aloha.core.data.DeviceStorage

/**
 * What the device keeps across accounts: the images drawn and the HTTP responses cached. They can hold
 * media from private posts and direct messages, so they go when the last account signs out.
 */
class DeviceCaches @Inject constructor(private val images: Provider<ImageLoader>, private val http: OkHttpClient) :
    DeviceStorage {
    override suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) {
        (images.get().diskCache?.size ?: 0L) + (http.cache?.size() ?: 0L)
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        images.get().let { loader ->
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        http.cache?.evictAll()
    }
}
