// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.feature)
    alias(libs.plugins.aloha.screenshot)
}

dependencies {
    implementation(project(":core:data"))
    // the player's own view: its controls, the speed and track menus, the subtitles
    implementation(libs.androidx.media3.ui)
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:database"))
    testImplementation(libs.kotlinx.coroutines.test)
}
