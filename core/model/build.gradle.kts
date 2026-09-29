// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // domain types are persisted as JSON (capabilities on the account row, status payloads in the cache)
    api(libs.kotlinx.serialization.json)
}
