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

import dev.quiblo.buildlogic.enforceNoCompose

plugins {
    id("quiblo.android.library")
}

// Android-backed :core:* modules (database, datastore, network, media, data, common).
// They need a Context for Room/DataStore/Media3, but must stay free of Compose so the
// phase-2 TV and desktop frontends can consume them untouched (docs/FREEZE.md §4.1).
enforceNoCompose()
