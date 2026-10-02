// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

/** Namespace from the Gradle path: `:core:model` becomes `social.aloha.core.model`. */
internal fun Project.alohaNamespace(): String =
    "social.aloha" + path.split(':').filter { it.isNotEmpty() }.joinToString("") { "." + it.replace("-", "") }

internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        namespace = alohaNamespace()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        lint.configureLint(this@configureAndroid)
    }
    configureKotlin()
}

internal fun Lint.configureLint(project: Project) {
    abortOnError = true
    warningsAsErrors = true
    checkDependencies = false
    // test sources are detekt's and ktlint's; lint over them cost a sixth of a full check and found nothing
    ignoreTestSources = true
    // version drift is Renovate's job; these checks need the network and fail offline
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    // a baseline exists only once a finding had to be accepted; lint must never write one itself
    project.file("lint-baseline.xml").takeIf { it.exists() }?.let { baseline = it }
}

internal fun Project.configureKotlin() {
    tasks.withType(KotlinCompilationTask::class.java).configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
            if (this is KotlinJvmCompilerOptions) {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
    if (path.startsWith(":core:")) {
        extensions.getByType(KotlinBaseExtension::class.java).explicitApi()
    }
}
