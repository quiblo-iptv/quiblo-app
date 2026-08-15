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
    id("quiblo.android.core")
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.quiblo.core.database"

    // `MigrationTestHelper` reads the exported schemas out of the assets of whatever context
    // it is given, so the JSON that KSP writes below has to be reachable from the test build
    // — and only from the test build. Putting it in `main` would ship a directory of schema
    // dumps inside both APKs to satisfy a test.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.named("test") { assets.srcDir("$projectDir/schemas") }
}

ksp {
    // Emits the schema JSON that Room migration tests diff against, from M1 onward.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    ksp(libs.room.compiler)

    // Room's MigrationTestHelper wants an instrumentation, and this repository has learned
    // that a migration signed off on reasoning is a migration that empties somebody's
    // favourites on a device. Robolectric supplies the instrumentation on the JVM, so the
    // upgrade path is exercised in CI rather than on a sweep day. MigrationTestHelper is a
    // JUnit 4 rule, hence the vintage engine — the same arrangement `:app-tv` uses.
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    // The queries themselves are SQL, so the only thing that can prove one orders and filters
    // as claimed is SQLite running it. An in-memory Room database on Robolectric is that.
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    // A `PagingSource` returned from a DAO is Room's own type. See `pagedBrowse`.
    api(libs.room.paging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
