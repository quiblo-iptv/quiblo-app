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

package dev.quiblo.core.model

/**
 * A subtitle track that lives beside the video rather than inside it (INC-F10).
 *
 * The engine treats one of these exactly like a text track the container declared, which is why
 * this is worth doing on the existing track seam: once it is loaded, the menu, the selection and
 * the "off" entry are the code that was already there.
 *
 * @property uri where the file is. An `http(s)` URL from a panel, or a `file://` in this app's
 *   cache for one the viewer picked — never the `content://` the picker returned, because that
 *   permission does not outlive the process that was granted it.
 * @property label what the menu calls it, or null when nothing supplied a name. A panel often
 *   sends a subtitle with no label at all, and inventing one here would be an English literal in
 *   a module that has no resources to translate it from — the fault `agile/012` **#017** was. Null
 *   leaves the naming to the player, which does have them.
 * @property language an ISO 639 code where one is known, and null where it is not. Guessing one
 *   from a filename is only done when the platform agrees the guess is a language at all.
 */
data class SubtitleFile(
    val uri: String,
    val label: String?,
    val mimeType: String,
    val language: String? = null,
    val origin: SubtitleOrigin = SubtitleOrigin.PROVIDER,
)

/**
 * Where a subtitle file came from, which decides who may remove it.
 *
 * A viewer can detach the file they picked. They cannot detach one the panel supplies, because
 * it will simply come back with the next details call, and a control that undoes itself is worse
 * than no control.
 */
enum class SubtitleOrigin {
    PROVIDER,
    PICKED,
}
