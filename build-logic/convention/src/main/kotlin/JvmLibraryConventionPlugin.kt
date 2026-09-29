// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import social.aloha.buildlogic.configureKotlin
import social.aloha.buildlogic.configureStaticAnalysis
import social.aloha.buildlogic.library
import social.aloha.buildlogic.libs

/**
 * A pure Kotlin module (`:core:model`, `:core:html`, …): no Android SDK on the
 * classpath, so the compiler keeps these modules free of Android APIs.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
            }
            configureKotlin()
            configureStaticAnalysis()
            dependencies {
                add("testImplementation", platform(libs.library("junit-bom")))
                add("testImplementation", libs.library("junit-jupiter"))
                add("testRuntimeOnly", libs.library("junit-platform-launcher"))
            }
            tasks.withType(Test::class.java).configureEach { useJUnitPlatform() }
        }
    }
}
