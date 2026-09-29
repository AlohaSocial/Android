// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.buildlogic

import org.gradle.api.Project

/**
 * `alohaUnitTests` runs the debug unit tests of this module, whatever its variants are called: the
 * app's are per flavor, most libraries have one, a JVM module has `test`. CI runs this one name, so a
 * module's tests cannot be skipped because its variant task is named differently.
 */
internal fun Project.registerUnitTestAggregate() {
    tasks.register("alohaUnitTests") {
        group = "verification"
        description = "Runs this module's debug unit tests."
        dependsOn(tasks.matching { it.name == "test" || (it.name.startsWith("test") && it.name.endsWith("DebugUnitTest")) })
    }
}

/** `alohaScreenshotTests` verifies this module's Roborazzi images against the debug variants. */
internal fun Project.registerScreenshotAggregate() {
    tasks.register("alohaScreenshotTests") {
        group = "verification"
        description = "Verifies this module's screenshots."
        dependsOn(tasks.matching { it.name.startsWith("verifyRoborazzi") && it.name.endsWith("Debug") })
    }
}
