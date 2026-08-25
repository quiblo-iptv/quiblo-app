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

package dev.quiblo.tv.ui.splash

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Is the splash the loading, or is it in front of it (`030` #5)?
 *
 * The screen used to run for a fixed five seconds and the catalogue was read afterwards, so the
 * viewer paid for both — five seconds of a mark, then a spinner over a database being opened for
 * the first time since the box was switched on. The window here is the fix: the same seconds are
 * spent warming what the first screen reads, and the mark leaves the moment that is done.
 *
 * Both bounds are asserted, because either one alone is a different bug. Without the floor a warm
 * start flashes the brand and vanishes; without the cap a storage read that never answers is a
 * television stuck on a logo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    // A bare Application: the real one starts Koin, which nothing here needs.
    application = Application::class,
)
class TvSplashWaitsForContentTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the splash holds while the catalogue is cold and leaves when it is warm`() {
        var completed = false
        var ready by mutableStateOf(false)

        rule.mainClock.autoAdvance = false
        rule.setContent {
            TvSplashScreen(
                versionName = "1.2.3",
                isReady = ready,
                // Robolectric has no audio device, and the sting is not what is being measured.
                playSound = false,
                onSplashComplete = { completed = true },
            )
        }

        // Well past the floor and well short of the cap. Nothing has said the app is ready, so
        // the splash is still there — which is the half that makes the seconds useful.
        rule.mainClock.advanceTimeBy(3_000)
        assertFalse("the splash left before anything was ready", completed)

        ready = true
        // Long enough for the zoom the screen exits on, which is 700ms.
        rule.mainClock.advanceTimeBy(1_500)
        assertTrue("the splash stayed after the catalogue was warm", completed)
    }

    @Test
    fun `the splash gives up waiting rather than holding the app on a logo`() {
        var completed = false

        rule.mainClock.autoAdvance = false
        rule.setContent {
            TvSplashScreen(
                versionName = "1.2.3",
                isReady = false,
                playSound = false,
                onSplashComplete = { completed = true },
            )
        }

        rule.mainClock.advanceTimeBy(6_000)
        assertTrue("the splash never gave up on a catalogue that never answered", completed)
    }
}
