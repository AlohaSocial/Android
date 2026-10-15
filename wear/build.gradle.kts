// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.application)
    // Wear's own Material 3, not the phone's that the Compose convention brings
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aloha.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "social.aloha.wear"
    defaultConfig {
        // the phone app's: Play lists the watch app with it, and the Data Layer pairs the two by it
        applicationId = "social.aloha.android"
        minSdk = libs.versions.wearMinSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.input)
    implementation(libs.wear.phone.interactions)
    implementation(libs.wear.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
}
