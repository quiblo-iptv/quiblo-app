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

package dev.quiblo.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.model.Appearance
import dev.quiblo.designsystem.QuibloSplashScreen
import dev.quiblo.player.ui.ConsentGate
import dev.quiblo.player.ui.ProfileGate
import dev.quiblo.player.ui.QuibloApp
import dev.quiblo.player.ui.theme.QuibloTheme
import org.koin.android.ext.android.inject

/**
 * The single activity hosting the whole Compose UI.
 */
class MainActivity : ComponentActivity() {

    private val appearanceRepository: PlayerSettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Saved, not remembered: the activity is recreated on a rotation and on a theme
            // change, and a splash that replays every time one happens is a five-second wall in
            // front of an app the viewer is already inside.
            var showSplash by rememberSaveable { mutableStateOf(true) }

            // Collected here rather than inside the theme so the whole tree recomposes
            // against one value, and a change applies without restarting the app.
            val appearance by appearanceRepository.appearance.collectAsStateWithLifecycle(Appearance())
            QuibloTheme(appearance = appearance) {
                Crossfade(targetState = showSplash, label = "mainSplashCrossfade") { inSplash ->
                    if (inSplash) {
                        QuibloSplashScreen(
                            versionName = BuildConfig.VERSION_NAME,
                            onSplashComplete = { showSplash = false },
                        )
                    } else {
                        ConsentGate {
                            ProfileGate { QuibloApp() }
                        }
                    }
                }
            }
        }
    }
}
