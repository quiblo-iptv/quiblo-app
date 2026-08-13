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

import app.cash.turbine.test
import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ScriptFilterTest {

    private fun channel(name: String) = Channel(
        id = name.hashCode().toLong(),
        sourceId = 1L,
        name = name,
        streamUrl = "http://example.invalid/$name",
        kind = MediaKind.VOD,
    )

    private val catalogue = listOf(
        channel("Dune"),
        channel("قطعة واحدة"),
        channel("Приключения"),
        channel("2026"),
    )

    @Test
    fun `nothing is hidden until a script is hidden`() = runTest {
        flowOf(catalogue)
            .hidingUnreadableScripts(flowOf(emptySet())) { it.name }
            .test {
                assertEquals(catalogue.map { it.name }, awaitItem().map { it.name })
                awaitComplete()
            }
    }

    @Test
    fun `an empty hidden set returns the same list, not a copy`() = runTest {
        flowOf(catalogue)
            .hidingUnreadableScripts(flowOf(emptySet())) { it.name }
            .test {
                assertSame(catalogue, awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun `hiding a script drops titles written in it and keeps the rest`() = runTest {
        flowOf(catalogue)
            .hidingUnreadableScripts(flowOf(setOf(TitleScript.Arabic))) { it.name }
            .test {
                assertEquals(listOf("Dune", "Приключения", "2026"), awaitItem().map { it.name })
                awaitComplete()
            }
    }

    @Test
    fun `a title with no letters survives every hidden set`() = runTest {
        flowOf(catalogue)
            .hidingUnreadableScripts(flowOf(TitleScript.offered.toSet())) { it.name }
            .test {
                assertEquals(listOf("2026"), awaitItem().map { it.name })
                awaitComplete()
            }
    }

    @Test
    fun `changing the setting re-emits without the catalogue changing`() = runTest {
        val hidden = MutableStateFlow<Set<TitleScript>>(emptySet())
        // A live catalogue, as Room gives one. `combine` completes when *every* source
        // completes, so a finished `flowOf` upstream would swallow the setting's later edits
        // — which is the shape a test writes by accident and a catalogue screen never has.
        val catalogueFlow = MutableStateFlow(catalogue)
        catalogueFlow
            .hidingUnreadableScripts(hidden) { it.name }
            .test {
                assertEquals(4, awaitItem().size)
                hidden.value = setOf(TitleScript.Cyrillic)
                assertEquals(listOf("Dune", "قطعة واحدة", "2026"), awaitItem().map { it.name })
                hidden.value = emptySet()
                assertEquals(4, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun `search results are filtered in all three kinds at once`() {
        val results = SearchResults(
            live = listOf(channel("BBC One"), channel("قناة")),
            movies = listOf(channel("Dune"), channel("كثيب")),
            series = listOf(channel("مسلسل")),
        )

        val visible = results.hidingUnreadableScripts(setOf(TitleScript.Arabic))

        assertEquals(listOf("BBC One"), visible.live.map { it.name })
        assertEquals(listOf("Dune"), visible.movies.map { it.name })
        assertEquals(emptyList<String>(), visible.series.map { it.name })
    }

    @Test
    fun `search results with nothing hidden are returned untouched`() {
        val results = SearchResults(movies = listOf(channel("Dune")))
        assertSame(results, results.hidingUnreadableScripts(emptySet()))
    }
}
