/*
 * Quiblo — a free, open source IPTV player.
 * Copyright (C) 2026 The Quiblo Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.quiblo.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/** The single source of truth for dependency versions. See gradle/libs.versions.toml. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Reads an integer version (compileSdk, minSdk, ...) from the version catalog. */
fun Project.catalogInt(alias: String): Int =
    libs.findVersion(alias).get().requiredVersion.toInt()

/** The JVM target every module compiles against. */
const val JVM_TARGET = 17

/**
 * Shared test configuration for every module.
 *
 * Note for Windows: AGP launches unit tests with `-Djava.library.path` copied from the
 * Gradle daemon's own library path, which the JVM derives from `PATH` at startup. A stray
 * double quote anywhere in the system `PATH` therefore breaks command-line tokenisation
 * and the test JVM dies with "Could not find or load main class <fragment of your PATH>".
 * No task-level configuration can repair that — the value is already mangled before the
 * build starts. Fix the `PATH` entry itself, then restart the daemon.
 */
fun Project.configureTests() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

/**
 * Enforces architectural invariant 1 of docs/FREEZE.md and AC-NFR-06: no Compose in
 * `:core:*` or `:source:*`.
 *
 * The `:core:model` and `:source:*` modules are plain JVM modules, so Compose cannot
 * reach them structurally. The Android `:core:*` modules could pick it up transitively,
 * so this asserts on the resolved compile classpath rather than trusting convention.
 */
fun Project.enforceNoCompose() {
    val checkNoCompose = tasks.register("checkNoCompose") {
        group = "verification"
        description = "Fails if any Compose artifact reaches this module's compile classpath (AC-NFR-06)."
        doLast {
            val offenders = configurations
                .filter { it.isCanBeResolved && it.name.endsWith("CompileClasspath") }
                .flatMap { configuration ->
                    runCatching {
                        configuration.incoming.resolutionResult.allDependencies
                            .mapNotNull { it.requested as? ModuleComponentSelector }
                            .filter { it.group.startsWith("androidx.compose") || it.group.startsWith("org.jetbrains.compose") }
                            .map { "${configuration.name} -> ${it.group}:${it.module}" }
                    }.getOrDefault(emptyList())
                }
                .distinct()
                .sorted()

            if (offenders.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine("AC-NFR-06 violation in $path: Compose must never reach :core:* or :source:*.")
                        appendLine("This is an architectural invariant (docs/FREEZE.md §4.1), not a style preference:")
                        appendLine("the phase-2 TV and desktop frontends consume these modules unchanged.")
                        appendLine("Offending dependencies:")
                        offenders.forEach { appendLine("  - $it") }
                    }
                )
            }
        }
    }
    tasks.named("check") { dependsOn(checkNoCompose) }
}
