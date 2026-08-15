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

package dev.quiblo.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The year out of a provider's date field, which is a different string on every panel.
 *
 * Worth its own tests because the failure is silent in both directions: a year not found means a
 * detail screen quietly missing a fact, and a wrong year found means a screen stating one.
 */
class ReleaseYearTest {

    @Test
    @DisplayName("reads the year out of every shape a panel sends")
    fun `common formats`() {
        assertEquals(2021, releaseYearIn("2021-10-22"))
        assertEquals(2021, releaseYearIn("2021"))
        assertEquals(2021, releaseYearIn("22/10/2021"))
        assertEquals(2021, releaseYearIn("October 22, 2021"))
    }

    @Test
    @DisplayName("nothing at all is not a year")
    fun `absent and meaningless values`() {
        assertNull(releaseYearIn(null))
        assertNull(releaseYearIn(""))
        assertNull(releaseYearIn("   "))
        // Panels send this for "we do not know", and it must not read as the year zero.
        assertNull(releaseYearIn("0000-00-00"))
        assertNull(releaseYearIn("N/A"))
    }

    @Test
    @DisplayName("a quality tag is not a release year")
    fun `four digits that mean something else`() {
        // The reason for the range check. Without it a film titled by its resolution is
        // mediaeval, and a viewer sees "1080" where the year should be.
        assertNull(releaseYearIn("1080p"))
        assertNull(releaseYearIn("2160"))
        // Adjacent digits are not a year either: 12021 is a typo, not the hundred-and-twentieth
        // century.
        assertNull(releaseYearIn("12021"))
    }

    @Test
    @DisplayName("the first plausible year wins where a string carries more than one number")
    fun `mixed strings`() {
        assertEquals(1999, releaseYearIn("1999-03-31 1080p"))
        // The resolution comes first here and is skipped rather than taken.
        assertEquals(1999, releaseYearIn("1080p 1999"))
    }
}
