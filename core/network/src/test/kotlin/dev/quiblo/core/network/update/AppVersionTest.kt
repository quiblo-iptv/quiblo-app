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

package dev.quiblo.core.network.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Release order, which is the one thing the update check cannot get wrong quietly.
 *
 * A wrong comparison does not crash and does not log: it simply tells everybody on the newest
 * build that an older one is available, or tells everybody on an old build that they are current.
 * Both are silent for as long as nobody checks, which is what these are for.
 */
class AppVersionTest {

    @Test
    @DisplayName("0.10.0 is newer than 0.9.0, which comparing the strings would deny")
    fun `ten is newer than nine`() {
        val ten = AppVersion.parse("0.10.0")!!
        val nine = AppVersion.parse("0.9.0")!!

        assertTrue(ten > nine)
        assertTrue("0.10.0" < "0.9.0", "String order would put 0.9.0 first, and does not.")
    }

    @Test
    fun `the v prefix the tags carry is not part of the version`() {
        assertEquals(AppVersion.parse("0.19.0"), AppVersion.parse("v0.19.0"))
    }

    @Test
    fun `equal versions are neither newer nor older`() {
        assertEquals(0, AppVersion.parse("v1.2.3")!!.compareTo(AppVersion.parse("1.2.3")!!))
    }

    /** An alpha of 0.20.0 is not an upgrade from 0.20.0, and is an upgrade from 0.19.0. */
    @Test
    fun `a pre-release sits below its release and above the one before`() {
        val alpha = AppVersion.parse("v0.20.0-alpha.1")!!

        assertTrue(alpha < AppVersion.parse("v0.20.0")!!)
        assertTrue(alpha > AppVersion.parse("v0.19.0")!!)
    }

    @Test
    fun `major beats minor beats patch`() {
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("0.99.99")!!)
        assertTrue(AppVersion.parse("1.1.0")!! > AppVersion.parse("1.0.99")!!)
        assertTrue(AppVersion.parse("1.1.1")!! > AppVersion.parse("1.1.0")!!)
    }

    /**
     * Anything unreadable is refused rather than guessed at.
     *
     * A tag this cannot parse is a release something has gone wrong with, and the alternative to
     * refusing it is downloading whatever a malformed release happens to point at.
     */
    @Test
    fun `a tag that cannot be read is not a version`() {
        listOf("", "v", "latest", "1.2", "1.2.3.4", "1.two.3", "-1.0.0", "nightly-2026-08-17")
            .forEach { assertNull(AppVersion.parse(it), "'$it' must not parse") }
    }
}
