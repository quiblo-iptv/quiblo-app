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

// A plain JVM module, not an Android one.
//
// Everything here reads text: which direction it runs in, which script it is written in, what
// encoding a subtitle file arrived in. None of it needs the Android framework, and being a JVM
// module is what lets `:source:*` — which are JVM precisely so that Android cannot reach them —
// use the same rules the app does instead of keeping a second copy.
plugins {
    id("quiblo.jvm.library")
}

dependencies {
    api(projects.core.model)
}
