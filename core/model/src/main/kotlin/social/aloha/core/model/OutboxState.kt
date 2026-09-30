// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/** Where a post in the outbox stands. */
public enum class OutboxState {
    /** Being written, or put aside; never sent on its own. */
    Draft,

    /** Waiting for a network, or for the posts queued before it. */
    Queued,

    /** Going out now; nothing may change it until it is sent or back in the queue. */
    Sending,

    /** The account must sign in again before its queue goes on. */
    Paused,

    /** The server refused it; the writer fixes it in the composer. */
    Failed,
}
