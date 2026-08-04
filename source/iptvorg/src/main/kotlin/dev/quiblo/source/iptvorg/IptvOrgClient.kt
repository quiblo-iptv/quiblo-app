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

package dev.quiblo.source.iptvorg

import dev.quiblo.source.iptvorg.dto.IptvOrgChannelDto
import dev.quiblo.source.iptvorg.dto.IptvOrgLogoDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

/**
 * One entry in the built logo index: a key to match on, and the artwork it resolves to.
 *
 * Flattened at build time rather than looked up through the channel record at query time,
 * because the query side is a database with an index on one column and the alternative is
 * holding several megabytes of channel records in memory for the life of the process.
 */
data class ChannelLogo(val matchKey: String, val logoUrl: String)

/**
 * A read-only client for the iptv-org channel reference list.
 *
 * The list is a community-maintained catalogue of real television channels — names,
 * identifiers and logos — with no streams in it. Nothing here is a `MediaSource`: it
 * annotates channels the user's own provider already returned, in exactly the way
 * `TmdbClient` annotates films.
 *
 * Off unless the user turns it on. It contacts a host nobody configured, which is precisely
 * what AC-NFR-03 forbids doing by default, so the switch is the feature's front door rather
 * than a refinement of it.
 *
 * The download is one large static file, so it is fetched rarely and cached in full. See
 * `ChannelLogoRepository` for the caching, which is not this class's business.
 */
class IptvOrgClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = defaultJson,
) {

    /**
     * The whole index, or null if it could not be built.
     *
     * Null for every failure equally — unreachable, rate-limited, a body that no longer
     * parses — because the caller's response to all of them is the same: carry on showing
     * the placeholder the channel already had. A missing logo is not an error state worth
     * putting on screen.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun fetchLogoIndex(): List<ChannelLogo>? = try {
        val channels = fetch<IptvOrgChannelDto>(CHANNELS_PATH)
        if (channels.isNullOrEmpty()) null else buildIndex(channels, fetch<IptvOrgLogoDto>(LOGOS_PATH).orEmpty())
    } catch (_: Exception) {
        null
    }

    private suspend inline fun <reified T> fetch(path: String): List<T>? {
        val response = httpClient.get("$baseUrl$path")
        if (response.status != HttpStatusCode.OK) return null
        return json.decodeFromString<List<T>>(response.bodyAsText())
    }

    /**
     * Flattens channels and logos into one key-to-artwork list.
     *
     * A channel contributes several keys — its identifier, its name, each of its alternative
     * names — because a playlist may use any of them and there is no way to know which in
     * advance. Later entries never displace earlier ones, so the first channel to claim a
     * key keeps it and a defunct channel cannot take a name from a live one.
     */
    private fun buildIndex(
        channels: List<IptvOrgChannelDto>,
        logos: List<IptvOrgLogoDto>,
    ): List<ChannelLogo> {
        val logosByChannel = logos
            .filter { it.channel != null && it.url.isNotBlank() }
            // An untagged logo is the channel's ordinary one; the tagged variants are
            // horizontal cuts and monochrome versions that look wrong in a list row.
            .sortedBy { it.tags.size }
            .groupBy { requireNotNull(it.channel) }

        val index = LinkedHashMap<String, String>()
        // Channels still on air are indexed first, so a shut-down channel can only ever
        // claim a name nothing live wanted.
        channels.sortedBy { it.closed != null }.forEach { channel ->
            val logoUrl = channel.logo?.takeIf { it.isNotBlank() }
                ?: logosByChannel[channel.id]?.firstOrNull()?.url
                ?: return@forEach

            index.putIfAbsent(channel.id.lowercase(), logoUrl)
            (listOf(channel.name) + channel.altNames).forEach { name ->
                iptvOrgMatchKey(name).takeIf { it.isNotBlank() }?.let { index.putIfAbsent(it, logoUrl) }
            }
        }

        return index.map { (key, url) -> ChannelLogo(matchKey = key, logoUrl = url) }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://iptv-org.github.io/api/"
        const val CHANNELS_PATH = "channels.json"
        const val LOGOS_PATH = "logos.json"

        /**
         * Lenient by necessity.
         *
         * A community dataset adds fields between releases, and refusing to parse a file
         * that gained a column would break the feature for everyone the day it happened.
         */
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
