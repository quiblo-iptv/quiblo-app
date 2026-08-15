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

package dev.quiblo.tv

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.quiblo.tv.ui.QuibloTvTheme
import dev.quiblo.tv.ui.TvApp

/** The single activity hosting the television UI. */
class TvMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuibloTvTheme {
                /*
                 * Closing means closing, and `finishAndRemoveTask` is why.
                 *
                 * Plain `finish()` ends the activity and leaves the task in the recents list, so
                 * the television's own "recent apps" still offers a card that resumes into a
                 * session the viewer just asked to leave. Removing the task is what makes the
                 * next launch a launch.
                 *
                 * It is not, on its own, enough to bring the profile chooser back — the chosen
                 * profile is cleared in `Application.onCreate`, which does not run again while
                 * the process is cached. The shell signs out before calling this, which is the
                 * half that keeps the promise whether the process dies or not.
                 */
                /*
                 * The content makes room for a keyboard on a handset, and does not on a
                 * television.
                 *
                 * `adjustNothing` in the manifest is measured, not preferred:
                 * `TvSettingsFieldStabilityTest` holds the trace of a settings list chasing a
                 * shrinking viewport four items down before settling, and of the same trace flat
                 * once the window is held still. It is right on a television, where the leanback
                 * keyboard is a full-screen overlay with its own view of what is being typed —
                 * the app's window making room for it buys nothing.
                 *
                 * A phone's keyboard is not an overlay; it covers the bottom of the window, and a
                 * window that refuses to move hides the field being typed into. So the window
                 * stays fixed either way and the *content* insets itself on a handset, which
                 * leaves the measured behaviour on the panel untouched rather than trading it.
                 */
                TvApp(onExit = ::finishAndRemoveTask, insetForKeyboard = !isTelevision())
            }
        }
    }

    /**
     * Whether this is running on a television.
     *
     * Asked of the system rather than inferred from a screen size, because that is the question:
     * a ten-inch tablet in landscape is not a television and a small television is. `uiMode` is
     * what the platform itself uses to decide, and it is what the leanback launcher reads.
     */
    private fun isTelevision(): Boolean =
        (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType ==
            Configuration.UI_MODE_TYPE_TELEVISION
}
