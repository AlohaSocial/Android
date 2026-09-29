// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import social.aloha.buildlogic.configureAndroid
import social.aloha.buildlogic.configureFlavors
import social.aloha.buildlogic.configureStaticAnalysis
import social.aloha.buildlogic.libs
import social.aloha.buildlogic.version

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
                configureFlavors(this)
                buildTypes.getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    // CI's emulator job installs release builds to test the R8 output
                    if (providers.gradleProperty("aloha.signReleaseWithDebugKey").orNull == "true") {
                        signingConfig = signingConfigs.getByName("debug")
                    }
                }
            }
            configureStaticAnalysis()
        }
    }
}
