// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.library)
    alias(libs.plugins.aloha.android.hilt)
}

dependencies {
    implementation(project(":core:data"))
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.unifiedpush.connector)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
}
