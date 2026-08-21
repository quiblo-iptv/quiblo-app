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

package dev.quiblo.tv.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.quiblo.designsystem.QuibloSplashScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_SDK = 34

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvSplashScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `splash screen displays branding and version number`() {
        compose.setContent {
            QuibloSplashScreen(
                versionName = "0.20.2",
                durationMillis = 5000L,
            )
        }
        compose.waitForIdle()

        compose.onNodeWithText("Quiblo").assertIsDisplayed()
        compose.onNodeWithText("Free & Open Source IPTV").assertIsDisplayed()
        compose.onNodeWithTag("splash_version_text").assertIsDisplayed()
        compose.onNodeWithText("v0.20.2").assertIsDisplayed()
    }

    @Test
    fun `splash screen calls completion callback when duration completes`() {
        var completed = 0
        compose.setContent {
            QuibloSplashScreen(
                versionName = "0.20.2",
                durationMillis = 100L,
                onSplashComplete = { completed++ },
            )
        }
        compose.mainClock.advanceTimeBy(200L)
        compose.waitForIdle()

        assertEquals(1, completed)
    }
}
