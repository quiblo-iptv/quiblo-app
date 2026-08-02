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

package dev.vibrato.core.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * M0 smoke test.
 *
 * Proves the JUnit 5 platform is actually wired into a plain-JVM core module, so that a
 * green `./gradlew build` means tests ran rather than that no tests existed. Replaced by
 * real model tests in M1.
 */
class BuildWiringTest {

    @Test
    @DisplayName("JUnit 5 executes in :core:model")
    fun junitPlatformIsWired() {
        assertFalse(false, "JUnit 5 did not execute")
    }
}
