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

import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.entity.TitleMetadataEntity
import dev.quiblo.core.datastore.TmdbKeyStore
import dev.quiblo.core.model.AuthorLabel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.source.tmdb.NO_YEAR
import dev.quiblo.source.tmdb.TitleIdentity
import dev.quiblo.source.tmdb.TmdbAnswer
import dev.quiblo.source.tmdb.TmdbClient
import dev.quiblo.source.tmdb.TmdbKind
import dev.quiblo.source.tmdb.metadataOrNull
import dev.quiblo.source.tmdb.titleIdentity
import kotlinx.coroutines.flow.StateFlow

/**
 * Optional enrichment for films and series, from the user's own TMDB key.
 *
 * Off unless a key is set. When it is off nothing here touches the network at all, which
 * is what keeps AC-NFR-03 true: the only outbound hosts are the ones the user configured,
 * and TMDB becomes one of those only by the user pasting a key in.
 *
 * Answers are cached in the database, not in memory. TMDB rate-limits per key, and the key
 * belongs to the user — a cache that empties on every relaunch would spend their quota
 * re-learning what it already knew. Misses are cached too: "this title matches nothing" is
 * an answer, and re-asking it on every visit would be the most wasteful thing here.
 *
 * Entries expire so a title that gains a description, a certificate or a score is not stuck
 * without one forever.
 */
class TitleMetadataRepository(
    private val client: TmdbClient,
    private val keyStore: TmdbKeyStore,
    private val dao: TitleMetadataDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Null while no key is configured, which the settings screen renders as "off". */
    val apiKey: StateFlow<String?> = keyStore.apiKey

    val isEnabled: Boolean get() = !keyStore.apiKey.value.isNullOrBlank()

    /**
     * Everything TMDB knows about [title], or null.
     *
     * Null covers every reason equally — feature off, no match, key rejected, host
     * unreachable — because the caller's response to all of them is identical: show what
     * the provider already supplied and nothing more.
     *
     * A record that only a poster tile ever asked for is *not* an answer here: it carries a
     * score and no cast. Those are upgraded in place on first open rather than left beside
     * a second row for the same title.
     */
    suspend fun forTitle(title: String, kind: MediaKind): TitleMetadata? =
        cachedOrFetched(title, kind, acceptPartial = false)

    /**
     * What a poster tile needs: a score, and artwork for the very common case of a provider
     * that supplies none.
     *
     * One request rather than two on a miss, and nothing at all once cached. A record
     * already held in full answers this without asking anyone.
     *
     * Series are where this earns its keep. Panels routinely list a series with a cover on
     * the details endpoint and nothing on the catalogue entry, so a Series grid built from
     * the catalogue alone is a wall of placeholder icons — and the one request that fetches
     * the score already carries the poster.
     */
    suspend fun previewFor(title: String, kind: MediaKind): TitleMetadata? =
        cachedOrFetched(title, kind, acceptPartial = true)

    /**
     * The same lookup as [previewFor], with the reason a null was a null.
     *
     * **A tile that asked once and was refused must be able to ask again**, and a null cannot say
     * whether it should. A browse screen remembers which tiles it has requested so that scrolling
     * does not re-ask for what it already has; when the answer was a rate limit or an unreachable
     * host, that memory is what left a poster blank for the rest of the session while the same
     * title opened with a cover on its detail screen, which is the report.
     *
     * [PreviewOutcome.NoMatch] is still an answer and is still worth remembering: the miss is
     * cached in the database, so a caller that forgets it pays a local read rather than a request.
     */
    suspend fun previewOutcomeFor(title: String, kind: MediaKind): PreviewOutcome =
        when (val answer = answerFor(title, kind, acceptPartial = true)) {
            is TmdbAnswer.Found -> PreviewOutcome.Found(answer.metadata)
            TmdbAnswer.NoMatch -> PreviewOutcome.NoMatch
            is TmdbAnswer.Refused -> PreviewOutcome.Unanswered
        }

    /**
     * Everything the cache holds for [title], or null if nothing is cached.
     *
     * Does not touch the network.
     */
    suspend fun cachedPreviewFor(title: String, kind: MediaKind): TitleMetadata? {
        val identity = title.titleIdentity().takeIf { it.searchTitle.isNotBlank() } ?: return null
        return dao.find(identity.searchTitle, kind.name, identity.year)
            ?.takeIf { now() - it.fetchedAtEpochMillis < CACHE_TTL_MILLIS }
            ?.takeUnless { it.isMiss }
            ?.toMetadata()
    }

    /**
     * Asks the service about [title] again, ignoring whatever is cached.
     *
     * `INC-F7`. The intake asks for this because artwork is sometimes missing or a plot is
     * sometimes out of date, and a viewer looking at a wrong-looking screen has no other way
     * to say "ask again" — the cached answer otherwise stands for a fortnight.
     *
     * **Three properties, and two of them were bought with defects in `005`:**
     *
     * - **A refusal is never cached.** A rate limit or a rejected key leaves the existing row
     *   exactly as it was. That is [fetchAndCache]'s existing behaviour and this inherits it
     *   rather than reimplementing it — the whole reason this goes through the same path.
     * - **It spends the same rate limit as everything else.** A button a viewer can press
     *   repeatedly is a request source like any other, and this project has had an account
     *   blocked twice.
     * - **It returns the outcome rather than the metadata**, so the screen can tell "the
     *   service has nothing about this title" from "the service would not answer". Those look
     *   identical in a null and must not look identical on screen.
     *
     * Returns [MetadataRefresh.NoMatch] without asking when the feature is off, which is also
     * what the button's absence should already have prevented.
     */
    suspend fun refresh(title: String, kind: MediaKind): MetadataRefresh {
        val key = keyStore.apiKey.value?.takeIf { it.isNotBlank() }
        val tmdbKind = kind.toTmdbKind()
        val identity = title.titleIdentity().takeIf { it.searchTitle.isNotBlank() }
        if (key == null || tmdbKind == null || identity == null) return MetadataRefresh.NoMatch

        // Straight to the fetch. Going through resolve() would find the fresh row this call
        // exists to disbelieve and return it without asking anyone.
        return fetchAndCache(
            apiKey = key,
            title = title,
            kind = kind,
            tmdbKind = tmdbKind,
            identity = identity,
            // The full record, never the cheap half: a viewer pressing refresh on a detail
            // screen is looking at the plot and the cast, and a partial row would overwrite a
            // complete one with less than it had.
            partial = false,
        ).toMetadataRefresh()
    }

    /** Just the score. Kept for callers that render a badge and nothing else. */
    suspend fun ratingFor(title: String, kind: MediaKind): Double? = previewFor(title, kind)?.rating

    private suspend fun cachedOrFetched(
        title: String,
        kind: MediaKind,
        acceptPartial: Boolean,
    ): TitleMetadata? = answerFor(title, kind, acceptPartial).metadataOrNull()

    /**
     * The same work as [cachedOrFetched], with the outcome intact.
     *
     * Internal because only the catalogue scan needs it: it is the one caller that has to
     * stop when TMDB stops answering, rather than carrying on through thirty thousand titles
     * collecting nulls.
     */
    internal suspend fun answerFor(
        title: String,
        kind: MediaKind,
        acceptPartial: Boolean = true,
    ): TmdbAnswer {
        val key = keyStore.apiKey.value?.takeIf { it.isNotBlank() }
        val tmdbKind = kind.toTmdbKind()
        val identity = title.titleIdentity().takeIf { it.searchTitle.isNotBlank() }

        // Three ways there is nothing to look up: the feature is off, the item is a live
        // channel, or the title cleaned down to nothing worth searching for. None of them is
        // a failure, and none of them is worth asking about twice.
        if (key == null || tmdbKind == null || identity == null) return TmdbAnswer.NoMatch

        return resolve(key, tmdbKind, title, kind, identity, acceptPartial)
    }

    @Suppress("LongParameterList")
    private suspend fun resolve(
        apiKey: String,
        tmdbKind: TmdbKind,
        title: String,
        kind: MediaKind,
        identity: TitleIdentity,
        acceptPartial: Boolean,
    ): TmdbAnswer {
        val fresh = dao.find(identity.searchTitle, kind.name, identity.year)
            ?.takeIf { now() - it.fetchedAtEpochMillis < CACHE_TTL_MILLIS }

        if (fresh != null && fresh.answers(acceptPartial)) {
            return if (fresh.isMiss) TmdbAnswer.NoMatch else TmdbAnswer.Found(fresh.toMetadata())
        }

        return fetchAndCache(
            apiKey = apiKey,
            title = title,
            kind = kind,
            tmdbKind = tmdbKind,
            identity = identity,
            partial = acceptPartial,
        )
    }

    /**
     * Asks TMDB and writes down whatever comes back, if it is the sort of thing worth
     * writing down.
     *
     * Returns the answer rather than the record, because callers that fetch in bulk need to
     * tell a title that matched nothing from a request that never landed. Everything on a
     * screen ignores the distinction and takes [TmdbAnswer.metadataOrNull].
     */
    @Suppress("LongParameterList")
    private suspend fun fetchAndCache(
        apiKey: String,
        title: String,
        kind: MediaKind,
        tmdbKind: TmdbKind,
        identity: TitleIdentity,
        partial: Boolean,
    ): TmdbAnswer {
        // The year narrows a search that would otherwise match a remake. The identity has
        // already read it off the raw title, so it is not read a second time here — two
        // places reading one fact out of one string is how they come to disagree.
        val year = identity.year.takeIf { it != NO_YEAR }
        val answer = if (partial) {
            client.summary(apiKey = apiKey, title = title, kind = tmdbKind, year = year)
        } else {
            client.lookup(apiKey = apiKey, title = title, kind = tmdbKind, year = year)
        }

        when (answer) {
            is TmdbAnswer.Found -> dao.upsert(answer.metadata.toEntity(identity, kind.name, now()))

            // A miss is recorded rather than forgotten, so the next visit costs nothing.
            TmdbAnswer.NoMatch -> dao.upsert(
                TitleMetadataEntity(
                    searchTitle = identity.searchTitle,
                    kind = kind.name,
                    year = identity.year,
                    fetchedAtEpochMillis = now(),
                    isMiss = true,
                ),
            )

            // Nothing at all. A refusal is not an answer about this title, and caching it as
            // one would put a fortnight's worth of "matches nothing" behind a single bad
            // minute on the network — or behind a rate limit met half way through a scan of
            // thirty thousand titles, which is where this stopped being theoretical.
            is TmdbAnswer.Refused -> Unit
        }

        return answer
    }

    /**
     * The titles the cache holds an answer for, misses included, whatever their age.
     *
     * **Age is deliberately not consulted, and it used to be.** This set is subtracted from a
     * scan's work list, and a scan is an hour of somebody's rate limit: a title that has been
     * asked about is work that has been done, and a fortnight later it is still work that has
     * been done. Filtering by the TTL here meant a single wrong clock — a television that boots
     * before it syncs the time, an emulator resumed from a stale snapshot — could age every row
     * at once and hand back a full catalogue to re-walk. That is the defect this method's
     * previous shape produced on somebody's television, and it cost an hour.
     *
     * Nothing is stuck as a result. The TTL still governs [resolve], so an expired row is
     * refetched the moment a screen actually needs it, and the refresh button ignores the cache
     * entirely. What changed is only that *bulk* work no longer redoes itself.
     */
    internal suspend fun answeredKeys(): Set<CacheIdentity> =
        dao.allKeys().mapTo(HashSet()) { CacheIdentity(TitleIdentity(it.searchTitle, it.year), it.kind) }

    /** How many answers are cached, for the figure settings shows. */
    suspend fun cachedTitleCount(): Int = dao.count()

    /**
     * Saves the key, and empties the cache **only if the key actually changed**.
     *
     * A different key can return different results — a different language, a different region's
     * certificates — so nothing carries over from one to another. The same key returns the same
     * results, and clearing on it is pure loss: re-entering a key that was already saved threw
     * away however many hours of scanning stood behind it, which is not what pressing Save on an
     * unchanged field means anywhere.
     */
    suspend fun setApiKey(apiKey: String?) {
        val cleaned = apiKey?.trim()?.takeIf { it.isNotBlank() }
        val changed = cleaned != keyStore.apiKey.value
        keyStore.set(cleaned)
        if (changed) dao.clear()
    }

    suspend fun validate(apiKey: String): Boolean = client.validate(apiKey)

    suspend fun load() = keyStore.load()

    private companion object {
        /**
         * How long a cached answer stands.
         *
         * A fortnight. Long enough that browsing costs nothing, short enough that a title
         * which gains a description or a certificate picks it up without the user having to
         * do anything.
         */
        const val CACHE_TTL_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}

/**
 * What pressing refresh did.
 *
 * Named for the metadata rather than for the gesture because `RefreshOutcome` is already taken
 * by [SourceRepository], where refreshing means reloading a whole playlist. Two different things
 * called Refresh is the sort of collision that reads fine until somebody imports the wrong one.
 *
 * A restatement of `TmdbAnswer` rather than a reuse of it, on the same rule as `ScanRefusal`:
 * `docs/PLAN.md` §2 says features talk to `:core:data` and nothing else, and a detail screen
 * importing `:source:tmdb` to render a message would be the first crack in it.
 *
 * Three cases rather than a nullable, because all three need different words on screen and two
 * of them look identical in a null.
 */
sealed interface MetadataRefresh {

    /** The service answered, and this is what it now knows. The screen redraws. */
    data class Updated(val metadata: TitleMetadata) : MetadataRefresh

    /** The service was asked and holds nothing about this title. Not a failure. */
    data object NoMatch : MetadataRefresh

    /** The service would not answer. **Nothing was overwritten**, and saying so matters. */
    data class Refused(val reason: ScanRefusal) : MetadataRefresh
}

private fun TmdbAnswer.toMetadataRefresh(): MetadataRefresh = when (this) {
    is TmdbAnswer.Found -> MetadataRefresh.Updated(metadata)
    TmdbAnswer.NoMatch -> MetadataRefresh.NoMatch
    is TmdbAnswer.Refused -> MetadataRefresh.Refused(reason.toScanRefusal())
}

/**
 * Which row of the metadata cache a title belongs to: its identity, and which catalogue.
 *
 * [kind] is half the key rather than a column, because "Fargo" is a film and "Fargo" is a
 * series and they are not the same record.
 *
 * This type exists because **three places have to agree on it exactly** — the repository that
 * writes the row, the scanner that subtracts what is already cached from its work list, and
 * the search screen's genre index that joins the catalogue back onto the cache. They used to
 * agree by each spelling the key out, with a comment in `SearchRepository` explaining that
 * they must agree and were therefore written separately. That reasoning is backwards, and
 * #024 is what it cost: the moment the key changed, two of the three would have gone on
 * building the old one and the genre filter would have quietly returned nothing.
 */
data class CacheIdentity(val title: TitleIdentity, val kind: String)

/**
 * Where this provider title is filed in the metadata cache, or null if it is filed nowhere.
 *
 * Null means the title cleaned away to nothing — a name written entirely in a non-Latin
 * script, or a bare language tag. It is never looked up and never cached, and callers must
 * keep skipping it rather than filing a hundred such titles under one empty key.
 */
internal fun String.cacheIdentity(kind: String): CacheIdentity? =
    titleIdentity().takeIf { it.searchTitle.isNotBlank() }?.let { CacheIdentity(it, kind) }

/**
 * Which TMDB catalogue, if any, describes this kind of item.
 *
 * Live channels return null and are never looked up. A television channel is not a title —
 * searching for one matches whatever film happens to share its name, and the guide already
 * says what is on it.
 */
/**
 * The catalogue TMDB would file this kind under, or null for a live channel.
 *
 * Shared with [PopularTitlesRepository] rather than copied into it. A second copy of a two-line
 * mapping is how a third kind comes to be handled in one place and not the other.
 */
internal fun MediaKind.toTmdbKind(): TmdbKind? = when (this) {
    MediaKind.VOD -> TmdbKind.MOVIE
    MediaKind.SERIES -> TmdbKind.SERIES
    MediaKind.LIVE -> null
}

/**
 * Whether a cached row is an answer to the question being asked.
 *
 * A miss answers everything: a title that matched nothing has no fuller version to go and
 * fetch, so a detail screen must not mistake it for a partial record and re-ask on every
 * open. Otherwise a partial row answers a poster tile and not a detail screen.
 */
private fun TitleMetadataEntity.answers(acceptPartial: Boolean): Boolean =
    isMiss || acceptPartial || !isPartial

/** Newline-separated in one column: a join table for a handful of names is not worth it. */
private const val LIST_SEPARATOR = "\n"

private fun TitleMetadataEntity.toMetadata() = TitleMetadata(
    overview = overview,
    genres = genres?.split(LIST_SEPARATOR).orEmpty().filter { it.isNotBlank() },
    ageRating = ageRating,
    rating = rating,
    author = author,
    authorLabel = if (kind == MediaKind.SERIES.name) AuthorLabel.CREATOR else AuthorLabel.DIRECTOR,
    topCast = topCast?.split(LIST_SEPARATOR).orEmpty().filter { it.isNotBlank() },
    posterUrl = TmdbClient.toHighResTmdbUrl(posterUrl, isBackdrop = false),
    backdropUrl = TmdbClient.toHighResTmdbUrl(backdropUrl, isBackdrop = true),
    releaseYear = releaseYear,
    runtimeMinutes = runtimeMinutes,
    originalLanguage = originalLanguage,
    popularity = popularity,
    isPartial = isPartial,
)

private fun TitleMetadata.toEntity(identity: TitleIdentity, kind: String, fetchedAt: Long) = TitleMetadataEntity(
    searchTitle = identity.searchTitle,
    kind = kind,
    year = identity.year,
    overview = overview,
    genres = genres.joinToString(LIST_SEPARATOR).takeIf { it.isNotBlank() },
    ageRating = ageRating,
    rating = rating,
    author = author,
    topCast = topCast.joinToString(LIST_SEPARATOR).takeIf { it.isNotBlank() },
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    releaseYear = releaseYear,
    runtimeMinutes = runtimeMinutes,
    originalLanguage = originalLanguage,
    popularity = popularity,
    fetchedAtEpochMillis = fetchedAt,
    isMiss = false,
    isPartial = isPartial,
)

/**
 * How a poster tile's lookup went.
 *
 * The three outcomes of [TmdbAnswer] as this layer's own type, so that a screen can tell "asked
 * and there is nothing" from "never got an answer" without the feature modules depending on the
 * TMDB source module for the distinction.
 */
sealed interface PreviewOutcome {

    /** The service answered, and this is what it said. */
    data class Found(val metadata: TitleMetadata) : PreviewOutcome

    /** The service answered and holds nothing under this title. Cached; asking again is cheap. */
    data object NoMatch : PreviewOutcome

    /** Nothing was asked, or nothing usable came back. Worth trying again later. */
    data object Unanswered : PreviewOutcome
}
