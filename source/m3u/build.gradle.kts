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
    id("vibrato.jvm.library")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(projects.source.api)
    implementation(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)
}

// AC-NFR-07: the parser must stay above 80% line coverage, including the malformed-input
// cases in AC-PL-04. This fails the build rather than printing a number, because a
// coverage report nobody is forced to read is a coverage report nobody reads.
kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
