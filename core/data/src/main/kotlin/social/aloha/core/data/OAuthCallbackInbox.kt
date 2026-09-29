// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands the OAuth callback from the activity that received it to the sign-in screen. Held as state
 * until consumed, so a callback that arrives while the screen is being recreated is not lost.
 */
@Singleton
public class OAuthCallbackInbox @Inject constructor() {
    private val latest = MutableStateFlow<String?>(null)

    public val callback: StateFlow<String?> = latest.asStateFlow()

    public fun deliver(uri: String) {
        latest.value = uri
    }

    public fun consume(uri: String) {
        latest.compareAndSet(uri, null)
    }
}

/** Whether a typed `http://` server is accepted: true only in debug builds, for the local dev instance. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
public annotation class CleartextAllowed
