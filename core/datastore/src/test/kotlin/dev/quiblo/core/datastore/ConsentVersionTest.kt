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

package dev.quiblo.core.datastore

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * When the terms are shown again, and when they are not (`FREEZE.md` Amendment 9).
 *
 * Four cases and every one of them is a decision somebody could reasonably get wrong in the
 * opposite direction, which is the whole reason this is a function rather than an expression
 * inside a `map`.
 */
class ConsentVersionTest {

    @Test
    fun `a fresh install is asked`() {
        assertTrue(needsConsent(accepted = null, current = 1))
    }

    @Test
    fun `an install that accepted this version is not asked again`() {
        assertFalse(needsConsent(accepted = 1, current = 1))
    }

    @Test
    fun `raising the version asks again`() {
        assertTrue(needsConsent(accepted = 1, current = 2))
    }

    /**
     * A downgrade does not re-ask.
     *
     * Somebody running an older build after a newer one has already agreed to terms this build
     * has never heard of. Asking again would be a screen with nothing behind it, and the naive
     * spelling — "accepted != current" — gets exactly this case wrong.
     */
    @Test
    fun `an install carrying a newer version is left alone`() {
        assertFalse(needsConsent(accepted = 3, current = 2))
    }
}
