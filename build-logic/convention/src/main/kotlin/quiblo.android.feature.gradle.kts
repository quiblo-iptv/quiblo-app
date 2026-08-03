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
import dev.quiblo.buildlogic.libs

plugins {
    id("quiblo.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<LibraryExtension> {
    buildFeatures {
        compose = true
    }
}

dependencies {
    val bom = platform(libs.findLibrary("compose-bom").get())
    add("implementation", bom)
    add("androidTestImplementation", bom)

    add("implementation", libs.findLibrary("compose-ui").get())
    add("implementation", libs.findLibrary("compose-ui-graphics").get())
    add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
    add("implementation", libs.findLibrary("compose-material3").get())
    add("implementation", libs.findLibrary("compose-material-icons").get())
    add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
    add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-ktx").get())
    add("implementation", libs.findLibrary("androidx-navigation-compose").get())
    add("implementation", libs.findLibrary("androidx-activity-compose").get())
    // Koin artifacts carry no version of their own; the BOM supplies them.
    add("implementation", platform(libs.findLibrary("koin-bom").get()))
    add("implementation", libs.findLibrary("koin-android").get())
    add("implementation", libs.findLibrary("koin-androidx-compose").get())

    add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
    add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())

    // JUnit 5, coroutines-test, Turbine and MockK come from quiblo.android.library.
    add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit4").get())
}
