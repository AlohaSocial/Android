// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import social.aloha.buildlogic.ModuleRules

/**
 * `alohaArchitectureCheck`: every `project(...)` dependency of every module,
 * checked against the allowed module graph. A module that is not in
 * [ModuleRules] fails the check, so a new module cannot join without a rule.
 */
class ArchitectureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) { "aloha.architecture is applied to the root project only" }
        val edges = target.objects.listProperty(String::class.java)
        target.gradle.projectsEvaluated {
            edges.set(
                target.subprojects.flatMap { module ->
                    module.configurations.flatMap { configuration ->
                        val scope = if (configuration.name.contains("test", ignoreCase = true)) "test" else "main"
                        configuration.dependencies.withType(ProjectDependency::class.java)
                            .map { "${module.path} ${it.path} $scope" }
                    }
                }.distinct().sorted(),
            )
        }
        target.tasks.register("alohaArchitectureCheck", ArchitectureCheckTask::class.java) {
            group = "verification"
            description = "Fails on a module dependency the module graph does not allow."
            this.edges.set(edges)
        }
    }
}

abstract class ArchitectureCheckTask : DefaultTask() {
    @get:Input
    abstract val edges: ListProperty<String>

    @TaskAction
    fun check() {
        val violations = edges.get().map { it.split(' ') }
            .filter { (from, to, scope) -> from != to && !ModuleRules.allows(from, to, testOnly = scope == "test") }
            .map { (from, to) -> "$from -> $to" }
            .distinct()
        if (violations.isNotEmpty()) {
            throw GradleException("Module graph violations (allowed edges: ModuleRules.kt):\n" + violations.joinToString("\n"))
        }
        logger.lifecycle("Module graph: ${edges.get().size} edges, all allowed")
    }
}
