// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import social.aloha.core.data.CleartextAllowed
import social.aloha.core.data.RedirectUriProvider
import social.aloha.core.network.di.UserAgent
import social.aloha.core.network.oauth.OAuthIdentity

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

    private const val APP_LINK_HOST = "aloha.social"
}
