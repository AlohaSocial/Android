// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
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
        val strings = target.tasks.register("alohaStringsCheck", StringsCheckTask::class.java) {
            group = "verification"
            description = "Fails on a string without a translator comment of its own."
            files.from(
                target.fileTree(target.rootDir) {
                    include("**/src/main/res/values/strings.xml")
                    exclude("**/build/**")
                },
            )
        }
        target.tasks.register("alohaArchitectureCheck", ArchitectureCheckTask::class.java) {
            group = "verification"
            description = "Fails on a module dependency the module graph does not allow."
            this.edges.set(edges)
            // run with it, so CI's architecture step holds the strings to their comments too
            dependsOn(strings)
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

/**
 * `alohaStringsCheck`: every string and plural a translator will see has a comment of its own directly
 * above it, which translation tools show beside it; a comment over a group reaches only the first.
 */
abstract class StringsCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val files: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val entry = Regex("""^\s*<(string|plurals) name="([^"]+)"([^>]*)>""")
        val bare = files.files.sortedBy { it.path }.flatMap { file ->
            val lines = file.readLines()
            lines.withIndex().mapNotNull { (index, line) ->
                val match = entry.find(line) ?: return@mapNotNull null
                val translatable = !match.groupValues[3].contains("translatable=\"false\"")
                val commented = lines.getOrNull(index - 1)?.trim()?.endsWith("-->") == true
                "${file.path}: ${match.groupValues[2]}".takeIf { translatable && !commented }
            }
        }
        if (bare.isNotEmpty()) {
            throw GradleException("Strings without a translator comment above them:\n" + bare.joinToString("\n"))
        }
        logger.lifecycle("Strings: every one has its translator comment")
    }
}
