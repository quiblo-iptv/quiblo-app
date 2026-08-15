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

package dev.quiblo.core.data

import dev.quiblo.core.common.SCRIPT_MASK_UNKNOWN
import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.common.isInHiddenScript
import dev.quiblo.core.datastore.PlayerSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Which writing systems the viewer has said they do not read (INC-F14).
 *
 * The setting is a subtraction, not a selection. "Show only English" promises something the
 * data cannot deliver — an Egyptian film released under a transliterated Latin title is
 * indistinguishable from an American one by anything in a playlist — and a promise like that
 * hides titles the viewer wanted with no way to notice. "Hide titles written in a script I do
 * not read" is a promise the detector keeps exactly, every time it fires.
 *
 * Nothing is hidden until the viewer hides it.
 */
class ScriptFilterRepository(private val store: PlayerSettingsStore) {

    val hiddenScripts: Flow<Set<TitleScript>> = store.hiddenScripts

    suspend fun setHidden(script: TitleScript, hidden: Boolean) {
        val current = store.hiddenScripts.first()
        val updated = if (hidden) current + script else current - script
        if (updated != current) store.setHiddenScripts(updated)
    }

    /** Turns the filter off in one action, for a viewer who cannot find what they hid. */
    suspend fun showEverything() {
        if (store.hiddenScripts.first().isNotEmpty()) store.setHiddenScripts(emptySet())
    }
}

/**
 * Drops the items whose name is written in a hidden script, and re-emits when that set changes.
 *
 * Applied to the catalogue and to search, and deliberately **not** to favourites or to watch
 * history: those hold titles this viewer chose, and a filter that removes something a person
 * picked by hand is not a filter, it is a loss. The setting is about what the catalogue offers,
 * not about what they have already decided they want.
 *
 * An empty hidden set returns the list untouched, so the common case allocates nothing.
 */
fun <T> Flow<List<T>>.hidingUnreadableScripts(
    hiddenScripts: Flow<Set<TitleScript>>,
    nameOf: (T) -> String,
): Flow<List<T>> = combine(hiddenScripts) { items, hidden ->
    if (hidden.isEmpty()) items else items.filterNot { nameOf(it).isInHiddenScript(hidden) }
}

/**
 * The same filter, for the rows a query could not decide about on its own.
 *
 * A catalogue written before schema 19 carries [SCRIPT_MASK_UNKNOWN] where its scripts should be,
 * and the browse and search queries deliberately let those rows through rather than guess: an
 * unknown mask has every bit set, so filtering on it would hide the entire catalogue, and an
 * empty one would hide none of it. Neither is what the viewer asked for.
 *
 * So the rows that have a mask are decided in SQL — which is the whole point of the column — and
 * the ones that do not are decided here, exactly as every row was before. `CatalogueIdentityBackfill`
 * empties this branch in the background; on a fresh install it is empty from the start, and once
 * a catalogue is filled this walks a list and touches nothing.
 */
internal fun <T> List<T>.hidingUncomputedScripts(
    hidden: Set<TitleScript>,
    maskOf: (T) -> Int,
    nameOf: (T) -> String,
): List<T> {
    if (hidden.isEmpty()) return this
    return filterNot { maskOf(it) == SCRIPT_MASK_UNKNOWN && nameOf(it).isInHiddenScript(hidden) }
}
