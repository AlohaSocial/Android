// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import social.aloha.buildlogic.configureFlavors

plugins {
    alias(libs.plugins.aloha.android.library)
}

android {
    configureFlavors(this)
}
