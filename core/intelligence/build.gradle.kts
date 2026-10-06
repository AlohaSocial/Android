// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.library)
    alias(libs.plugins.aloha.android.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:media"))
    implementation(libs.androidx.core.ktx)
    // the open image classifier every build drafts alt text with; it sends nothing anywhere
    implementation(libs.litert)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
