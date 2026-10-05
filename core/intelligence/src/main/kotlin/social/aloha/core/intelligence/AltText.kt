// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.intelligence

import android.content.res.Resources

/**
 * Alt text put together from what the device found in a picture, and nothing else: blunt, but never
 * wrong about what is there. People are a count; nobody is named, aged or otherwise described.
 */
internal object AltText {
    const val MAXIMUM = 400

    fun assemble(seen: Observations, resources: Resources): String {
        val people = when {
            seen.people == 1 -> resources.getString(R.string.intelligence_one_person)
            seen.people > 1 -> resources.getQuantityString(R.plurals.intelligence_people, seen.people, seen.people)
            else -> null
        }
        val things = seen.subjects.take(SUBJECTS).joinToString(", ").takeIf { it.isNotEmpty() }
        val sentence = when {
            people != null && things != null -> resources.getString(R.string.intelligence_with, people, things)
            people != null -> people
            things != null -> things.replaceFirstChar { it.titlecase() }
            else -> resources.getString(R.string.intelligence_picture)
        }
        return (if (sentence.endsWith(".")) sentence else "$sentence.").take(MAXIMUM)
    }

    private const val SUBJECTS = 3
}
