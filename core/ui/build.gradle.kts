// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.android.library)
    alias(libs.plugins.aloha.android.compose)
}

dependencies {
    implementation(project(":core:designsystem"))
    api(project(":core:html"))
    api(project(":core:media"))
}
