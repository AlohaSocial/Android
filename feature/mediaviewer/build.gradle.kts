// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.feature)
    alias(libs.plugins.aloha.screenshot)
}

dependencies {
    implementation(project(":core:data"))
    // a video in the viewer, with the player's own controls
    implementation(libs.androidx.media3.ui)
    testImplementation(project(":core:testing"))
}
