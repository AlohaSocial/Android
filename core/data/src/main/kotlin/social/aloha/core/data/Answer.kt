// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult

/** What an answer from the server came to: the value, or why there is none. */
public sealed interface Answer<out T> {
    public data class Got<T>(val value: T) : Answer<T>

    public data class Missed(val error: ApiError) : Answer<Nothing>
}

/** The same answer with its value, if any, turned into something else. */
public inline fun <T, R> Answer<T>.map(transform: (T) -> R): Answer<R> = when (this) {
    is Answer.Got -> Answer.Got(transform(value))
    is Answer.Missed -> this
}

/** Asks [reader]'s server; an account with no client (signed out meanwhile) reads as not found. */
public suspend fun <T> ClientFactory.answer(reader: SignedInAccount, request: ApiRequest<T>): Answer<T> {
    val client = forAccount(reader) ?: return Answer.Missed(ApiError.NotFound)
    return when (val answer = client.execute(request)) {
        is ApiResult.Success -> Answer.Got(answer.value)
        is ApiResult.Failure -> Answer.Missed(answer.error)
    }
}
