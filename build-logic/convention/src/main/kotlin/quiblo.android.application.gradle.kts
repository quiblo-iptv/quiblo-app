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

import com.android.build.api.dsl.ApplicationExtension
import dev.quiblo.buildlogic.catalogInt
import dev.quiblo.buildlogic.configureTests
import dev.quiblo.buildlogic.failOnWarnings
import dev.quiblo.buildlogic.libs

plugins {
    // AGP 9 compiles Kotlin itself; org.jetbrains.kotlin.android must not be applied.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("quiblo.detekt")
}

extensions.configure<ApplicationExtension> {
    compileSdk = catalogInt("compileSdk")

    defaultConfig {
        minSdk = catalogInt("minSdk")
        targetSdk = catalogInt("targetSdk")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Same set as quiblo.android.library. Without these, `:app` has no test framework on
    // its classpath at all, and any test added under app/src/test simply fails to compile.
    add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    add("testImplementation", libs.findLibrary("turbine").get())
    add("testImplementation", libs.findLibrary("mockk").get())
    add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
    add("androidTestImplementation", libs.findLibrary("androidx-test-junit").get())

    // Align app runtime with androidTest: see concurrentFutures in libs.versions.toml.
    add("implementation", libs.findLibrary("androidx-concurrent-futures").get())
    add("implementation", libs.findLibrary("androidx-concurrent-futures-ktx").get())
}

configureTests()

/*
 * What this application actually ships, listed for the attribution check.
 *
 * Here rather than in the root build file because a configuration belongs to the project that
 * owns it: reading `:app`'s release classpath from the root is cross-project resolution, which
 * Gradle refuses outright. So each application answers for itself and the root aggregates —
 * which is also why this lives in the convention plugin both applications already apply, rather
 * than as two copies in two build files that could drift apart the way #015's did.
 */
tasks.register("licenceModules") {
    group = "verification"
    description = "Lists every external module on this application's release runtime classpath."

    val classpath = configurations.named("releaseRuntimeClasspath")
    val output = layout.buildDirectory.file("reports/licences/modules.txt")
    outputs.file(output)

    doLast {
        val modules = classpath.get()
            .incoming
            .resolutionResult
            .allComponents
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .map { "${it.moduleIdentifier.group}:${it.moduleIdentifier.name}" }
            .distinct()
            .sorted()

        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(modules.joinToString(separator = "\n", postfix = "\n"))
        }
    }
}

failOnWarnings()
