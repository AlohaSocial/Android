// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.auth

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.wear.phone.interactions.authentication.CodeChallenge
import androidx.wear.phone.interactions.authentication.CodeVerifier
import androidx.wear.phone.interactions.authentication.OAuthRequest
import androidx.wear.phone.interactions.authentication.OAuthResponse
import androidx.wear.phone.interactions.authentication.RemoteAuthClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl

/** What came back from the sign-in page opened on the phone. */
sealed interface PhoneAnswer {
    /** The address the server sent the browser back to, carrying the code or the refusal. */
    data class Returned(val url: String) : PhoneAnswer

    /** The page never opened or never came back: no phone, no companion app, or it was closed. */
    data class Failed(val reason: Int) : PhoneAnswer
}

/** Opens a sign-in page in the paired phone's browser and waits for it to come back to the watch. */
interface PhoneBrowser {
    /** Where the server has to send the browser back to, which the client is registered with. */
    val redirectUri: String

    suspend fun authorize(url: HttpUrl, clientId: String, verifier: String): PhoneAnswer
}

/**
 * Wear's `RemoteAuthClient`: the page opens on the phone through the Wear OS companion app, whichever
 * Aloha build is installed there, or none, and the answer comes back through Google's redirect address.
 */
class RemoteAuthBrowser @Inject constructor(@ApplicationContext private val context: Context) : PhoneBrowser {
    // the builder picks the redirect address for this watch's region; the URL here only satisfies it
    override val redirectUri: String by lazy {
        request("https://example.invalid/".toUri(), "-", CodeVerifier()).redirectUrl
    }

    override suspend fun authorize(url: HttpUrl, clientId: String, verifier: String): PhoneAnswer {
        val request = try {
            request(url.toString().toUri(), clientId, CodeVerifier(verifier))
        } catch (_: IllegalArgumentException) {
            return PhoneAnswer.Failed(RemoteAuthClient.ERROR_UNSUPPORTED)
        }
        val client = RemoteAuthClient.create(context)
        return try {
            suspendCancellableCoroutine { continuation ->
                client.sendAuthorizationRequest(
                    request,
                    Runnable::run,
                    object : RemoteAuthClient.Callback() {
                        override fun onAuthorizationResponse(request: OAuthRequest, response: OAuthResponse) {
                            val returned = response.responseUrl?.toString()
                            continuation.resume(
                                returned?.let(PhoneAnswer::Returned) ?: PhoneAnswer.Failed(response.errorCode),
                            )
                        }

                        override fun onAuthorizationError(request: OAuthRequest, errorCode: Int) {
                            continuation.resume(PhoneAnswer.Failed(errorCode))
                        }
                    },
                )
            }
        } finally {
            client.close()
        }
    }

    private fun request(page: Uri, clientId: String, verifier: CodeVerifier): OAuthRequest =
        OAuthRequest.Builder(context)
            .setAuthProviderUrl(page)
            .setClientId(clientId)
            .setCodeChallenge(CodeChallenge(verifier))
            .build()
}
