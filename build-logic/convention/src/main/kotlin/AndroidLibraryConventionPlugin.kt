// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import social.aloha.buildlogic.configureAndroid
import social.aloha.buildlogic.configureStaticAnalysis

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroid(this)
                testOptions.unitTests.isIncludeAndroidResources = true
            }
            configureStaticAnalysis()
        }
    }
}
