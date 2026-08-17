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
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "dev.quiblo.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.source.api)
    api(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.source.iptvorg)
    implementation(projects.source.m3u)
    implementation(projects.source.tmdb)
    implementation(projects.source.xtream)
    implementation(libs.kotlinx.coroutines.core)
    // `Flow<PagingData<Channel>>` crosses this module's public surface, so `api` and not
    // `implementation`: a feature collecting one has to be able to name the type.
    api(libs.paging.common)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.ktor.client.core)
    implementation(libs.kotlinx.serialization.json)
}

/*
 * Amendment 10's floor, on the module the arithmetic lives in.
 *
 * `coverageAll` covered the two parser modules and nothing else, on the argument that a covered
 * line in a parser is genuinely an exercised line while a covered line in a Compose module
 * measures how much of the framework got instantiated. That argument is right about UI and wrong
 * about this module: the matching, the scoring, the merge rules and the caches here are pure
 * functions over rows, exactly like a parser, and they are where every round since `020` has put
 * its decisions.
 *
 * The bound is 70 rather than the parsers' 80 because 70 is the floor Amendment 10 actually sets,
 * and a number chosen above what the rule asks for is a number somebody will lower quietly. It is
 * a floor to be raised deliberately, not a target to sit on.
 *
 * The dependency-injection module is excluded. It is a list of constructor calls with no branch in
 * it, and the only way to execute one is to stand up Koin — which measures Koin.
 */
kover {
    reports {
        filters {
            excludes {
                classes("dev.quiblo.core.data.di.*")
            }
        }
        verify {
            rule {
                minBound(70)
            }
        }
    }
}

