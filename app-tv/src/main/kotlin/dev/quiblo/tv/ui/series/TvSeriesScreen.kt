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

package dev.quiblo.tv.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.data.MERGED_SEASON_NUMBER
import dev.quiblo.core.data.MetadataRefresh
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.Opinion
import dev.quiblo.core.model.Season
import dev.quiblo.designsystem.ambientBackdrop
import dev.quiblo.designsystem.rememberAmbient
import dev.quiblo.feature.browse.runtimeLabel
import dev.quiblo.feature.series.SeriesDetailUiState
import dev.quiblo.feature.series.SeriesDetailViewModel
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.detail.DETAIL_COLUMN_GAP
import dev.quiblo.tv.ui.detail.DetailArtwork
import dev.quiblo.tv.ui.detail.DetailButton
import dev.quiblo.tv.ui.detail.DetailFacts
import dev.quiblo.tv.ui.detail.DetailOverview
import dev.quiblo.tv.ui.detail.DetailTitle
import dev.quiblo.tv.ui.detail.genresOrEmpty
import dev.quiblo.tv.ui.detail.messageRes
import dev.quiblo.tv.ui.detail.openDetailScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A series, and the episode to play.
 *
 * This screen exists because a series row is not playable. Its `streamUrl` is not an
 * episode — episodes are fetched from the panel per series and are never rows in the
 * channel table — so the television handing a series straight to the player could only ever
 * produce the "plays nothing, shows a series as a channel" behaviour reported as #009.
 *
 * It carries everything a film's screen carries — artwork, plot, score, cast, favouriting
 * and a resume point — because #007's complaint was precisely that a series had none of
 * them. The presentation is shared with the film screen rather than written twice, so the
 * two cannot drift into showing different facts about the same kind of thing.
 */
@Composable
fun TvSeriesScreen(
    channel: Channel,
    /**
     * Play this episode, out of this run, from here.
     *
     * The run travels with the press rather than being looked up later, because this screen is
     * the only one that has the episodes at all — see `TvPlaybackRequest.Episode.run`.
     */
    onPlayEpisode: (Episode, List<Episode>, Long?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    focusEpisodeId: String? = null,
) {
    val viewModel: SeriesDetailViewModel = koinViewModel(
        key = "tv-series-${channel.id}",
        parameters = { parametersOf(channel.id) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // TMDB's poster first, the provider's cover second, its logo last — the same order the phone
    // screen uses. A provider logo is often a channel bug rather than artwork, and light taken
    // from one is light from the wrong picture.
    val artworkUrl = (state as? SeriesDetailUiState.Success)?.let { success ->
        success.metadata?.posterUrl
            ?: success.details.coverUrl?.takeIf { it.isNotBlank() }
            ?: success.channel.logoUrl
    } ?: channel.logoUrl
    val ambient = rememberAmbient(artworkUrl)

    // The resume point is watched rather than re-read on returning to the foreground. A read on
    // resume raced the player's own write of the position it had just finished with, and lost it
    // often enough that backing out of a film offered "Play" for something four minutes in. See
    // `observeResumePosition`.

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .ambientBackdrop(ambient),
    ) {
        when (val current = state) {
            is SeriesDetailUiState.Loading -> Centered { CircularProgressIndicator(color = Color.White) }

            is SeriesDetailUiState.Error -> Centered {
                Text(
                    text = stringResource(R.string.tv_series_unavailable),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            is SeriesDetailUiState.Success -> Loaded(
                state = current,
                onPlayEpisode = onPlayEpisode,
                onToggleFavorite = viewModel::toggleFavorite,
                onRemoveFromHistory = viewModel::removeFromHistory,
                onRefreshMetadata = viewModel::refreshMetadata,
                onRate = viewModel::rate,
                onMerged = viewModel::setMerged,
                onDescending = viewModel::setDescending,
                focusEpisodeId = focusEpisodeId,
            )
        }
    }
}

private fun findReturningSeason(seasons: List<Season>, focusEpisodeId: String?): Int? =
    focusEpisodeId?.let { id ->
        seasons.indexOfFirst { season -> season.episodes.any { it.id == id } }.takeIf { it >= 0 }
    }

@Composable
private fun SeriesArrangementRow(
    isMerged: Boolean,
    isDescending: Boolean,
    onMerged: (Boolean) -> Unit,
    onDescending: (Boolean) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Its own focus group, so walking along it with the remote does not step into
        // the season chips below by accident.
        modifier = Modifier
            .padding(top = 6.dp)
            .focusGroup(),
    ) {
        item {
            SeasonChip(
                label = stringResource(R.string.tv_series_merge_seasons),
                isSelected = isMerged,
                onClick = { onMerged(!isMerged) },
            )
        }
        item {
            SeasonChip(
                label = stringResource(R.string.tv_series_newest_first),
                isSelected = isDescending,
                onClick = { onDescending(!isDescending) },
            )
        }
    }
}

@Composable
private fun SeriesSeasonsRow(
    seasons: List<Season>,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        itemsIndexed(
            items = seasons,
            key = { _, season -> season.seasonNumber },
        ) { index, season ->
            val label = if (season.seasonNumber == MERGED_SEASON_NUMBER && season.name.isEmpty()) {
                stringResource(R.string.tv_series_all_episodes)
            } else {
                season.name.ifBlank {
                    stringResource(R.string.tv_series_season, season.seasonNumber)
                }
            }
            SeasonChip(
                label = label,
                isSelected = index == selectedSeason,
                onClick = { onSelectSeason(index) },
            )
        }
    }
}

@Composable
private fun Loaded(
    state: SeriesDetailUiState.Success,
    onPlayEpisode: (Episode, List<Episode>, Long?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onRate: (Opinion) -> Unit,
    onMerged: (Boolean) -> Unit,
    onDescending: (Boolean) -> Unit,
    focusEpisodeId: String?,
) {
    val seasons = state.seasons.ifEmpty { state.details.seasons }

    /*
     * The run is the same for every press on this screen, so it is bound once here and the
     * parts below keep the two-argument callback they already had. Threading a third argument
     * through the header, the actions and the rows would have put the same constant in four
     * signatures for no reader's benefit.
     */
    val run = remember(seasons) { episodeRun(seasons) }
    val play: (Episode, Long?) -> Unit = { episode, resumeFrom -> onPlayEpisode(episode, run, resumeFrom) }

    // Returning to an episode means returning to its season, not to the first one.
    val returningSeason = remember(state.details.seriesId, focusEpisodeId) {
        findReturningSeason(seasons, focusEpisodeId)
    }
    // Also keyed on the arrangement: merging collapses several seasons into one, so an index
    // of 4 points at nothing the moment the switch is flipped.
    var selectedSeason by remember(state.details.seriesId, state.preference) {
        mutableIntStateOf(returningSeason ?: 0)
    }
    val episodes = seasons.getOrNull(selectedSeason)?.episodes.orEmpty()

    val firstAction = remember { FocusRequester() }
    val episodeCursor = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /*
     * Where the remote lands, and what the screen is showing when it does.
     *
     * Both cases live in `openDetailScreen`, shared with the film screen. They had a copy each
     * and the copies disagreed, which is how #015 came back on this screen after being fixed on
     * that one — see that function for what the panel measured and why a lazy list needs more
     * than a single reset.
     */
    // The header is one item, and the season strip is another when there is more than one
    // season — so an episode's index in the list is its index in the season plus those.
    val leadingItems = 1 + if (seasons.size > 1) 1 else 0

    LaunchedEffect(state.details.seriesId, focusEpisodeId) {
        val cursor = focusEpisodeId
            ?.let { id -> episodes.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }

        openDetailScreen(
            listState = listState,
            firstAction = firstAction,
            episodeCursor = episodeCursor.takeIf { cursor != null },
            episodeIndex = cursor?.let { leadingItems + it },
        )
    }

    /*
     * The whole screen is one scrolling list, header included.
     *
     * It used to be a fixed header above a list that got whatever height was left. On this
     * hardware that is close to nothing: the panel is 960x540dp, so after overscan there are
     * 444dp to spend, and the cover, title, plot and actions consumed nearly all of it. The
     * episode list was a sliver, and the season picker sat below the fold with no way to
     * reach it, so a series with more than one season could not be navigated at all.
     *
     * As one list, every part is an item the remote walks through in order: actions, then
     * seasons, then episodes. Nothing can be pushed out of reach, because reaching something
     * is now just scrolling to it.
     */
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            /*
             * The header is one scrolling item, and bringing the buttons back into view is what
             * brings the rest of the page with them (`027` #3).
             *
             * Without this, navigating up from the episodes would land on the buttons at whatever
             * scroll position the list happened to be in, which on this panel left the title and
             * artwork partially or entirely cut off.
             */
            Box(
                modifier = Modifier.onFocusChanged { focusState ->
                    val isScrolled = listState.firstVisibleItemIndex > 0 ||
                        listState.firstVisibleItemScrollOffset > 0
                    if (focusState.hasFocus && isScrolled) {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                },
            ) {
                SeriesHeader(
                    state = state,
                    seasonCount = seasons.size,
                    firstAction = firstAction,
                    onPlayEpisode = play,
                    onToggleFavorite = onToggleFavorite,
                    onRemoveFromHistory = onRemoveFromHistory,
                    onRefreshMetadata = onRefreshMetadata,
                    onRate = onRate,
                )
            }
        }

        if (seasons.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.tv_series_no_episodes),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            return@LazyColumn
        }

        item(key = "arrangement") {
            SeriesArrangementRow(
                isMerged = state.preference.isMerged,
                isDescending = state.preference.isDescending,
                onMerged = onMerged,
                onDescending = onDescending,
            )
        }

        if (seasons.size > 1) {
            item(key = "seasons") {
                SeriesSeasonsRow(
                    seasons = seasons,
                    selectedSeason = selectedSeason,
                    onSelectSeason = { selectedSeason = it },
                )
            }
        }

        items(items = episodes, key = { it.id }) { episode ->
            val isResume = episode.id == state.resumeEpisode?.id
            EpisodeRow(
                episode = episode,
                onClick = {
                    play(episode, state.resumePositionMillis.takeIf { isResume })
                },
                modifier = if (episode.id == focusEpisodeId) {
                    Modifier.focusRequester(episodeCursor)
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * Artwork, facts, plot and the actions — everything above the season strip.
 *
 * Its own composable rather than an inline `item` because it is the half of this screen that
 * has nothing to do with navigating episodes, and keeping the two apart is what lets the
 * function that does the navigating stay readable.
 */
@Composable
private fun SeriesHeader(
    state: SeriesDetailUiState.Success,
    seasonCount: Int,
    firstAction: FocusRequester,
    onPlayEpisode: (Episode, Long?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onRate: (Opinion) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(DETAIL_COLUMN_GAP)) {
        DetailArtwork(
            url = state.channel.logoUrl?.takeIf { it.isNotBlank() }
                ?: state.details.coverUrl
                ?: state.metadata?.posterUrl,
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            DetailTitle(state.details.title)

            DetailFacts(
                rating = state.metadata?.rating,
                ageRating = state.metadata?.ageRating,
                genres = state.metadata.genresOrEmpty(),
                // The panel's year first, the service's second, and no length at all: a series
                // does not have one, and the average episode length a service offers is not the
                // length of anything anybody is about to watch.
                year = state.details.releaseYear ?: state.metadata?.releaseYear,
                extra = pluralSeasons(seasonCount),
            )

            DetailOverview(
                overview = state.details.overview?.takeIf { it.isNotBlank() }
                    ?: state.metadata?.overview,
                // False rather than a null check on metadata: the panel details are already
                // loaded, and the metadata service is only asked when a key is configured, so
                // "no metadata" is the permanent answer for most users and a spinner against
                // it would never resolve.
                isEnriching = false,
                author = state.metadata?.author,
                authorLabel = state.metadata?.authorLabel?.name
                    ?.lowercase()?.replaceFirstChar(Char::uppercase),
                cast = state.metadata?.topCast.orEmpty(),
            )

            SeriesActions(
                state = state,
                firstAction = firstAction,
                onPlayEpisode = onPlayEpisode,
                onToggleFavorite = onToggleFavorite,
                onRemoveFromHistory = onRemoveFromHistory,
                onRefreshMetadata = onRefreshMetadata,
                onRate = onRate,
            )
        }
    }
}

/** Resume, play or start again, favouriting and forgetting — the row the remote lands on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList")
private fun SeriesActions(
    state: SeriesDetailUiState.Success,
    firstAction: FocusRequester,
    onPlayEpisode: (Episode, Long?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onRate: (Opinion) -> Unit,
) {
    val hasResume = state.resumeEpisode != null

    // Wraps rather than overflows.
    //
    // A `Row` cannot make room it does not have: with a resume button, a start-again button, a
    // favourites button and now a history one, the last of them ran off the edge and was cut
    // mid-word (#014). Shortening a label buys one release and loses the next one to a
    // translation. Wrapping cannot be lost that way, and a second line of buttons is reachable
    // by the D-pad for free because it is still one focus group.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(top = 14.dp)
            .focusGroup(),
    ) {
        state.resumeEpisode?.let { episode ->
            DetailButton(
                icon = Icons.Filled.PlayArrow,
                label = stringResource(
                    R.string.tv_series_resume,
                    episode.seasonNumber,
                    episode.episodeNumber,
                ),
                onClick = { onPlayEpisode(episode, state.resumePositionMillis) },
                isPrimary = true,
                modifier = Modifier.focusRequester(firstAction),
            )
        }

        state.firstEpisode?.let { first ->
            DetailButton(
                icon = if (hasResume) Icons.Filled.SkipPrevious else Icons.Filled.PlayArrow,
                label = if (hasResume) null else stringResource(R.string.tv_detail_play),
                contentDescription = stringResource(
                    if (hasResume) R.string.tv_detail_from_start else R.string.tv_detail_play,
                ),
                onClick = { onPlayEpisode(first, null) },
                isPrimary = !hasResume,
                modifier = if (hasResume) Modifier else Modifier.focusRequester(firstAction),
            )
        }

        DetailButton(
            icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(
                if (state.isFavorite) R.string.tv_detail_unfavourite else R.string.tv_detail_favourite
            ),
            onClick = onToggleFavorite,
        )

        // What the viewer thought — about the series, not the episode. Nobody has an opinion
        // about episode four of season two separately from the show, and a thumbs-down on one
        // episode that removed the whole series from suggestions would answer a question that
        // was not asked.
        SeriesRatingButtons(opinion = state.opinion, onRate = onRate)

        // Only when there is something to remove. A control that would do nothing is the
        // hollow-feature shape this project has deleted nine of — and here it is also the
        // honest signal that this series is on the continue-watching row at all.
        //
        // The phone has offered this since its detail screens were built and the television
        // never did, which is the parity gap `agile/004` generalised: the two frontends drift
        // wherever nobody looked (#014).
        if (hasResume) {
            DetailButton(
                icon = Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.tv_detail_remove_history),
                onClick = onRemoveFromHistory,
            )
        }

        // Last, for the same reason as on the film screen: it is the control a viewer reaches
        // for only when the screen already looks wrong, and it must not sit between Resume and
        // the favourite on the way there.
        if (state.canRefreshMetadata) {
            DetailButton(
                icon = Icons.Filled.Refresh,
                contentDescription = stringResource(
                    if (state.isEnriching) R.string.tv_detail_refresh_working else R.string.tv_detail_refresh
                ),
                onClick = onRefreshMetadata,
            )
        }
    }

    state.refreshResult?.let { result ->
        Text(
            text = stringResource(result.messageRes()),
            color = if (result is MetadataRefresh.Refused) {
                Color(0xFFFF8A80)
            } else {
                Color.White.copy(alpha = 0.75f)
            },
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun SeasonChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Text(
        text = label,
        color = Color.White.copy(alpha = if (isFocused || isSelected) 1f else 0.6f),
        fontSize = 15.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val alpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else IDLE_ALPHA,
        label = "episodeAlpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                R.string.tv_series_episode_number,
                episode.seasonNumber,
                episode.episodeNumber,
            ),
            color = Color.White.copy(alpha = alpha * 0.75f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(88.dp),
        )

        Text(
            text = episode.title,
            color = Color.White.copy(alpha = alpha),
            fontSize = 16.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Takes the space the title needs and leaves the length at the end of the row,
            // where the eye finds it in the same place on every line.
            modifier = Modifier.weight(1f, fill = true),
        )

        // Omitted rather than blanked where the panel does not time its episodes, which is most
        // of them: a column of dashes says less than no column at all.
        runtimeLabel(episode.durationSeconds)?.let { length ->
            Text(
                text = length,
                color = Color.White.copy(alpha = alpha * 0.6f),
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * Every episode of a series, in the order somebody watches them.
 *
 * A plain function, and public to its module, because this is the whole definition of what
 * "next episode" means and it deserves a test rather than a reading. It is deliberately not
 * derived from what the screen is drawing: the season strip can be reversed and the seasons
 * can be merged, and neither of those changes which episode follows which.
 *
 * Sorting by season and then by number covers both arrangements with one rule. Merging puts
 * everything in a single season whose own number is a sentinel, but the episodes inside it
 * keep the season they came from, so they sort back into their real order here.
 */
internal fun episodeRun(seasons: List<Season>): List<Episode> =
    seasons.flatMap { it.episodes }.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private const val IDLE_ALPHA = 0.65f

@Composable
private fun SeriesRatingButtons(
    opinion: Opinion,
    onRate: (Opinion) -> Unit,
) {
    DetailButton(
        icon = if (opinion == Opinion.UP) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
        contentDescription = stringResource(
            if (opinion == Opinion.UP) {
                R.string.tv_detail_liked
            } else {
                R.string.tv_detail_like
            },
        ),
        onClick = { onRate(Opinion.UP) },
    )

    DetailButton(
        icon = if (opinion == Opinion.DOWN) {
            Icons.Filled.ThumbDown
        } else {
            Icons.Outlined.ThumbDown
        },
        contentDescription = stringResource(
            if (opinion == Opinion.DOWN) {
                R.string.tv_detail_disliked
            } else {
                R.string.tv_detail_dislike
            },
        ),
        onClick = { onRate(Opinion.DOWN) },
    )
}

/** "3 seasons", or nothing at all when the provider listed none. */
@Composable
private fun pluralSeasons(count: Int): String? =
    if (count <= 0) null else pluralStringResource(R.plurals.tv_series_seasons, count, count)
