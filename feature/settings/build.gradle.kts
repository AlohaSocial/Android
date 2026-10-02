// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.feature)
    alias(libs.plugins.aloha.screenshot)
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:sync"))
    implementation(libs.aboutlibraries.core)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
