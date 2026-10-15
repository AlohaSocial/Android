// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.application)
    // Compose for TV's own Material 3, not the phone's that the Compose convention brings
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "social.aloha.tv"
    defaultConfig {
        // the phone app's, so Play lists the TV app with it
        applicationId = "social.aloha.android"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.tv.material)
    debugImplementation(libs.compose.ui.tooling)
}
