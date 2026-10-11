// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.library)
    alias(libs.plugins.aloha.android.hilt)
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:navigation"))
    implementation(project(":core:nextcloud"))
    implementation(project(":core:html"))
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.work)
    // the phone's own search: what the app adds to it, in the system's index from Android 12 on
    implementation(libs.androidx.appsearch)
    implementation(libs.androidx.appsearch.platform.storage)
    implementation(libs.androidx.appsearch.builtin.types)
    implementation(libs.unifiedpush.connector)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
}
