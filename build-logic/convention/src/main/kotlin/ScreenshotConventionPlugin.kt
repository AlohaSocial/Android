// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import social.aloha.buildlogic.library
import social.aloha.buildlogic.libs
import social.aloha.buildlogic.registerScreenshotAggregate

/**
 * Roborazzi screenshot tests on Robolectric (JUnit 4, which Robolectric's runner
 * needs). Reference images live next to the tests in `src/test/screenshots`.
 */
class ScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")
            registerScreenshotAggregate()
            extensions.getByType(CommonExtension::class.java).testOptions.unitTests.isIncludeAndroidResources = true
            // Robolectric's file-descriptor interceptor reaches into java.base on JDK 17+
            tasks.withType(Test::class.java).configureEach {
                jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
            dependencies {
                add("testImplementation", libs.library("junit4"))
                add("testImplementation", libs.library("robolectric"))
                add("testImplementation", libs.library("roborazzi"))
                add("testImplementation", libs.library("roborazzi-compose"))
                add("testImplementation", libs.library("roborazzi-junit-rule"))
                add("testImplementation", libs.library("compose-ui-test-junit4"))
                // the Accessibility Test Framework checks every screenshot test can run
                add("testImplementation", libs.library("compose-ui-test-junit4-accessibility"))
                for (alias in listOf("atf-protobuf-javalite", "atf-jsoup")) {
                    val pinned = libs.library(alias).get()
                    constraints.add("testImplementation", "${pinned.module}:${pinned.version}")
                }
                add("debugImplementation", libs.library("compose-ui-test-manifest"))
            }
        }
    }
}
