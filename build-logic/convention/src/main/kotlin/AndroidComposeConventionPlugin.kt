// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import social.aloha.buildlogic.library
import social.aloha.buildlogic.libs

/** Compose on the BOM's Material 3 (1.4); Expressive arrives with material3 1.5. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true
            dependencies {
                val bom = platform(libs.library("compose-bom"))
                add("implementation", bom)
                add("testImplementation", bom)
                add("implementation", libs.library("compose-ui"))
                add("implementation", libs.library("compose-material3"))
                add("implementation", libs.library("compose-ui-tooling-preview"))
                add("debugImplementation", libs.library("compose-ui-tooling"))
            }
        }
    }
}
