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
import dev.quiblo.core.common.toMask
import dev.quiblo.core.datastore.PlayerSettingsStore
import dev.quiblo.core.model.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

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
@OptIn(ExperimentalCoroutinesApi::class)
class ScriptFilterRepository(
    private val store: PlayerSettingsStore,
    private val profiles: ProfileRepository,
) {

    /**
     * Per profile since `029` #6, and re-read when the profile changes.
     *
     * It used to be one answer for the television, argued from the catalogue being one catalogue.
     * But what a person can read is a fact about the person: a household with an Arabic speaker
     * and an English one had to choose whose titles disappeared.
     */
    val hiddenScripts: Flow<Set<TitleScript>> = profiles.activeProfile.flatMapLatest { profile ->
        store.hiddenScripts(profile?.id ?: Profile.NONE_ID)
    }

    suspend fun setHidden(script: TitleScript, hidden: Boolean) {
        val profileId = profiles.activeProfileId
        val current = store.hiddenScripts(profileId).first()
        val updated = if (hidden) current + script else current - script
        if (updated != current) store.setHiddenScripts(profileId, updated)
    }

    /** Turns the filter off in one action, for a viewer who cannot find what they hid. */
    suspend fun showEverything() {
        val profileId = profiles.activeProfileId
        if (store.hiddenScripts(profileId).first().isNotEmpty()) {
            store.setHiddenScripts(profileId, emptySet())
        }
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

/**
 * The whole filter, for a list a query did not filter at all.
 *
 * The browse and search paths hand the mask to SQL and only mop up the rows written before
 * schema 19, which is what [hidingUncomputedScripts] is for. A list fetched by id has had no
 * such predicate applied — the ids were chosen by a service's popularity list or by a scoring
 * function, neither of which knows what this viewer reads — so every row still has to be
 * decided here.
 *
 * A row with a computed mask is decided by the mask; a row without one is decided by its name,
 * exactly as every row was before the column existed. An empty hidden set returns the list
 * untouched.
 */
internal fun <T> List<T>.hidingUnreadableScripts(
    hidden: Set<TitleScript>,
    maskOf: (T) -> Int,
    nameOf: (T) -> String,
): List<T> {
    if (hidden.isEmpty()) return this
    val hiddenMask = hidden.toMask()
    return filterNot { item ->
        val mask = maskOf(item)
        if (mask == SCRIPT_MASK_UNKNOWN) {
            nameOf(item).isInHiddenScript(hidden)
        } else {
            mask and hiddenMask != 0
        }
    }
}
