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

import dev.quiblo.core.common.SCRIPT_MASK_UNKNOWN
import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.ChannelTitle
import dev.quiblo.core.model.MediaKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * What the migration deliberately did not do.
 *
 * `MIGRATION_18_19` adds three columns and reads nothing, because a migration runs on the first
 * access to the database with every screen waiting behind it. This is the same work done
 * afterwards, off the main thread, while the app is usable — and what is asserted is that it
 * computes the same two answers `Channel.toEntity` computes at import, so a catalogue upgraded
 * and a catalogue re-imported end up identical.
 */
class CatalogueIdentityBackfillTest {

    private val channelDao: ChannelDao = mockk(relaxed = true)

    private val backfill = CatalogueIdentityBackfill(
        channelDao = channelDao,
        // The default pool is what ships, and nothing here is timing-dependent.
        dispatcher = Dispatchers.Default,
    )

    @Test
    @DisplayName("a title's identity and its scripts are worked out the way an import works them out")
    fun `writes the cleaned identity and the script mask`() = runTest {
        val title = slot<String>()
        val year = slot<Int>()
        val mask = slot<Int>()
        givenWaiting(ChannelTitle(id = 1L, name = "Fargo (1996) [FHD]", kind = MediaKind.VOD.name))
        coEvery { channelDao.setIdentity(1L, capture(title), capture(year), capture(mask)) } returns Unit

        assertEquals(1, backfill.run())

        // The quality tag and the brackets are gone, the year is a field rather than words, and
        // the mask says Latin — which is exactly what `Channel.toEntity` would have stored.
        assertEquals("fargo", title.captured)
        assertEquals(1996, year.captured)
        assertEquals(TitleScript.Latin.bit, mask.captured)
    }

    /**
     * A title in two writing systems carries both bits, not the first one it happens to start in.
     *
     * The first-letter rule was the original design and the reported defect: a catalogue is full
     * of Arabic titles a provider has prefixed in English, and every one of them came back for a
     * viewer who had asked not to be shown Arabic. The mask is the stored form of the rule that
     * replaced it, so it has to carry the same answer.
     */
    @Test
    fun `a title in two scripts records both of them`() = runTest {
        val mask = slot<Int>()
        givenWaiting(ChannelTitle(id = 1L, name = "HD مسلسل الاختيار", kind = MediaKind.SERIES.name))
        coEvery { channelDao.setIdentity(1L, any(), any(), capture(mask)) } returns Unit

        backfill.run()

        assertEquals(TitleScript.Latin.bit or TitleScript.Arabic.bit, mask.captured)
    }

    /** A trailing bracketed tag says what was done to a title, not what the title is. */
    @Test
    fun `a trailing dub tag does not put a title in its script`() = runTest {
        val mask = slot<Int>()
        givenWaiting(ChannelTitle(id = 1L, name = "Oppenheimer [عربي]", kind = MediaKind.VOD.name))
        coEvery { channelDao.setIdentity(1L, any(), any(), capture(mask)) } returns Unit

        backfill.run()

        assertEquals(TitleScript.Latin.bit, mask.captured)
    }

    /**
     * A title that cleans away to nothing keeps the blank identity, and that is deliberate.
     *
     * Blank is the existing "there is nothing here worth looking up" value. It is *not* a key,
     * and the genre join excludes it for that reason — filing a hundred such rows under whatever
     * the one blank cache row carries would be the loudest possible wrong answer.
     */
    @Test
    fun `a bare language tag is left with no identity`() = runTest {
        val title = slot<String>()
        givenWaiting(ChannelTitle(id = 1L, name = "AR", kind = MediaKind.VOD.name))
        coEvery { channelDao.setIdentity(1L, capture(title), any(), any()) } returns Unit

        backfill.run()

        assertEquals("", title.captured)
    }

    /**
     * It walks batch by batch until nothing is left, and needs no cursor to do it.
     *
     * Filling a row takes it out of the query's own predicate, so the next call returns the next
     * unfilled rows on its own. An offset over a predicate that shrinks underneath it would skip
     * rows, which is the bug this shape cannot have.
     */
    @Test
    fun `keeps asking until the query has nothing left`() = runTest {
        coEvery { channelDao.titlesWithoutIdentity(SCRIPT_MASK_UNKNOWN, any()) } returnsMany listOf(
            listOf(ChannelTitle(id = 1L, name = "Heat", kind = MediaKind.VOD.name)),
            listOf(ChannelTitle(id = 2L, name = "Fargo", kind = MediaKind.SERIES.name)),
            emptyList(),
        )

        assertEquals(2, backfill.run())

        coVerify(exactly = 1) { channelDao.setIdentity(1L, "heat", 0, any()) }
        coVerify(exactly = 1) { channelDao.setIdentity(2L, "fargo", 0, any()) }
    }

    /**
     * A catalogue already carrying its identities costs one query and no writes.
     *
     * This runs at every launch, so the second launch has to be free. A fresh install never has
     * work here either: rows written by `Channel.toEntity` arrive already computed.
     */
    @Test
    @DisplayName("the second launch does no work at all")
    fun `a filled catalogue writes nothing`() = runTest {
        coEvery { channelDao.titlesWithoutIdentity(SCRIPT_MASK_UNKNOWN, any()) } returns emptyList()

        assertEquals(0, backfill.run())

        coVerify(exactly = 1) { channelDao.titlesWithoutIdentity(SCRIPT_MASK_UNKNOWN, any()) }
        coVerify(exactly = 0) { channelDao.setIdentity(any(), any(), any(), any()) }
    }

    private fun givenWaiting(vararg rows: ChannelTitle) {
        coEvery { channelDao.titlesWithoutIdentity(SCRIPT_MASK_UNKNOWN, any()) } returnsMany listOf(
            rows.toList(),
            emptyList(),
        )
    }
}
