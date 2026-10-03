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

package dev.quiblo.feature.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.quiblo.core.model.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Profiles can be deleted and renamed from a screen (`FEAT-039`).
 *
 * `ProfilesViewModel.delete` existed and nothing called it, so `AC-PROF-06` could not be met by
 * anybody holding the phone. These drive the dialogs a person uses and assert what reaches the
 * ViewModel's methods.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class ManageProfilesTest {

    @get:Rule
    val compose = createComposeRule()

    private val sam = Profile(id = 1L, name = "Sam")
    private val alex = Profile(id = 2L, name = "Alex")
    private val guest = Profile(id = 3L, name = "Guest", isGuest = true)

    private val deleted = mutableListOf<Profile>()
    private val renamed = mutableListOf<Pair<Profile, String>>()

    private fun showManager() = compose.setContent {
        ManageProfilesDialog(
            profiles = listOf(sam, alex, guest),
            onRename = { profile, name -> renamed += profile to name },
            onSetAvatar = { _, _ -> },
            onDelete = { deleted += it },
            onDismiss = {},
        )
    }

    @Test
    fun `everybody with a profile is listed, and the guest is not`() {
        showManager()

        compose.onNodeWithText("Sam").assertIsDisplayed()
        compose.onNodeWithText("Alex").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTextCount("Guest") == 0)
    }

    @Test
    fun `a profile can be deleted, after saying what goes with it`() {
        showManager()

        compose.onNodeWithText("Alex").performClick()
        compose.onNodeWithText("Delete this profile").performClick()
        compose.onNodeWithText("Delete Alex?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf(alex), deleted)
    }

    @Test
    fun `keeping a profile at the question deletes nothing`() {
        showManager()

        compose.onNodeWithText("Sam").performClick()
        compose.onNodeWithText("Delete this profile").performClick()
        compose.onNodeWithText("Keep").performClick()

        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `a profile can be renamed`() {
        showManager()

        compose.onNodeWithText("Sam").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Samira")
        compose.onNodeWithText("Save").performClick()

        assertEquals(listOf(sam to "Samira"), renamed)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
