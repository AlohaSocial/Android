// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

/** What the device keeps to be quick, which can always be fetched again: pictures and server answers. */
public interface DeviceStorage {
    /** How much it takes, in bytes. */
    public suspend fun cacheBytes(): Long

    public suspend fun clear()
}
