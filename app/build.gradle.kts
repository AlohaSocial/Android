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
    // generated on a device by :benchmark, checked in under src/main/generated/baselineProfiles: both
    // flavours run the same code, so one profile serves every variant
    automaticGenerationDuringBuild = false
    mergeIntoMain = true
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":core:platform"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:media"))
    implementation(project(":feature:composer"))
    implementation(project(":feature:signin"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:thread"))
    implementation(project(":feature:timeline"))
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:database"))
    baselineProfile(project(":benchmark"))
}
