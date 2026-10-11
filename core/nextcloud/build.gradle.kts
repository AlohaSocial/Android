// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.library)
    alias(libs.plugins.aloha.android.hilt)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}
