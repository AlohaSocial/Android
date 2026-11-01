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
            description = "Fails on a string without a translator comment of its own, or named as another module's."
            files.from(
                target.fileTree(target.rootDir) {
                    include("**/src/main/res/values/strings.xml")
                    exclude("**/build/**")
                },
            )
        }
        val banned = target.tasks.register("alohaBannedCodeCheck", BannedCodeCheckTask::class.java) {
            group = "verification"
            description = "Fails on a `!!` anywhere, or a call detekt bans but cannot see, in production code."
            files.from(
                target.fileTree(target.rootDir) {
                    include("**/*.kt", "**/*.kts")
                    exclude("**/build/**", ".gradle/**")
                },
            )
        }
        target.tasks.register("alohaArchitectureCheck", ArchitectureCheckTask::class.java) {
            group = "verification"
            description = "Fails on a module dependency the module graph does not allow."
            this.edges.set(edges)
            // run with it, so CI's architecture step holds the strings to their comments too
            dependsOn(strings, banned)
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
        // resources merge by name: the app's own, or whichever module wins, silently replaces the other's
        val shared = files.files.flatMap { file ->
            file.readLines().mapNotNull { entry.find(it) }.map { "${it.groupValues[1]} ${it.groupValues[2]}" to file }
        }.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        if (shared.isNotEmpty()) {
            throw GradleException(
                "Strings defined in more than one module, so one replaces the other:\n" +
                    shared.map { (name, where) -> "$name: ${where.joinToString { it.path }}" }.joinToString("\n"),
            )
        }
        logger.lifecycle("Strings: every one has its translator comment and a name no other module uses")
    }
}

/**
 * `alohaBannedCodeCheck`: what `config/detekt/detekt.yml` forbids but detekt cannot see. Its
 * `UnsafeCallOnNullableType` and `ForbiddenMethodCall` need type resolution, which the plain `detekt` task
 * does not have, so they never ran. This reads the text instead: `!!` anywhere, tests included, and in
 * production code (every source set but `test*` and `androidTest*`) the calls detekt lists. Comments and
 * string literals are left alone.
 */
abstract class BannedCodeCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val files: ConfigurableFileCollection

    private class Ban(val pattern: Regex, val reason: String, val testsToo: Boolean = false)

    @TaskAction
    fun check() {
        val bans = listOf(
            Ban(Regex("""[\w)\]}>]!!"""), "`!!`: use checkNotNull or requireNotNull", testsToo = true),
            Ban(Regex("""\brunBlocking\b"""), "runBlocking: blocking bridges only in tests"),
            Ban(Regex("""\bprintln\s*\("""), "println: use Timber, tagged with a LogArea"),
            Ban(Regex("""\bSystem\.currentTimeMillis\b"""), "System.currentTimeMillis: inject a Clock"),
            Ban(
                Regex("""\ballowMainThreadQueries\b"""),
                "allowMainThreadQueries: no database access on the main thread",
            ),
        )
        // every source set of the app but its tests: main, the flavours, the build types
        val tests = Regex("/src/(test|androidTest)[^/]*/")
        val found = files.files.sortedBy { it.path }.flatMap { file ->
            val path = file.invariantSeparatorsPath
            val production = "/src/" in path && !tests.containsMatchIn(path)
            codeOf(file.readText()).lines().withIndex().flatMap { (index, code) ->
                bans.filter { (production || it.testsToo) && it.pattern.containsMatchIn(code) }
                    .map { "${file.path}:${index + 1}: ${it.reason}" }
            }
        }
        if (found.isNotEmpty()) throw GradleException("Banned code:\n" + found.joinToString("\n"))
        logger.lifecycle("Banned code: none in ${files.files.size} Kotlin files")
    }
}

/**
 * [text] with its comments and literals blanked out, its line breaks kept so lines still count: block
 * comments nest, as Kotlin's do, and a raw string runs over lines. A string's `${…}` template goes with it.
 */
private fun codeOf(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    fun blankTo(end: Int) {
        while (i < end && i < text.length) out.append(if (text[i] == '\n') '\n' else ' ').also { i++ }
    }
    fun quoted(quote: Char): Int {
        var j = i + 1
        while (j < text.length && text[j] != quote && text[j] != '\n') j += if (text[j] == '\\') 2 else 1
        return j + 1
    }
    while (i < text.length) {
        when {
            text.startsWith("/*", i) -> {
                var depth = 0
                var j = i
                do {
                    when {
                        text.startsWith("/*", j) -> {
                            depth++
                            j += 2
                        }
                        text.startsWith("*/", j) -> {
                            depth--
                            j += 2
                        }
                        else -> j++
                    }
                } while (depth > 0 && j < text.length)
                blankTo(j)
            }
            text.startsWith("//", i) -> blankTo(text.indexOf('\n', i).takeIf { it >= 0 } ?: text.length)
            text.startsWith("\"\"\"", i) -> {
                val close = text.indexOf("\"\"\"", i + 3).takeIf { it >= 0 } ?: text.length
                var end = close + 3
                while (end < text.length && text[end] == '"') end++
                blankTo(end)
            }
            text[i] == '"' -> blankTo(quoted('"'))
            text[i] == '\'' -> blankTo(quoted('\''))
            else -> out.append(text[i]).also { i++ }
        }
    }
    return out.toString()
}
