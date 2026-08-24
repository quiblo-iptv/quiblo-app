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

import dev.quiblo.core.database.dao.ChannelLogoDao
import dev.quiblo.core.database.entity.ChannelLogoEntity
import dev.quiblo.core.datastore.ChannelLogoStore
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Profile
import dev.quiblo.source.iptvorg.IptvOrgClient
import dev.quiblo.source.iptvorg.iptvOrgMatchKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fills in logos for live channels whose playlist supplied none.
 *
 * Optional, and off unless the user turns it on (AC-NFR-03) — see [ChannelLogoStore].
 *
 * The reference list is one large static file rather than a per-channel endpoint, so the
 * shape of this cache is the opposite of the film metadata one: a single download, held in
 * full, refreshed rarely. Downloading it once per channel is not an option the API offers,
 * and downloading it once per session would be several megabytes to answer a question about
 * a logo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelLogoRepository(
    private val client: IptvOrgClient,
    private val store: ChannelLogoStore,
    private val dao: ChannelLogoDao,
    private val profiles: ProfileRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Whether this viewer wants missing logos filled in. Per profile since `029` #6.
     *
     * The cached index behind it is not — it is one file shared by the television — so switching
     * to a profile that has this off stops the lookups without discarding what was downloaded.
     */
    val isEnabled: Flow<Boolean> = profiles.activeProfile.flatMapLatest { profile ->
        store.isEnabled(profile?.id ?: Profile.NONE_ID)
    }

    /**
     * One download at a time.
     *
     * A browse screen asks about every visible row at once, and the first thing they all
     * find is an empty index. Without this, opening the Live tab on a fresh install starts
     * a dozen downloads of the same several-megabyte file.
     */
    private val refreshLock = Mutex()

    /**
     * Artwork for [channel], or null.
     *
     * Null covers every reason equally — the feature is off, the index is empty, the
     * download failed, the channel is not in the list — because the caller's response to
     * all of them is to show the placeholder it was already showing.
     *
     * Only ever called for a channel that has no logo of its own. The playlist's artwork is
     * the artwork for the stream the user is actually paying for, and replacing it with a
     * guess from a public list would be a downgrade dressed as a feature.
     */
    suspend fun logoFor(channel: Channel): String? {
        if (!store.isEnabledNow(profiles.activeProfileId)) return null
        if (!ensureIndex()) return null

        // The provider's own id first. Many playlists set `tvg-id` to exactly the iptv-org
        // identifier — that is where the convention comes from — and an exact identifier
        // beats any amount of clever name matching.
        val byId = channel.tvgId?.takeIf { it.isNotBlank() }?.let { dao.logoFor(it.lowercase()) }
        if (byId != null) return byId

        return iptvOrgMatchKey(channel.name).takeIf { it.isNotBlank() }?.let { dao.logoFor(it) }
    }

    /**
     * Makes sure there is an index to query, downloading one if there is not.
     *
     * @return whether anything is available to look up afterwards.
     */
    private suspend fun ensureIndex(): Boolean = refreshLock.withLock {
        val hasIndex = dao.count() > 0
        val age = now() - store.lastRefreshedAt()
        if (hasIndex && age < INDEX_TTL_MILLIS) return@withLock true

        val logos = client.fetchLogoIndex()
        if (logos.isNullOrEmpty()) {
            // A failed refresh leaves a stale index in place rather than clearing it. Last
            // month's logos are right for almost every channel; no logos are right for none.
            return@withLock hasIndex
        }

        dao.replaceAll(logos.map { ChannelLogoEntity(matchKey = it.matchKey, logoUrl = it.logoUrl) })
        store.markRefreshed(now())
        true
    }

    /**
     * Turns the feature on or off.
     *
     * Switching it off drops the cached index as well. Someone turning this off is asking
     * the app to stop using a third party's data, and keeping several megabytes of it on
     * their device to serve from would be answering a different question.
     *
     * **The switch is this profile's and the index is the television's**, so switching off also
     * costs another profile that still wants logos its download. That is the right way round: the
     * promise this setting stands for is about data on the device, and honouring it only for
     * whoever happens to be watching would not be honouring it. The other profile's next browse
     * fetches the file again.
     */
    suspend fun setEnabled(enabled: Boolean) {
        store.setEnabled(profiles.activeProfileId, enabled)
        if (!enabled) dao.clear()
    }

    private companion object {
        /**
         * How long a downloaded index stands: a fortnight.
         *
         * Long enough that this is not a recurring download, short enough that a channel
         * added to the reference list turns up without the user doing anything.
         */
        const val INDEX_TTL_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}
