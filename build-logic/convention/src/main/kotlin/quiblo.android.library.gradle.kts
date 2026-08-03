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

import com.android.build.api.dsl.LibraryExtension
import dev.quiblo.buildlogic.catalogInt
import dev.quiblo.buildlogic.configureTests
import dev.quiblo.buildlogic.libs

plugins {
    // AGP 9 compiles Kotlin itself; applying org.jetbrains.kotlin.android here is an
    // error since 9.0. Kotlin's jvmTarget follows compileOptions.targetCompatibility.
    id("com.android.library")
    id("quiblo.detekt")
}

extensions.configure<LibraryExtension> {
    compileSdk = catalogInt("compileSdk")

    defaultConfig {
        minSdk = catalogInt("minSdk")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    add("testImplementation", libs.findLibrary("turbine").get())
    add("testImplementation", libs.findLibrary("mockk").get())
    add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
    add("androidTestImplementation", libs.findLibrary("androidx-test-junit").get())
}

configureTests()
