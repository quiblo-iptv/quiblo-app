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

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
}

/**
 * Aggregate entry point used by CI: `./gradlew detektAll`.
 * Runs detekt across every module without needing to enumerate them in the workflow.
 */
tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt on all modules."
    // Only real modules. `:core`, `:source` and `:feature` exist as projects because their
    // children are nested under them, but they carry no build file and so no detekt task —
    // depending on them by name fails configuration outright.
    dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:detekt" })
}

/**
 * Aggregate entry point used by CI: `./gradlew coverageAll`.
 *
 * Only the parser modules are covered, because AC-NFR-07 only asks for the parsers. They
 * are also the only modules where a coverage number means anything: they are pure
 * functions over text, so a covered line is genuinely an exercised line. Applying the same
 * gate to UI modules would measure how much Compose got instantiated, not how much
 * behaviour got tested, and the number would be gamed within a week.
 */
tasks.register("coverageAll") {
    group = "verification"
    description = "Verifies parser coverage against the AC-NFR-07 threshold."
    dependsOn(":source:m3u:koverVerify", ":source:xtream:koverVerify")
}

/*
 * Attribution, checked against the dependency graph instead of against somebody's memory.
 *
 * `THIRD_PARTY_LICENSES` is hand-written, and the annotation in it — what each component is for
 * — should stay hand-written, because that is the part with judgement in it. What cannot stay
 * hand-written is the *set*: a dependency added in a pull request that nobody remembers to list
 * is attribution missing from every release after it, and no test fails. `agile/009` E2 calls
 * that failure mode silent and permanent, and this is the answer to it.
 *
 * Both applications are read, not one. They ship different graphs — the television pulls
 * `androidx.tv`, the phone does not — and the obligation is per artefact a user installs.
 */
val shippedApplications = listOf(":app", ":app-tv")

/**
 * Every external module that reaches a release build of either application, `group:name`.
 *
 * Read from what each application wrote about itself. A configuration belongs to the project
 * that owns it — reading `:app`'s classpath from here is cross-project resolution and Gradle
 * refuses it — so `licenceModules` runs in each application and this only adds them up.
 */
fun shippedModules(): List<String> =
    shippedApplications
        .flatMap { path ->
            project(path)
                .layout
                .buildDirectory
                .file("reports/licences/modules.txt")
                .get()
                .asFile
                .readLines()
        }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

/**
 * The prefixes the in-app list claims, read out of the source it is written in.
 *
 * A `coordinates` field is prose — `"androidx.compose:*"`, or three groups separated by commas —
 * because it is read by a person on a settings screen. So it is parsed here rather than
 * restructured: making the data machine-shaped would make the screen worse to read, and the
 * screen is the thing with the legal obligation attached.
 */
fun claimedPrefixes(): List<String> {
    val source = file("feature/settings/src/main/kotlin/dev/quiblo/feature/settings/ThirdPartyLicense.kt")
    return Regex("""coordinates = "([^"]+)"""")
        .findAll(source.readText())
        .flatMap { it.groupValues[1].split(",").asSequence() }
        .map { it.trim().removeSuffix("*").removeSuffix(":") }
        .filter { it.isNotEmpty() }
        .toList()
}

/** What ships and is claimed by nothing in the in-app list. */
fun unattributedModules(): List<String> {
    val claimed = claimedPrefixes()
    return shippedModules().filterNot { module -> claimed.any { module.startsWith(it) } }
}

fun inventoryText(): String = buildString {
    appendLine("<!--")
    appendLine("  Quiblo — a free, open source IPTV player.")
    appendLine("  Copyright (C) 2026 The Quiblo Authors")
    appendLine("  Licensed under the GNU General Public License v3.0 or later. See LICENSE.")
    appendLine("-->")
    appendLine()
    appendLine("# Third-party inventory")
    appendLine()
    appendLine("**Generated. Do not edit — run `./gradlew licenceInventory`.**")
    appendLine()
    appendLine("Every external module on the release runtime classpath of either application:")
    appendLine("what is inside `quiblo-<version>.apk` and `quiblo-tv-<version>.apk`, rather than")
    appendLine("what is named in `gradle/libs.versions.toml`. The second list is about forty entries")
    appendLine("long and the first is what the attribution obligation actually attaches to.")
    appendLine()
    appendLine("The reader-facing list, with a note on what each component does, is in the apps'")
    appendLine("own settings screens and in `ThirdPartyLicense.kt`. This file is the check on it:")
    appendLine("`./gradlew licenceCheck` fails when the two disagree.")
    appendLine()
    appendLine("Versions are deliberately omitted. They change on every dependency bump and would")
    appendLine("make this file a merge conflict rather than a record; what matters here is *which*")
    appendLine("components ship.")
    appendLine()
    shippedModules().forEach { appendLine("- `$it`") }
}

tasks.register("licenceInventory") {
    group = "verification"
    description = "Writes docs/LICENSES.md from what the two applications actually ship."
    dependsOn(shippedApplications.map { "$it:licenceModules" })
    doLast { file("docs/LICENSES.md").writeText(inventoryText()) }
}

/**
 * Aggregate entry point used by CI: `./gradlew licenceCheck`.
 *
 * Two failures, and they are different questions. The inventory drifting means the graph changed
 * and nobody regenerated the file. A module attributed by nothing means the graph changed and
 * nobody told the *user* — which is the one with a licence obligation behind it.
 */
tasks.register("licenceCheck") {
    group = "verification"
    description = "Fails when the shipped dependency graph and the in-app licence list disagree."
    dependsOn(shippedApplications.map { "$it:licenceModules" })
    doLast {
        val inventory = file("docs/LICENSES.md")
        if (!inventory.exists() || inventory.readText() != inventoryText()) {
            error(
                "docs/LICENSES.md no longer describes what ships. Run `./gradlew licenceInventory` " +
                    "and read the diff — every line added is a component a user is now installing.",
            )
        }

        val unattributed = unattributedModules()
        if (unattributed.isNotEmpty()) {
            error(
                buildString {
                    appendLine("${unattributed.size} shipped module(s) are attributed by nothing in the app:")
                    unattributed.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine(
                        "Add them to THIRD_PARTY_LICENSES with a note saying what they are for, or " +
                            "widen an existing entry's coordinates if one already covers them in spirit. " +
                            "Apache-2.0 requires its notices to travel with the binary, and a component " +
                            "nobody listed is a notice that did not travel.",
                    )
                },
            )
        }
    }
}
