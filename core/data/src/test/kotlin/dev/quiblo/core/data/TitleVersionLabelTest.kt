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

package dev.quiblo.core.data

import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * What each copy of a film is called on the chip that switches to it.
 *
 * **The difference between the names, rather than a parse of them.** A list of known quality tags
 * would be a list that is wrong for the next panel — one writes `4K`, the next `[UHD]`, the next
 * `AR| … MULTI`. What holds across all of them is that the listings of one title share a
 * beginning and differ at the end.
 */
class TitleVersionLabelTest {

    @Test
    @DisplayName("a chip says what tells this copy from the others")
    fun `the label is what the names do not share`() {
        val labels = labelVersions(
            listOf(
                channel(1, "Dune (2021) SD"),
                channel(2, "Dune (2021) FHD"),
                channel(3, "Dune (2021) 4K MULTI-SUB"),
            ),
        ).map { it.label }

        assertEquals(listOf("SD", "FHD", "4K MULTI-SUB"), labels)
    }

    /** The punctuation a provider joins a tag on with is not part of the tag. */
    @Test
    fun `separators are trimmed off the front of a label`() {
        val labels = labelVersions(
            listOf(
                channel(1, "Heat (1995) - HD"),
                channel(2, "Heat (1995) - [4K]"),
            ),
        ).map { it.label }

        assertEquals(listOf("HD", "4K"), labels)
    }

    /**
     * A name that adds nothing keeps all of itself.
     *
     * Two identical listings — a panel that sent the same film twice — would otherwise both get
     * an empty chip, and a chip with nothing on it cannot be chosen deliberately.
     */
    @Test
    fun `a listing that differs in nothing is labelled with its whole name`() {
        val labels = labelVersions(
            listOf(
                channel(1, "Dune (2021)"),
                channel(2, "Dune (2021)"),
            ),
        ).map { it.label }

        assertEquals(listOf("Dune (2021)", "Dune (2021)"), labels)
    }

    /** Names that share no beginning at all keep theirs, rather than being cut at nothing. */
    @Test
    fun `names with nothing in common are labelled in full`() {
        val labels = labelVersions(
            listOf(
                channel(1, "AR| Dune 2021"),
                channel(2, "EN| Dune 2021"),
            ),
        ).map { it.label }

        assertEquals(listOf("AR| Dune 2021", "EN| Dune 2021"), labels)
    }

    /**
     * A bracket cut in half by the shared beginning is not part of the label.
     *
     * A panel that writes the year one way in one listing and another way in the next shares only
     * `Avatar ` between them, so the second listing's label began mid-bracket. Trimming took the
     * `(` off the front, because it was an edge, and left the `)` where it was, because it was
     * not — and the chip read `2009) HD`.
     */
    @Test
    fun `a bracket the cut left without its partner is dropped`() {
        val labels = labelVersions(
            listOf(
                channel(1, "Avatar 2009"),
                channel(2, "Avatar (2009) HD"),
            ),
        ).map { it.label }

        assertEquals(listOf("2009", "2009 HD"), labels)
    }

    /**
     * Brackets that still come in pairs are the provider's own, and are left alone.
     *
     * A pair at the edge of a label is taken by the separator trim, exactly as `- [4K]` is above.
     * This is about the pair in the middle, which nothing else touches and nothing should.
     */
    @Test
    fun `a bracketed tag that survives the cut whole keeps its brackets`() {
        val labels = labelVersions(
            listOf(
                channel(1, "Dune (2021) HD"),
                channel(2, "Dune (2021) 4K (HDR) DUAL"),
            ),
        ).map { it.label }

        assertEquals(listOf("HD", "4K (HDR) DUAL"), labels)
    }

    private fun channel(id: Long, name: String) = Channel(
        id = id,
        sourceId = 1L,
        name = name,
        streamUrl = "https://example.invalid/$id",
        kind = MediaKind.VOD,
    )
}
