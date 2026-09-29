// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * detekt and ktlint with zero tolerance. A module's baseline lives in
 * `config/detekt/baselines/<path>.xml` and only exists once a finding had to be
 * accepted; CI fails when a pull request adds entries to one.
 */
internal fun Project.configureStaticAnalysis() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    val baselineName = path.trim(':').replace(':', '-')
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baselines/$baselineName.xml")
        parallel = true
    }
    // detekt 1.23 takes its target from the daemon JDK and rejects anything above 22
    tasks.withType(Detekt::class.java).configureEach { jvmTarget = "17" }
    extensions.configure<KtlintExtension> {
        version.set(libs.version("ktlint"))
        android.set(true)
        baseline.set(rootProject.file("config/ktlint/baselines/$baselineName.xml"))
    }
}
