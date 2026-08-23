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

import dev.quiblo.core.database.dao.CategoryCount
import dev.quiblo.core.database.dao.CategoryOverrideDao
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.entity.CategoryOverrideEntity
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Profile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Where a category sits, once a viewer has said where it should sit.
 *
 * The provider's own order is the default and stays the default. What is under test is the two
 * halves of moving one: that everything unmoved keeps the provider's order behind the moved ones,
 * and that a single move writes down the whole visible order rather than one row.
 *
 * That second half is the one worth a test rather than an argument. Writing only the moved row
 * would leave it carrying a position while its neighbours carried none, and there is no answer to
 * "is third before or after not-moved" — so the first move would scatter the list instead of
 * shifting one shelf by one place.
 */
class CategoryOrderTest {

    private val channelDao: ChannelDao = mockk(relaxed = true)
    private val overrideDao: CategoryOverrideDao = mockk(relaxed = true)
    private val overrides = MutableStateFlow<List<CategoryOverrideEntity>>(emptyList())

    private val activeProfile = MutableStateFlow<Profile?>(VIEWER)

    private val profiles: ProfileRepository = mockk {
        every { activeProfile } returns this@CategoryOrderTest.activeProfile
        every { activeProfileId } returns VIEWER.id
    }

    private val repository = CategoryRepository(
        channelDao = channelDao,
        categoryOverrideDao = overrideDao,
        profiles = profiles,
    )

    private fun catalogueOf(vararg titles: String) {
        every { channelDao.observeCategoriesByKind(SOURCE_ID, MediaKind.VOD.name) } returns
            flowOf(titles.map { CategoryCount(groupTitle = it, itemCount = 1) })
        every { overrideDao.observeForKind(VIEWER.id, MediaKind.VOD.name) } returns overrides
    }

    @Test
    fun `a viewer who has moved nothing gets the provider's own order`() = runTest {
        catalogueOf("Films", "Series", "Documentaries")

        val categories = repository.observeAllCategories(SOURCE_ID, MediaKind.VOD).first()

        assertEquals(listOf("Films", "Series", "Documentaries"), categories.map { it.title })
    }

    @Test
    fun `moved categories come first, and the rest keep the provider's order behind them`() = runTest {
        catalogueOf("Films", "Series", "Documentaries", "Kids")
        overrides.value = listOf(override("Kids", userOrder = 0), override("Series", userOrder = 1))

        val categories = repository.observeAllCategories(SOURCE_ID, MediaKind.VOD).first()

        assertEquals(listOf("Kids", "Series", "Films", "Documentaries"), categories.map { it.title })
    }

    @Test
    fun `moving one category up writes down where every one of them now sits`() = runTest {
        catalogueOf("Films", "Series", "Documentaries")
        val written = slot<List<CategoryOverrideEntity>>()
        coEvery { overrideDao.upsertAll(capture(written)) } returns Unit

        repository.moveCategory(
            kind = MediaKind.VOD,
            originalTitle = "Documentaries",
            ordered = listOf("Films", "Series", "Documentaries"),
            by = -1,
        )

        assertEquals(
            listOf("Films", "Documentaries", "Series"),
            written.captured.sortedBy { it.userOrder }.map { it.originalTitle },
        )
        assertEquals(listOf(0, 1, 2), written.captured.sortedBy { it.userOrder }.map { it.userOrder })
    }

    /** A rename or a hide already on the row is carried across rather than flattened by the move. */
    @Test
    fun `moving a category keeps whatever else the viewer had said about it`() = runTest {
        catalogueOf("Films", "Series")
        overrides.value = listOf(override("Films", customName = "Movies", isHidden = true))
        val written = slot<List<CategoryOverrideEntity>>()
        coEvery { overrideDao.upsertAll(capture(written)) } returns Unit

        repository.moveCategory(
            kind = MediaKind.VOD,
            originalTitle = "Series",
            ordered = listOf("Films", "Series"),
            by = -1,
        )

        val films = written.captured.single { it.originalTitle == "Films" }
        assertEquals("Movies", films.customName)
        assertEquals(true, films.isHidden)
    }

    /**
     * Switching person redraws the shelves, without anything else having to happen.
     *
     * The reads follow the active profile rather than reading its id once. A list that kept the
     * last viewer's hiding until some other event reloaded it would be the one screen in this app
     * where switching profile did not take effect, and the failure is silent — the shelves simply
     * stay wrong.
     */
    @Test
    fun `switching profile redraws the list with that viewer's edits`() = runTest {
        catalogueOf("Films", "Series", "Kids")
        overrides.value = listOf(override("Kids", userOrder = 0))

        val otherOverrides = MutableStateFlow(
            listOf(
                CategoryOverrideEntity(
                    profileId = OTHER.id,
                    kind = MediaKind.VOD.name,
                    originalTitle = "Series",
                    userOrder = 0,
                ),
            ),
        )
        every { overrideDao.observeForKind(OTHER.id, MediaKind.VOD.name) } returns otherOverrides

        assertEquals(
            listOf("Kids", "Films", "Series"),
            repository.observeAllCategories(SOURCE_ID, MediaKind.VOD).first().map { it.title },
        )

        activeProfile.value = OTHER

        assertEquals(
            listOf("Series", "Films", "Kids"),
            repository.observeAllCategories(SOURCE_ID, MediaKind.VOD).first().map { it.title },
        )
    }

    @Test
    fun `a move off either end is not a move`() = runTest {
        catalogueOf("Films", "Series")

        repository.moveCategory(MediaKind.VOD, "Films", listOf("Films", "Series"), by = -1)

        // Nothing captured because nothing was written: `upsertAll` is relaxed and would have
        // answered a call, so the assertion is that the list is unchanged rather than absent.
        assertEquals(emptyList<CategoryOverrideEntity>(), overrides.value)
    }

    private fun override(
        title: String,
        customName: String? = null,
        isHidden: Boolean = false,
        userOrder: Int? = null,
    ) = CategoryOverrideEntity(
        profileId = VIEWER.id,
        kind = MediaKind.VOD.name,
        originalTitle = title,
        customName = customName,
        isHidden = isHidden,
        userOrder = userOrder,
    )

    private companion object {
        const val SOURCE_ID = 4L

        /** Every edit here belongs to somebody, because since schema 23 every edit does. */
        val VIEWER = Profile(id = 3L, name = "A viewer")
        val OTHER = Profile(id = 4L, name = "Somebody else")
    }
}
