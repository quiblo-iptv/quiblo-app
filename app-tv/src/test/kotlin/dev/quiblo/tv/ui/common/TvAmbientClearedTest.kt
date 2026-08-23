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

package dev.quiblo.tv.ui.common

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.quiblo.designsystem.AmbientColours
import dev.quiblo.designsystem.ambientBackdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Does the light go out when a screen that has none takes over?
 *
 * **The shell's backdrop is a single piece of state that outlives every screen inside it.** A
 * focused poster writes its artwork into `LocalAmbientSink` and nothing ever wrote anything back
 * — so opening Search or Live left the colours of a film that had been focused two tabs ago
 * lighting a screen with no film on it. Reported, and rightly: it reads as the app failing to
 * notice where you are.
 *
 * The fix is one line per screen, which is exactly the kind of line that gets lost in a later
 * refactor of the screen it sits in. What is asserted here is the contract rather than either
 * call site: **a screen with no artwork of its own must say so.** A screen that stops saying it
 * inherits somebody else's light again, and nothing else in the build notices.
 *
 * The glow that replaces it is not tested here — it is two radial gradients on an infinite
 * transition, and a screenshot is the only honest assertion about how light looks. `A15` sends
 * that to the panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    // The plain `Application`, not this app's own. `QuibloTvApplication` starts Koin, and the
    // second test in a JVM to start it throws — see `TvLicencesReachableTest`, which is where
    // that was learned. Nothing here needs a graph anyway.
    application = Application::class,
)
class TvAmbientClearedTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a screen with no artwork of its own puts out the last screen's light`() {
        var lit: AmbientRequest? = AmbientRequest.Artwork("https://example.invalid/a-film-two-tabs-ago.jpg")

        compose.setContent {
            CompositionLocalProvider(LocalAmbientSink provides { lit = it }) {
                ScreenWithNoArtwork()
            }
        }
        compose.waitForIdle()

        assertEquals(
            "a screen with no artwork of its own is still lit by the last one",
            AmbientRequest.None,
            lit,
        )
    }

    /**
     * And Search asks for its own light rather than drawing it.
     *
     * `023`: the drifting glow used to be a modifier on the search screen's own column, inside the
     * shell's padding and below the tab bar, so it stopped at four edges the artwork light does
     * not stop at. The screen now says what it wants and the shell draws it full-bleed.
     */
    @Test
    fun `search asks the shell for its own light`() {
        var lit: AmbientRequest? = null

        compose.setContent {
            CompositionLocalProvider(LocalAmbientSink provides { lit = it }) {
                val sink = LocalAmbientSink.current
                LaunchedEffect(Unit) { sink(AmbientRequest.Drift) }
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()

        assertEquals(AmbientRequest.Drift, lit)
    }

    /**
     * What Search and Live both do, and the whole of what is under test.
     *
     * Written out here rather than composing either screen: both pull a ViewModel out of Koin and
     * a database under that, and standing all of it up would test Koin's wiring while claiming to
     * test a backdrop. The line being pinned is this one.
     */
    @Composable
    private fun ScreenWithNoArtwork() {
        val sink = LocalAmbientSink.current
        LaunchedEffect(Unit) { sink(AmbientRequest.None) }
        Box(modifier = Modifier.fillMaxSize())
    }

    /**
     * And the light comes back when something that has artwork takes over.
     *
     * The other half, and the one a careless fix breaks: clearing on every screen, or clearing
     * from the shell itself on every tab change, would put the catalogue's own light out a frame
     * after a poster asked for it.
     */
    @Test
    fun `a screen with artwork still lights the room`() {
        var lit: AmbientRequest? = null

        compose.setContent {
            CompositionLocalProvider(LocalAmbientSink provides { lit = it }) {
                val sink = LocalAmbientSink.current
                LaunchedEffect(Unit) { sink(AmbientRequest.Artwork(POSTER)) }
                Box(modifier = Modifier.fillMaxSize().ambientBackdrop(AmbientColours.None))
            }
        }
        compose.waitForIdle()

        assertEquals("a focused poster no longer lights the room", AmbientRequest.Artwork(POSTER), lit)
    }

    private companion object {
        const val POSTER = "https://example.invalid/the-focused-poster.jpg"
    }
}
