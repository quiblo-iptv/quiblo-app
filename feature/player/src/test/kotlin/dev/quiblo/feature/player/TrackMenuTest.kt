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

package dev.quiblo.feature.player

import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.TrackOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

private const val OFF = "Off"
private val ADD = TrackMenuAction(TrackMenuActionKind.ADD_SUBTITLE_FILE, "Add a subtitle file…")
private val REMOVE = TrackMenuAction(TrackMenuActionKind.REMOVE_SUBTITLE_FILE, "Remove the added file")

/**
 * What the track menu offers, and — more importantly — what it does not.
 *
 * AC-PLAY-04 has never been run and does not pass: the engine has exposed audio and subtitle
 * tracks since it was written and no screen offered them (`agile/012` #023). These pin the
 * decisions taken while giving it a way in, because each of them is a way to build a menu that
 * technically works and is wrong to use from a sofa.
 */
class TrackMenuTest {

    @Test
    @DisplayName("a single audio track is not a choice, so it is not offered")
    fun `one audio track produces no audio section`() {
        val menu = trackMenu(state(audio = listOf(track("a", "English", selected = true))), OFF)

        assertTrue(menu.isEmpty)
    }

    @Test
    fun `two audio tracks produce a section naming both`() {
        val menu = trackMenu(
            state(
                audio = listOf(
                    track("a", "English", selected = true),
                    track("b", "العربية"),
                ),
            ),
            OFF,
        )

        val audio = menu.sections.single { it.kind == TrackMenuKind.AUDIO }
        assertEquals(listOf("English", "العربية"), audio.entries.map { it.label })
        assertEquals(listOf(true, false), audio.entries.map { it.isSelected })
    }

    @Test
    @DisplayName("subtitles always offer off, and it is selected when none is showing")
    fun `subtitles gain an off entry`() {
        val menu = trackMenu(state(text = listOf(track("s1", "English"))), OFF)

        val subtitles = menu.sections.single { it.kind == TrackMenuKind.SUBTITLES }
        assertEquals(listOf(OFF, "English"), subtitles.entries.map { it.label })
        assertEquals(null, subtitles.entries.first().trackId)
        assertTrue(subtitles.entries.first().isSelected)
    }

    @Test
    fun `off stops being selected once a subtitle track is`() {
        val menu = trackMenu(state(text = listOf(track("s1", "English", selected = true))), OFF)

        val subtitles = menu.sections.single { it.kind == TrackMenuKind.SUBTITLES }
        assertFalse(subtitles.entries.first().isSelected)
        assertTrue(subtitles.entries.last().isSelected)
    }

    @Test
    @DisplayName("audio is never offered an off entry, because silence is not a track")
    fun `audio has no off entry`() {
        val menu = trackMenu(
            state(audio = listOf(track("a", "English", selected = true), track("b", "French"))),
            OFF,
        )

        val audio = menu.sections.single { it.kind == TrackMenuKind.AUDIO }
        assertTrue(audio.entries.all { it.trackId != null })
    }

    @Test
    fun `a stream with neither offers nothing at all`() {
        assertTrue(trackMenu(state(), OFF).isEmpty)
    }

    @Test
    @DisplayName("a film with no subtitle track still offers the way to add one (INC-F10)")
    fun `subtitle actions bring the section into existence`() {
        // The case that matters most and would be easiest to miss: a viewer wants to attach a
        // file exactly when the stream carries nothing, and a section hidden until a track
        // exists is a section hidden when it is needed.
        val menu = trackMenu(state(), OFF, listOf(ADD))

        val subtitles = menu.sections.single { it.kind == TrackMenuKind.SUBTITLES }
        assertTrue(subtitles.entries.isEmpty(), "nothing to turn off, so no off entry")
        assertEquals(listOf(ADD), subtitles.actions)
    }

    @Test
    fun `actions sit beside the tracks, not among them`() {
        val menu = trackMenu(state(text = listOf(track("s1", "English"))), OFF, listOf(ADD, REMOVE))

        val subtitles = menu.sections.single { it.kind == TrackMenuKind.SUBTITLES }
        assertEquals(listOf(OFF, "English"), subtitles.entries.map { it.label })
        assertEquals(listOf(ADD, REMOVE), subtitles.actions)
    }

    @Test
    fun `an action never carries a tick, because it is not a choice`() {
        val menu = trackMenu(state(), OFF, listOf(ADD))

        assertTrue(menu.sections.single().actions.none { action -> action.label.isEmpty() })
        assertTrue(menu.sections.single().entries.none { it.isSelected })
    }

    @Test
    fun `audio is unaffected by subtitle actions`() {
        val menu = trackMenu(state(), OFF, listOf(ADD))

        assertTrue(menu.sections.none { it.kind == TrackMenuKind.AUDIO })
    }

    @Test
    @DisplayName("appearance is offered only while a subtitle is actually showing (INC-F11)")
    fun `appearance sections follow the selection`() {
        val appearance = listOf(TrackMenuSection(TrackMenuKind.SUBTITLE_SIZE, emptyList()))

        val off = trackMenu(state(text = listOf(track("s1", "English"))), OFF, emptyList(), appearance)
        assertTrue(
            off.sections.none { it.kind == TrackMenuKind.SUBTITLE_SIZE },
            "a size chosen with nothing on screen is a size chosen blind",
        )

        val on = trackMenu(
            state(text = listOf(track("s1", "English", selected = true))),
            OFF,
            emptyList(),
            appearance,
        )
        assertTrue(on.sections.any { it.kind == TrackMenuKind.SUBTITLE_SIZE })
    }

    private fun state(
        audio: List<TrackOption> = emptyList(),
        text: List<TrackOption> = emptyList(),
    ) = PlaybackState(audioTracks = audio, textTracks = text)

    private fun track(id: String, label: String, selected: Boolean = false) =
        TrackOption(id = id, label = label, language = null, isSelected = selected)
}
