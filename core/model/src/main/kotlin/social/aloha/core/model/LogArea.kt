// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

/**
 * What a log line is about, as its tag: `Timber.tag(LogArea.Network.name)`. One fixed list, set on
 * every line, since the tag Timber derives from the call site is a lambda's name in a debug build and
 * an obfuscated letter in a release build.
 */
public enum class LogArea { App, Auth, Network, Sync, Push, Compose, Media, Moderation }
