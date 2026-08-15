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

package dev.quiblo.source.tmdb.dto

import dev.quiblo.core.model.AuthorLabel
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.core.model.releaseYearIn
import dev.quiblo.source.tmdb.TmdbClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResponse(val results: List<SearchResult> = emptyList())

/**
 * One search hit.
 *
 * Carries more than an id because the search response already contains a score and a
 * poster, and a poster tile needs nothing else. Reading them here is what makes a rating
 * cost one request instead of two.
 */
@Serializable
internal data class SearchResult(
    val id: Int? = null,
    /**
     * A film's title and a series' name, both spelled by the endpoint that returned the hit.
     *
     * Unread by the search path, which already knows what it asked for. The popular lists are
     * the caller that needs them: nothing there was searched for by name, so the name is the
     * only thing that can be matched against a catalogue.
     */
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    /**
     * A film's release date and a series' first broadcast, both spelled by the endpoint that
     * returned the hit. Only one of them is ever present, so both are read and the first
     * non-blank wins — see [SearchResult.year].
     */
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    /**
     * Numbers, not names — the search endpoint never spells its genres out.
     *
     * Translating them costs one call to the genre list per catalogue, held for the life of
     * the process. That is what lets a genre arrive with the one-request record rather than
     * only with the two-request one, and it is the whole reason a catalogue scan is an hour
     * rather than two.
     */
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
)

/** `/genre/movie/list` and `/genre/tv/list`: the whole vocabulary, in one call each. */
@Serializable
internal data class GenreListResponse(val genres: List<GenreDto> = emptyList())

@Serializable
internal data class MovieDetailsDto(
    val overview: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val credits: CreditsDto? = null,
    @SerialName("release_dates") val releaseDates: ReleaseDatesDto? = null,
    /** Whole minutes. Zero and null both mean the service does not know. */
    val runtime: Int? = null,
    @SerialName("release_date") val releaseDate: String? = null,
)

/**
 * A series, which TMDB describes under a different set of names to a film.
 *
 * Not a shared type with [MovieDetailsDto]: the overlap is coincidental field-by-field, and
 * a union of both would need every difference expressed as a nullable anyway. The three
 * that matter are that a series is *created* rather than directed, that its certificate
 * lives in `content_ratings` under `rating` rather than in `release_dates` under
 * `certification`, and that its seasons are not something this screen shows.
 */
@Serializable
internal data class TvDetailsDto(
    val overview: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val credits: CreditsDto? = null,
    @SerialName("created_by") val createdBy: List<CreatorDto> = emptyList(),
    @SerialName("content_ratings") val contentRatings: ContentRatingsDto? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
)

@Serializable
internal data class CreatorDto(val name: String? = null)

@Serializable
internal data class ContentRatingsDto(val results: List<ContentRatingDto> = emptyList())

@Serializable
internal data class ContentRatingDto(
    @SerialName("iso_3166_1") val country: String? = null,
    val rating: String? = null,
)

@Serializable
internal data class GenreDto(val id: Int? = null, val name: String? = null)

@Serializable
internal data class CreditsDto(
    val cast: List<CastMemberDto> = emptyList(),
    val crew: List<CrewMemberDto> = emptyList(),
)

@Serializable
internal data class CastMemberDto(val name: String? = null, val order: Int? = null)

@Serializable
internal data class CrewMemberDto(val name: String? = null, val job: String? = null)

@Serializable
internal data class ReleaseDatesDto(val results: List<CountryReleaseDto> = emptyList())

@Serializable
internal data class CountryReleaseDto(
    @SerialName("iso_3166_1") val country: String? = null,
    @SerialName("release_dates") val releases: List<CertificationDto> = emptyList(),
)

@Serializable
internal data class CertificationDto(val certification: String? = null)

/** Flattens the API's shape into the one the app renders. */
internal fun MovieDetailsDto.toMetadata(): TitleMetadata = TitleMetadata(
    overview = overview?.takeIf { it.isNotBlank() },
    genres = genres.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } },
    ageRating = certificate(),
    // TMDB returns 0.0 for an unrated film, which would render as a genuine score of zero.
    rating = voteAverage?.takeIf { it > 0.0 },
    author = credits?.crew?.firstOrNull { it.job == "Director" }?.name,
    authorLabel = AuthorLabel.DIRECTOR,
    topCast = credits?.cast.orEmpty().leadNames(),
    posterUrl = posterPath?.let { TmdbClient.IMAGE_BASE_URL + it },
    backdropUrl = backdropPath?.let { TmdbClient.IMAGE_BASE_URL + it },
    releaseYear = releaseYearIn(releaseDate),
    // Zero is how the service says "unknown", exactly as it does for a score.
    runtimeMinutes = runtime?.takeIf { it > 0 },
)

/**
 * The same flattening for a series.
 *
 * The creator is taken from `created_by` rather than from a crew credit: a series has no
 * single director, and the per-episode directors TMDB does list are a list of strangers to
 * anyone deciding whether to watch it.
 */
internal fun TvDetailsDto.toMetadata(): TitleMetadata = TitleMetadata(
    overview = overview?.takeIf { it.isNotBlank() },
    genres = genres.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } },
    ageRating = contentRating(),
    rating = voteAverage?.takeIf { it > 0.0 },
    author = createdBy.firstNotNullOfOrNull { it.name?.takeIf(String::isNotBlank) },
    authorLabel = AuthorLabel.CREATOR,
    topCast = credits?.cast.orEmpty().leadNames(),
    posterUrl = posterPath?.let { TmdbClient.IMAGE_BASE_URL + it },
    backdropUrl = backdropPath?.let { TmdbClient.IMAGE_BASE_URL + it },
    releaseYear = releaseYearIn(firstAirDate),
    // Left null on purpose. TMDB offers an average episode length for a series and it is not
    // the length of anything a viewer is about to watch.
    runtimeMinutes = null,
)

/**
 * What a search hit alone can say.
 *
 * Marked partial so a detail screen knows to ask properly rather than render a record with
 * no cast and call it complete.
 */
internal fun SearchResult.toPartialMetadata(genreNames: Map<Int, String>): TitleMetadata = TitleMetadata(
    overview = overview?.takeIf { it.isNotBlank() },
    // An id with no name is dropped rather than rendered as a number. TMDB adds genres from
    // time to time, and a vocabulary fetched before one was added simply does not have it.
    genres = genreIds.mapNotNull { genreNames[it] },
    rating = voteAverage?.takeIf { it > 0.0 },
    posterUrl = posterPath?.let { TmdbClient.IMAGE_BASE_URL + it },
    backdropUrl = backdropPath?.let { TmdbClient.IMAGE_BASE_URL + it },
    releaseYear = year(),
    // A search hit carries no runtime at all, for either kind. That is what makes this record
    // partial, and a detail screen upgrades it.
    runtimeMinutes = null,
    isPartial = true,
)

/**
 * The year of this hit, whichever of the two date fields the endpoint filled in.
 *
 * Films come back with `release_date` and series with `first_air_date`, and one search never
 * returns both — so reading either is reading the only one there is.
 */
private fun SearchResult.year(): Int? = releaseYearIn(releaseDate) ?: releaseYearIn(firstAirDate)

/** The film's title or the series' name, whichever the endpoint used. */
internal fun SearchResult.titleOrName(): String? =
    title?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }

/** The release year as this file already reads it, exposed for the popular lists. */
internal fun SearchResult.releaseYear(): Int? = year()

private fun List<CastMemberDto>.leadNames(): List<String> =
    sortedBy { it.order ?: Int.MAX_VALUE }.mapNotNull { it.name }.take(TOP_CAST_COUNT)

/**
 * A series certificate, preferring the US one, for the same reason [certificate] does.
 *
 * TMDB spells this differently for television: the field is `rating` inside
 * `content_ratings`, where a film's is `certification` inside `release_dates`.
 */
private fun TvDetailsDto.contentRating(): String? {
    val countries = contentRatings?.results.orEmpty()
    val preferred = countries.firstOrNull { it.country == PREFERRED_CERTIFICATE_COUNTRY }
    val chosen = preferred ?: countries.firstOrNull { !it.rating.isNullOrBlank() }
    return chosen?.rating?.takeIf(String::isNotBlank)
}

/**
 * A certificate, preferring the US one.
 *
 * Certificates are national and not comparable — a UK 15 is not a US R — so this picks one
 * system rather than pretending to merge them. US is chosen because TMDB populates it most
 * consistently; the first country carrying any certificate is the fallback, which is better
 * than showing nothing.
 */
private fun MovieDetailsDto.certificate(): String? {
    val countries = releaseDates?.results.orEmpty()
    val preferred = countries.firstOrNull { it.country == PREFERRED_CERTIFICATE_COUNTRY }
    val chosen = preferred ?: countries.firstOrNull { country ->
        country.releases.any { !it.certification.isNullOrBlank() }
    }
    return chosen?.releases?.firstNotNullOfOrNull { it.certification?.takeIf(String::isNotBlank) }
}

private const val TOP_CAST_COUNT = 5
private const val PREFERRED_CERTIFICATE_COUNTRY = "US"
