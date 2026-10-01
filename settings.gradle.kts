// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

apply(from = "gradle/security-floors.settings.gradle.kts")

rootProject.name = "AlohaSocial"

include(":app")
include(":benchmark")
include(":widget")
listOf(
    "model", "html", "network", "database", "datastore", "data", "sync", "media",
    "designsystem", "ui", "navigation", "nextcloud", "platform", "testing",
).forEach { include(":core:$it") }
listOf(
    "signin", "timeline", "thread", "profile", "notifications", "composer", "search", "explore",
    "lists", "settings", "photos", "video", "shorts", "stories", "mediaviewer", "audio", "news",
    "safety", "moderation", "share", "hashtags", "saved", "conversations",
).forEach { include(":feature:$it") }
