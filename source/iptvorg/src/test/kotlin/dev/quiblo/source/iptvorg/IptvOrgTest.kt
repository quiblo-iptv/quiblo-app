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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChannelNameMatchingTest {

    @Test
    fun `a playlist name and a reference name meet in the middle`() {
        // The whole feature rests on this: neither spelling is wrong, and a literal
        // comparison matches nothing for the great majority of real playlists.
        assertEquals(iptvOrgMatchKey("BBC One"), iptvOrgMatchKey("UK| BBC One HD"))
        assertEquals(iptvOrgMatchKey("BBC One"), iptvOrgMatchKey("bbc-one"))
        assertEquals(iptvOrgMatchKey("Sky Sports F1"), iptvOrgMatchKey("UK: Sky Sports F1 FHD"))
    }

    @Test
    fun `strips a bracketed suffix and non-ascii quality marks`() {
        assertEquals(iptvOrgMatchKey("Al Jazeera"), iptvOrgMatchKey("Al Jazeera (Backup)"))
        assertEquals(iptvOrgMatchKey("Al Jazeera"), iptvOrgMatchKey("Al Jazeera ᴴᴰ"))
    }

    @Test
    fun `keeps genuinely different channels apart`() {
        // The cost of an over-eager rule is the wrong logo on the wrong channel, which is
        // worse than no logo — so the numbered feeds of one brand must not collapse.
        assertTrue(iptvOrgMatchKey("BBC One") != iptvOrgMatchKey("BBC Two"))
        assertTrue(iptvOrgMatchKey("Sky Sports F1") != iptvOrgMatchKey("Sky Sports Main Event"))
    }

    @Test
    fun `a name that is nothing but markers reduces to nothing`() {
        // Empty is the signal not to look anything up. A key of "" would otherwise match
        // whatever else cleaned down to the same, which is an arbitrary logo.
        assertEquals("", iptvOrgMatchKey("HD"))
        assertEquals("", iptvOrgMatchKey("###"))
    }
}

class IptvOrgClientTest {

    @Test
    fun `indexes a channel by its id, its name and its alternatives`() = runTest {
        val client = clientReturning(
            channels = """
                [{"id":"BBCOne.uk","name":"BBC One","alt_names":["BBC 1"],"logo":"https://logo.invalid/bbc1.png"}]
            """.trimIndent(),
            logos = "[]",
        )

        val index = client.fetchLogoIndex()

        assertNotNull(index)
        val keys = index.orEmpty().map { it.matchKey }
        // A playlist may use any of the three and there is no telling which in advance.
        assertTrue(keys.contains("bbcone.uk"))
        assertTrue(keys.contains(iptvOrgMatchKey("BBC One")))
        assertTrue(keys.contains(iptvOrgMatchKey("BBC 1")))
    }

    @Test
    fun `falls back to the separate logo list when a channel carries no logo`() = runTest {
        // The logo field has moved in and out of the channel file across revisions of this
        // API. Reading both is what keeps the feature working across one of those moves.
        val client = clientReturning(
            channels = """[{"id":"ITV1.uk","name":"ITV1"}]""",
            logos = """[{"channel":"ITV1.uk","url":"https://logo.invalid/itv1.png","tags":[]}]""",
        )

        val index = client.fetchLogoIndex()

        assertEquals("https://logo.invalid/itv1.png", index?.firstOrNull()?.logoUrl)
    }

    @Test
    fun `prefers an untagged logo over a tagged variant`() = runTest {
        val client = clientReturning(
            channels = """[{"id":"ITV1.uk","name":"ITV1"}]""",
            logos = """
                [{"channel":"ITV1.uk","url":"https://logo.invalid/mono.png","tags":["monochrome"]},
                 {"channel":"ITV1.uk","url":"https://logo.invalid/plain.png","tags":[]}]
            """.trimIndent(),
        )

        // The tagged variants are horizontal cuts and monochrome versions, which look wrong
        // in a list row next to ordinary logos.
        assertEquals("https://logo.invalid/plain.png", client.fetchLogoIndex()?.firstOrNull()?.logoUrl)
    }

    @Test
    fun `a live channel keeps a name a defunct one would otherwise claim`() = runTest {
        val client = clientReturning(
            channels = """
                [{"id":"Old.uk","name":"Example TV","closed":"2019-01-01","logo":"https://logo.invalid/old.png"},
                 {"id":"New.uk","name":"Example TV","logo":"https://logo.invalid/new.png"}]
            """.trimIndent(),
            logos = "[]",
        )

        val byName = client.fetchLogoIndex()?.firstOrNull { it.matchKey == iptvOrgMatchKey("Example TV") }

        assertEquals("https://logo.invalid/new.png", byName?.logoUrl)
    }

    @Test
    fun `ignores fields it has never heard of`() = runTest {
        // A community dataset gains columns between releases. Refusing to parse one that
        // did would break the feature for everyone on the day it happened.
        val client = clientReturning(
            channels = """[{"id":"A.uk","name":"A","logo":"https://logo.invalid/a.png","invented_field":42}]""",
            logos = "[]",
        )

        assertNotNull(client.fetchLogoIndex())
    }

    @Test
    fun `an unreachable list is silence, not an error`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
        val client = IptvOrgClient(HttpClient(engine), baseUrl = BASE_URL)

        // Null for every failure equally: the caller shows the placeholder it already had.
        assertNull(client.fetchLogoIndex())
    }

    private fun clientReturning(channels: String, logos: String): IptvOrgClient {
        val engine = MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("logos.json")) logos else channels
            respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        return IptvOrgClient(HttpClient(engine), baseUrl = BASE_URL)
    }

    private companion object {
        const val BASE_URL = "https://iptv-org.invalid/api/"
    }
}
