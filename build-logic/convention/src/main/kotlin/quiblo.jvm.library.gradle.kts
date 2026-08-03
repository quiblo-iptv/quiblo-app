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

import dev.quiblo.buildlogic.JVM_TARGET
import dev.quiblo.buildlogic.configureTests
import dev.quiblo.buildlogic.enforceNoCompose
import dev.quiblo.buildlogic.libs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("quiblo.detekt")
}

// Target JVM 17 bytecode without demanding a JDK 17 toolchain, so the build runs on any
// JDK 17 or newer without provisioning a second JDK.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(JVM_TARGET)
}

extensions.configure<KotlinJvmProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    add("testImplementation", libs.findLibrary("turbine").get())
    add("testImplementation", libs.findLibrary("mockk").get())
    add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
}

configureTests()

// :core:model and :source:* are plain JVM modules precisely so that Compose and the
// Android framework cannot reach them. Assert it rather than assume it (AC-NFR-06).
enforceNoCompose()
