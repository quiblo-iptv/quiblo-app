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
}

configureTests()
