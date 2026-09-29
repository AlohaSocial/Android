// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import social.aloha.buildlogic.library
import social.aloha.buildlogic.libs

/** A `:feature:*` module: an Android library with Compose and Hilt that sees only `:core:*`. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("aloha.android.library")
            pluginManager.apply("aloha.android.compose")
            pluginManager.apply("aloha.android.hilt")
            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:navigation"))
                add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-navigation3"))
            }
        }
    }
}
