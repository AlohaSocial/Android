// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import social.aloha.buildlogic.configureAndroid
import social.aloha.buildlogic.configureStaticAnalysis
import social.aloha.buildlogic.library
import social.aloha.buildlogic.libs
import social.aloha.buildlogic.registerUnitTestAggregate

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroid(this)
                testOptions.unitTests.isIncludeAndroidResources = true
            }
            configureStaticAnalysis()
            registerUnitTestAggregate()
            // JUnit 5 for plain unit tests; the vintage engine runs the JUnit 4 tests Robolectric needs
            dependencies {
                add("implementation", libs.library("timber"))
                add("testImplementation", platform(libs.library("junit-bom")))
                add("testImplementation", libs.library("junit-jupiter"))
                add("testRuntimeOnly", libs.library("junit-vintage-engine"))
                add("testRuntimeOnly", libs.library("junit-platform-launcher"))
            }
            tasks.withType(Test::class.java).configureEach {
                useJUnitPlatform()
                // a skeleton module has test resources from AGP but no tests yet
                failOnNoDiscoveredTests.set(false)
                // Robolectric's file-descriptor interceptor reaches into java.base on JDK 17+
                jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
}
