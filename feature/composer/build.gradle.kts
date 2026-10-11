// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.feature)
    alias(libs.plugins.aloha.screenshot)
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:intelligence"))
    implementation(project(":core:media"))
    implementation(project(":core:nextcloud"))
    implementation(project(":core:sync"))
    // the Authorization header on a Nextcloud preview, which only the Nextcloud's own origin is sent
    implementation(libs.coil.network.okhttp)
    // trimming, shrinking and converting a video before it is uploaded
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:database"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
}
