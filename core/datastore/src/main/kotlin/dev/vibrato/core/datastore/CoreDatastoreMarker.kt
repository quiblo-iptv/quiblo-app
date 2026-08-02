/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.core.datastore

/**
 * Module marker for `:core:datastore`.
 *
 * Settings and encrypted Xtream credentials land here in M4 (AC-XT-04).
 *
 * The module exists from M0 so the dependency graph and the AC-NFR-06 Compose
 * check are wired and enforced from the first commit (docs/PLAN.md §3).
 */
internal object CoreDatastoreMarker
