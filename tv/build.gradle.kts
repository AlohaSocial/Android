// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.application)
    // Compose for TV's own Material 3, not the phone's that the Compose convention brings
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aloha.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aloha.screenshot)
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
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:html"))
    implementation(project(":core:media"))
    // the brand's colours and the QR code a phone scans to sign in
    implementation(project(":core:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.tv.material)
    implementation(libs.timber)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
}
