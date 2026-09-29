// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    `kotlin-dsl`
}

group = "social.aloha.buildlogic"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.ktlint.gradle.plugin)
    compileOnly(libs.roborazzi.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "aloha.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "aloha.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "aloha.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "aloha.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "aloha.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "aloha.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("screenshot") {
            id = "aloha.screenshot"
            implementationClass = "ScreenshotConventionPlugin"
        }
        register("architecture") {
            id = "aloha.architecture"
            implementationClass = "ArchitectureConventionPlugin"
        }
    }
}
