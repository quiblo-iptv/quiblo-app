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
                TvApp(onExit = ::finishAndRemoveTask)
            }
        }
    }
}
