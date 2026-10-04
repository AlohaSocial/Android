// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import social.aloha.core.data.CleartextAllowed
import social.aloha.core.data.DeviceStorage
import social.aloha.core.data.RedirectUriProvider
import social.aloha.core.network.di.UserAgent
import social.aloha.core.network.oauth.OAuthIdentity
import social.aloha.core.sync.AvatarSource

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @UserAgent
    fun userAgent(): String =
        "AlohaSocial/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; +https://aloha.social)"

    /** Debug builds may sign in to the plain-HTTP dev instance; the network security config agrees. */
    @Provides
    @CleartextAllowed
    fun cleartextAllowed(): Boolean = BuildConfig.DEBUG

    /**
     * The verified App Link when this install is verified for aloha.social, so no other app can
     * receive the code; otherwise, and always below Android 12, the custom scheme, where PKCE makes a
     * hijacked code useless.
     */
    @Provides
    fun redirectUriProvider(@ApplicationContext context: Context): RedirectUriProvider = RedirectUriProvider {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appLinkVerified(context)) {
            OAuthIdentity.APP_LINK_REDIRECT
        } else {
            OAuthIdentity.SCHEME_REDIRECT
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun appLinkVerified(context: Context): Boolean {
        val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
        val state = manager.getDomainVerificationUserState(context.packageName) ?: return false
        return state.hostToStateMap[APP_LINK_HOST] == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
    }

    /** Avatars for the notifications the device raises, through the app's one image loader and its cache. */
    @Provides
    fun avatars(@ApplicationContext context: Context, loader: Provider<ImageLoader>): AvatarSource =
        AvatarSource { url ->
            val request = ImageRequest.Builder(context).data(url).size(AVATAR_PIXELS).allowHardware(false).build()
            (loader.get().execute(request) as? SuccessResult)?.image?.toBitmap()
        }

    private const val APP_LINK_HOST = "aloha.social"
    private const val AVATAR_PIXELS = 256
}

@Module
@InstallIn(SingletonComponent::class)
interface StorageModule {
    @Binds
    fun deviceStorage(caches: DeviceCaches): DeviceStorage
}
