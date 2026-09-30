// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.aloha.jvm.library)
}

dependencies {
    // RichText carries the domain's custom emoji, and the cache reads statuses
    api(project(":core:model"))
}
