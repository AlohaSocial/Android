// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.application)
    alias(libs.plugins.aloha.android.compose)
    alias(libs.plugins.aloha.android.hilt)
    alias(libs.plugins.aloha.screenshot)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "social.aloha.android"
    defaultConfig {
        applicationId = "social.aloha.android"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
    }
}

baselineProfile {
    // generated on a device by :benchmark, checked in under src/<flavor>/generated/baselineProfiles
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:platform"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    baselineProfile(project(":benchmark"))
}
