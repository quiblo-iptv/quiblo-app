/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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
