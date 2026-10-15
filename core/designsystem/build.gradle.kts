// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.library)
    alias(libs.plugins.aloha.android.compose)
}

dependencies {
    implementation(libs.materialkolor.utilities)
    // the QR codes a phone scans: a profile to follow, a TV to sign in
    implementation(libs.zxing.core)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    testImplementation(libs.junit4)
}
