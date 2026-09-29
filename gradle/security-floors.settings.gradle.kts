// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

// Minimum versions for vulnerable transitive dependencies of the build tooling
// (Android Gradle plugin, Android Lint, ktlint, androidx.benchmark). A request
// below the floor is raised to it before resolution, so the old version is never
// downloaded; a newer request is left alone. Applied to every configuration and
// to the plugin classpath. Drop an entry once no tool requests the old version.
val floors = mapOf(
    "ch.qos.logback:logback-classic" to "1.5.34", // GHSA-jhq6-gfmj-v8fx, GHSA-p47f-322f-whfh, GHSA-qqpg-mvqg-649v
    "ch.qos.logback:logback-core" to "1.5.34",
    "com.squareup.okio:okio" to "3.4.0", // GHSA-w33c-445m-f8w7
    "com.squareup.wire:wire-runtime" to "6.4.5", // GHSA-9rm7-3qhh-h2mc
    "com.squareup.wire:wire-runtime-jvm" to "6.4.5",
    "org.apache.commons:commons-lang3" to "3.18.0", // GHSA-j288-q9x7-2f5v
    "org.apache.httpcomponents:httpclient" to "4.5.14", // GHSA-7r82-7xv7-xcpj
    "org.bitbucket.b_c:jose4j" to "0.9.6", // GHSA-3677-xxcr-wjqv
    "org.bouncycastle:bcpkix-jdk18on" to "1.85", // GHSA-wg6q-6289-32hp
    "org.bouncycastle:bcprov-jdk18on" to "1.85", // GHSA-9pwp-9qqc-pr26, GHSA-c3fc-8qff-9hwx, GHSA-qp49-qgx5-5m26
    "org.bouncycastle:bcutil-jdk18on" to "1.85",
    "org.jdom:jdom2" to "2.0.6.1", // GHSA-2363-cqg2-863c
    "org.jetbrains.kotlin:kotlin-gradle-plugin" to "2.4.20", // GHSA-r937-wjx7-w2jp
)

fun isBelow(version: String, floor: String): Boolean {
    val a = version.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val b = floor.split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d < 0
    }
    return false
}

check(isBelow("1.80.2", "1.85") && isBelow("2.0.6", "2.0.6.1") && !isBelow("3.17.0", "3.4.0") && !isBelow("1.85", "1.85"))

gradle.allprojects {
    listOf(buildscript.configurations, configurations).forEach { container ->
        container.configureEach {
            resolutionStrategy.eachDependency {
                val floor = floors["${requested.group}:${requested.name}"]
                val version = requested.version
                if (floor != null && version != null && isBelow(version, floor)) {
                    useVersion(floor)
                    because("security floor for a vulnerable transitive dependency")
                }
            }
        }
    }
}
