// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.feature)
    alias(libs.plugins.aloha.screenshot)
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:database"))
    testImplementation(libs.kotlinx.coroutines.test)
}
