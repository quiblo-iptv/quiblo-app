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

import dev.quiblo.core.model.MovieMetadata
import dev.quiblo.source.tmdb.TmdbClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResponse(val results: List<SearchResult> = emptyList())

@Serializable
internal data class SearchResult(val id: Int? = null)

@Serializable
internal data class MovieDetailsDto(
    val overview: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val credits: CreditsDto? = null,
    @SerialName("release_dates") val releaseDates: ReleaseDatesDto? = null,
)

@Serializable
internal data class GenreDto(val name: String? = null)

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
internal fun MovieDetailsDto.toMetadata(): MovieMetadata = MovieMetadata(
    overview = overview?.takeIf { it.isNotBlank() },
    genres = genres.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } },
    ageRating = certificate(),
    // TMDB returns 0.0 for an unrated film, which would render as a genuine score of zero.
    rating = voteAverage?.takeIf { it > 0.0 },
    director = credits?.crew?.firstOrNull { it.job == "Director" }?.name,
    topCast = credits?.cast
        .orEmpty()
        .sortedBy { it.order ?: Int.MAX_VALUE }
        .mapNotNull { it.name }
        .take(TOP_CAST_COUNT),
    posterUrl = posterPath?.let { TmdbClient.IMAGE_BASE_URL + it },
    backdropUrl = backdropPath?.let { TmdbClient.IMAGE_BASE_URL + it },
)

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
