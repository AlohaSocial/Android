// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

/** A count as a badge shows it; servers cap their unread counts at 99, and so does the badge. */
public fun badgeCount(count: Int): String = if (count > MAXIMUM_BADGE) "$MAXIMUM_BADGE+" else count.toString()

private const val MAXIMUM_BADGE = 99
