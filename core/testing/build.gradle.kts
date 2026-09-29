// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.library)
}

dependencies {
    api(libs.okhttp.mockwebserver)
    api(project(":core:datastore"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:network"))
    testImplementation(project(":core:data"))
    testImplementation(project(":core:database"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
