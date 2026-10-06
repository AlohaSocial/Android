// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.buildlogic

/**
 * The allowed module graph. `:app` may depend on anything, a feature
 * only on `:core:*`, nothing on a feature, and each core module only on what its
 * row lists. Any module's tests may use the shared `:core:testing` support.
 */
object ModuleRules {
    private const val MODEL = ":core:model"

    private val core: Map<String, Set<String>> = mapOf(
        MODEL to emptySet(),
        ":core:html" to setOf(MODEL),
        ":core:network" to setOf(MODEL),
        ":core:database" to setOf(MODEL),
        ":core:datastore" to setOf(MODEL),
        ":core:data" to setOf(":core:network", ":core:database", ":core:datastore", MODEL, ":core:html"),
        ":core:sync" to setOf(":core:data", ":core:navigation", MODEL),
        ":core:media" to setOf(MODEL, ":core:network"),
        ":core:designsystem" to emptySet(),
        ":core:ui" to setOf(":core:designsystem", MODEL, ":core:html", ":core:media"),
        ":core:navigation" to setOf(MODEL),
        ":core:nextcloud" to setOf(":core:network", ":core:datastore", MODEL),
        ":core:platform" to setOf(MODEL),
        ":core:intelligence" to setOf(":core:media"),
    )

    private val widget = setOf(":core:data", ":core:navigation", ":core:designsystem", MODEL)

    fun allows(from: String, to: String, testOnly: Boolean = false): Boolean = when {
        from == ":app" -> true
        testOnly && to == ":core:testing" -> true
        from == ":benchmark" -> to == ":app"
        to.startsWith(":feature:") -> false
        from.startsWith(":feature:") -> to.startsWith(":core:")
        from == ":widget" -> to in widget
        from == ":core:testing" -> to.startsWith(":core:") && to != from
        else -> core[from]?.contains(to) ?: throw IllegalStateException("$from has no row in ModuleRules")
    }
}
