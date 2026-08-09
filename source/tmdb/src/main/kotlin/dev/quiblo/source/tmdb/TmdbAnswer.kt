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

package dev.quiblo.source.tmdb

import dev.quiblo.core.model.TitleMetadata

/**
 * What asking TMDB about a title produced.
 *
 * Three outcomes rather than a nullable record, and the distinction between the last two is
 * the whole reason this type exists: **"nothing matches this title" is an answer, and
 * "I could not ask" is not.** They used to be the same `null`.
 *
 * That conflation is harmless while browsing — a poster shows no score for a minute — and
 * ruinous in bulk. A scan of a catalogue that trips a rate limit half way through would
 * otherwise record tens of thousands of rows saying "matches nothing", each of them cached
 * for a fortnight, and the search screen would then report a described catalogue with no
 * genres in it. A cache may hold answers. It may never hold failures.
 */
sealed interface TmdbAnswer {

    /** TMDB knows this title. */
    data class Found(val metadata: TitleMetadata) : TmdbAnswer

    /** TMDB was asked and holds nothing under that name. Cacheable, and cheap to remember. */
    data object NoMatch : TmdbAnswer

    /**
     * The question never got a usable reply.
     *
     * A rate limit, a rejected key, a server error, an unreachable host — kept together
     * because a caller's options are the same for all of them: stop asking for a while, and
     * write nothing down.
     */
    data class Refused(val reason: TmdbRefusal, val retryAfterSeconds: Long? = null) : TmdbAnswer
}

/**
 * The record if there is one, and null for every reason there might not be.
 *
 * For screens, which respond to a missing plot and an unreachable host identically: show
 * what the provider already supplied and nothing more.
 */
fun TmdbAnswer.metadataOrNull(): TitleMetadata? = (this as? TmdbAnswer.Found)?.metadata

/** Why TMDB would not answer, in the only detail a caller can act on. */
enum class TmdbRefusal {
    /** HTTP 429. [TmdbAnswer.Refused.retryAfterSeconds] carries the service's own advice. */
    RATE_LIMITED,

    /** HTTP 401 or 403. No amount of waiting fixes this one — the key is wrong or revoked. */
    KEY_REJECTED,

    /** A 5xx, a timeout, no network, or a body that would not parse. Worth retrying later. */
    UNAVAILABLE,
}
