// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import social.aloha.buildlogic.library
import social.aloha.buildlogic.libs

/**
 * Room with KSP. Schemas are exported to `schemas/` and committed, so every migration of the
 * durable database is tested against the schema it starts from.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<RoomExtension> { schemaDirectory("$projectDir/schemas") }
            dependencies {
                add("api", libs.library("room-runtime"))
                add("ksp", libs.library("room-compiler"))
                add("testImplementation", libs.library("room-testing"))
            }
        }
    }
}
