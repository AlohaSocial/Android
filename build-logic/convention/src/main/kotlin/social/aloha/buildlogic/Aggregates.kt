// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * `alohaUnitTests` runs the debug unit tests of this module, whatever its variants are called: the
 * app's are per flavor, most libraries have one, a JVM module has `test`. CI runs this one name, so a
 * module's tests cannot be skipped because its variant task is named differently.
 */
internal fun Project.registerUnitTestAggregate() {
    tasks.register("alohaUnitTests") {
        group = "verification"
        description = "Runs this module's debug unit tests."
        dependsOn(taskNames { it == "test" || (it.startsWith("test") && it.endsWith("DebugUnitTest")) })
    }
}

/** `alohaScreenshotTests` verifies this module's Roborazzi images against the debug variants. */
internal fun Project.registerScreenshotAggregate() {
    tasks.register("alohaScreenshotTests") {
        group = "verification"
        description = "Verifies this module's screenshots."
        dependsOn(taskNames { it.startsWith("verifyRoborazzi") && it.endsWith("Debug") })
    }
}

/**
 * The names of this module's tasks that match [predicate], read without realizing any task, so a
 * task whose configuration fails, such as AGP's `generate<Variant>ComposePreviewRunfiles`, is not
 * configured unless it runs.
 */
private fun Project.taskNames(predicate: (String) -> Boolean): Provider<List<String>> =
    provider { tasks.names.filter(predicate) }
