// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import java.util.Locale

/** [code]'s name in the reader's language, capitalised as a list item; null for no code, or one it has no name for. */
public fun languageName(code: String?): String? = code?.let {
    Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { first -> first.titlecase(Locale.getDefault()) }
        .ifEmpty { null }
}
