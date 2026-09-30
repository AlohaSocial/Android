// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import social.aloha.core.network.ApiError

/** Why a fetch did not arrive, as a screen tells it; what is already shown stays. */
public enum class Trouble { Offline, RateLimited, Server }

public val ApiError.trouble: Trouble
    get() = when (this) {
        is ApiError.Transport -> Trouble.Offline
        is ApiError.RateLimited -> Trouble.RateLimited
        else -> Trouble.Server
    }
