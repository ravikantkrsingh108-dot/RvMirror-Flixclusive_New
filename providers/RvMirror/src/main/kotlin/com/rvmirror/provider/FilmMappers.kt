package com.rvmirror.provider

import com.flixclusive.model.film.FilmSearchItem
import com.flixclusive.model.film.Genre
import com.flixclusive.model.film.Movie
import com.flixclusive.model.film.TvShow
import com.flixclusive.model.film.common.tv.Episode
import com.flixclusive.model.film.common.tv.Season
import com.flixclusive.model.film.util.FilmType

internal fun PostDetails.toFilmSearchItem(
    ott: String,
    rawId: String,
    providerId: String,
): FilmSearchItem = FilmSearchItem(
    id = Mirror.encodeId(ott, rawId),
    providerId = providerId,
    filmType = if (isMovie) FilmType.MOVIE else FilmType.TV_SHOW,
    homePage = null,
    title = title,
    posterImage = Mirror.posterOf(ott, rawId),
    rating = rating,
    overview = overview,
    year = year,
    genres = genres.map { Genre(id = 0, name = it) },
)

internal fun PostDetails.toMovie(
    ott: String,
    rawId: String,
    providerId: String,
): Movie = Movie(
    id = Mirror.encodeId(ott, rawId),
    title = title,
    posterImage = Mirror.posterOf(ott, rawId),
    homePage = null,
    backdropImage = Mirror.backdropOf(ott, rawId),
    providerId = providerId,
    rating = rating,
    overview = overview,
    year = year,
    runtime = runtime,
    genres = genres.map { Genre(id = 0, name = it) },
)

internal fun PostDetails.toTvShow(
    ott: String,
    rawId: String,
    providerId: String,
    episodes: List<EpItem>,
): TvShow {
    val seasons = episodes
        .groupBy { it.season ?: 1 }
        .toSortedMap()
        .map { (number, items) ->
            Season(
                name = "Season $number",
                number = number,
                episodes = items
                    .sortedBy { it.episode ?: 0 }
                    .map { it.toEpisode(ott, number) },
            )
        }

    return TvShow(
        id = Mirror.encodeId(ott, rawId),
        title = title,
        posterImage = Mirror.posterOf(ott, rawId),
        homePage = null,
        backdropImage = Mirror.backdropOf(ott, rawId),
        providerId = providerId,
        rating = rating,
        overview = overview,
        year = year,
        genres = genres.map { Genre(id = 0, name = it) },
        seasons = seasons,
        totalSeasons = seasons.size,
        totalEpisodes = episodes.size,
    )
}

private fun EpItem.toEpisode(ott: String, fallbackSeason: Int): Episode = Episode(
    id = Mirror.encodeId(ott, id),
    number = episode ?: 0,
    season = season ?: fallbackSeason,
    title = title ?: "",
    image = image,
    runtime = runtime,
)