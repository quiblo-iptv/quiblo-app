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

package dev.quiblo.core.network.update

/**
 * A released version of this app, in the order releases actually go in.
 *
 * **Comparing the strings is the bug this type exists to prevent.** `"0.9.0" > "0.10.0"` is true
 * of text and false of software, and a check that compares text tells everybody on 0.10.0 that
 * 0.9.0 is newer — forever, because the answer never changes. `docs/RELEASE-MANAGEMENT.md` puts
 * this project on semantic versioning, so the versions are read as semantic versions.
 *
 * **A pre-release sorts below the release it leads to**, which is what the specification says
 * and also what a viewer means: `0.20.0-alpha.1` is not an upgrade from `0.20.0`. Anything after
 * the first three numbers is treated as a pre-release marker and nothing finer is read from it,
 * because this project has never shipped two pre-releases of the same version and a comparison
 * nobody exercises is a comparison nobody has checked.
 */
internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val isPreRelease: Boolean,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int = compareValuesBy(
        this,
        other,
        AppVersion::major,
        AppVersion::minor,
        AppVersion::patch,
        // False sorts below true, and a pre-release sorts below its release — so the flag is
        // inverted rather than compared directly.
        { !it.isPreRelease },
    )

    companion object {
        /**
         * Reads `v1.2.3`, `1.2.3`, `1.2.3-alpha.1` and the shapes in between.
         *
         * @return `null` for anything it cannot read. A tag it does not understand is not an
         *   update — offering one would mean downloading whatever a malformed release pointed at.
         */
        fun parse(raw: String): AppVersion? {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            val core = trimmed.substringBefore('-').substringBefore('+')

            val parts = core.split('.')
            // Dropped rather than defaulted: a part that is not a non-negative number shortens
            // the list, and a list that is not exactly three long is not a version.
            val numbers = parts.mapNotNull { part -> part.toIntOrNull()?.takeIf { it >= 0 } }

            return if (parts.size == PART_COUNT && numbers.size == PART_COUNT) {
                AppVersion(
                    major = numbers[0],
                    minor = numbers[1],
                    patch = numbers[2],
                    // Anything after the numbers is a pre-release marker: `0.20.0-alpha.1` leads
                    // to `0.20.0` rather than following it.
                    isPreRelease = trimmed.length > core.length,
                )
            } else {
                null
            }
        }

        private const val PART_COUNT = 3
    }
}
