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

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "quiblo"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":app-tv")

// Pure Kotlin / Android core. Never depends on :feature:* and never imports Compose.
include(":core:model")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:media")
include(":core:data")

// Source layer. MediaSource abstraction plus per-protocol implementations.
include(":source:api")
include(":source:m3u")
include(":source:xtream")
include(":source:tmdb")

// The iptv-org channel reference list. Not a MediaSource: it carries no streams, only
// names and logos for channels a user's own playlist already returned.
include(":source:iptvorg")

// Feature layer. Compose UI, consumes :core:data only.
//
// :feature:designsystem holds what both apps must agree on rather than what either does —
// the corner radius scale today. It is not a feature, and it is here for the same reason
// :feature:browse is: AC-NFR-06 forbids Compose in :core:*, so there is no :core:ui to put
// it in.
include(":feature:designsystem")
//
// :feature:browse holds the list UI that Live, Movies, Series and Favourites all share.
// It lives in the feature layer rather than in :core: because AC-NFR-06 forbids Compose
// in :core:*, which rules out a :core:ui module entirely. The alternative was the same
// screen copied four times.
include(":feature:browse")
include(":feature:sources")
include(":feature:live")
include(":feature:vod")
include(":feature:series")
include(":feature:player")
include(":feature:favorites")
include(":feature:settings")
//
// :feature:sync holds the scheduled work: the four-day catalogue sync and the popular-list check.
// It is in the feature layer rather than in :core: deliberately. WorkManager is Android's
// scheduler and nothing else has one — FREEZE.md §4.1 requires that a desktop or webOS frontend
// be able to consume :core:* untouched, and a :core: module that names WorkManager would force
// the platform's scheduler on a platform that does not have it. It carries no Compose.
include(":feature:sync")
