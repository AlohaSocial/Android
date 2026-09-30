// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.library)
}

dependencies {
    api(libs.okhttp.mockwebserver)
    api(project(":core:datastore"))
    // the signed-in fixture builds the real account repository on an in-memory database
    api(project(":core:data"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:network"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
