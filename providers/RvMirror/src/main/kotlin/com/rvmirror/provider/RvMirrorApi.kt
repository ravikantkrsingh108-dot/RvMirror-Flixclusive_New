package com.flixclusive.provider.rvmirror

import android.content.Context
import com.flixclusive.model.film.Film
import com.flixclusive.model.film.FilmMetadata
import com.flixclusive.model.film.FilmSearchItem
import com.flixclusive.model.film.SearchResponseData
import com.flixclusive.model.film.common.tv.Episode
import com.flixclusive.model.provider.ProviderCatalog
import com.flixclusive.model.provider.link.Flag
import com.flixclusive.model.provider.link.MediaLink
import com.flixclusive.model.provider.link.Stream
import com.flixclusive.provider.Provider
import com.flixclusive.provider.ProviderApi
import com.flixclusive.provider.filter.Filter
import com.flixclusive.provider.filter.FilterGroup
import com.flixclusive.provider.filter.FilterList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

class RvMirrorApi(
    context: Context,
    client: OkHttpClient,
    provider: Provider,
) : ProviderApi(client, provider) {

    private val mirror = MirrorClient(client)
    private val idStore = MirrorIdStore(context.applicationContext)
    private val providerId get() = provider.name

    override val catalogs: List<ProviderCatalog>
        get() {
        val savedCount = idStore.totalCount()
        return listOf(
            ProviderCatalog(
                name = "Saved IDs ($savedCount)",
                url = Mirror.savedCatalogUrl(),
                canPaginate = false,
                providerId = provider.name,
            ),
            ProviderCatalog(
                name = "Saved Movies (${idStore.countByType(true)})",
                url = Mirror.savedTypeCatalogUrl("movie"),
                canPaginate = false,
                providerId = provider.name,
            ),
            ProviderCatalog(
                name = "Saved TV Shows (${idStore.countByType(false)})",
                url = Mirror.savedTypeCatalogUrl("tv"),
                canPaginate = false,
                providerId = provider.name,
            ),
        ) + idStore.years().map { year ->
            ProviderCatalog(
                name = "Saved $year (${idStore.countByYear(year)})",
                url = Mirror.savedYearCatalogUrl(year),
                canPaginate = false,
                providerId = provider.name,
            )
        } + idStore.genres().map { genre ->
            ProviderCatalog(
                name = "$genre (${idStore.countByGenre(genre)})",
                url = Mirror.savedGenreCatalogUrl(genre),
                canPaginate = false,
                providerId = provider.name,
            )
        } + Mirror.PLATFORMS.map { platform ->
            ProviderCatalog(
                name = "${platform.label} (${idStore.count(platform.ott)})",
                url = Mirror.catalogUrl(platform.ott),
                canPaginate = false,
                providerId = provider.name,
            )
        }
    }

    override val filters: FilterList
        get() = FilterList(
            FilterGroup(
                "Filters",
                Filter.Select("Platform", listOf("All") + Mirror.PLATFORMS.map { it.label }, 0),
                Filter.Select("Type", listOf("All", "Movies", "TV Shows"), 0),
                Filter.Select("Year", listOf("Any") + idStore.years().map(Int::toString), 0),
                Filter.Select("Genre", listOf("Any") + idStore.genres(), 0),
            ),
        )

    override suspend fun getCatalogItems(
        catalog: ProviderCatalog,
        page: Int,
    ): SearchResponseData<FilmSearchItem> {
        if (Mirror.isSavedCatalogUrl(catalog.url)) {
            val ids = when (Mirror.savedCatalogKind(catalog.url)) {
                "type" -> when (Mirror.savedCatalogValue(catalog.url)) {
                    "movie" -> idStore.loadByType(true)
                    "tv" -> idStore.loadByType(false)
                    else -> emptyList()
                }
                "year" -> Mirror.savedCatalogValue(catalog.url)?.toIntOrNull()?.let(idStore::loadByYear).orEmpty()
                "genre" -> Mirror.savedCatalogValue(catalog.url)?.let(idStore::loadByGenre).orEmpty()
                else -> mergeTitleIds(loadPlatformHomeIds(), idStore.loadAll())
            }
            val items = enrich(ids)

            return SearchResponseData(
                page = 1,
                results = items,
                hasNextPage = false,
                totalPages = 1,
            )
        }

        val ott = Mirror.ottFromCatalogUrl(catalog.url)
        val liveIds = runCatching { mirror.homeIds(ott) }.getOrDefault(emptyList())
        idStore.rememberSeen(ott, liveIds)

        val ids = mergeIds(liveIds, idStore.load(ott))
            .map { MirrorTitleId(ott, it) }
        val items = enrich(ids)

        return SearchResponseData(
            page = 1,
            results = items,
            hasNextPage = false,
            totalPages = 1,
        )
    }

    override suspend fun search(
        title: String,
        page: Int,
        id: String?,
        imdbId: String?,
        tmdbId: Int?,
        filters: FilterList,
    ): SearchResponseData<FilmSearchItem> {
        val query = title.trim()
        if (query.isBlank()) return SearchResponseData()

        val filterOptions = filters.toMirrorFilterOptions()
        val searchIds = searchAllPlatforms(query, filterOptions)
        val items = enrich(searchIds).applyFilters(filterOptions)

        return SearchResponseData(
            page = 1,
            results = items,
            hasNextPage = false,
            totalPages = 1,
        )
    }

    override suspend fun getMetadata(film: Film): FilmMetadata {
        val (ott, rawId) = Mirror.decodeId(film.identifier)
        val details = mirror.post(ott, rawId)
        idStore.rememberSeen(ott, listOf(rawId) + details.suggestionIds)

        if (details.isMovie) {
            return details.toMovie(ott, rawId, providerId)
        }

        val episodes = mirror.allEpisodes(ott, rawId)
        if (episodes.isEmpty()) {
            return details.toMovie(ott, rawId, providerId)
        }

        return details.toTvShow(ott, rawId, providerId, episodes)
    }

    override suspend fun getLinks(
        watchId: String,
        film: FilmMetadata,
        episode: Episode?,
        onLinkFound: (MediaLink) -> Unit,
    ) {
        val source = film.identifier.ifBlank { watchId }
        val ott = Mirror.ottOf(source)
        val playId = episode?.let { Mirror.rawIdOf(it.id) } ?: Mirror.rawIdOf(source)

        val link = mirror.playerLink(ott, playId) ?: return
        if (link.url.isBlank()) return

        onLinkFound(
            Stream(
                name = film.title.ifBlank { "RvMirror" },
                url = link.url,
                flags = setOf(
                    Flag.RequiresAuth(customHeaders = link.headers),
                ),
            ),
        )
    }

    private suspend fun loadPlatformHomeIds(): List<MirrorTitleId> = coroutineScope {
        Mirror.PLATFORMS.map { platform ->
            async(Dispatchers.IO) {
                val ids = runCatching { mirror.homeIds(platform.ott) }.getOrDefault(emptyList())
                idStore.rememberSeen(platform.ott, ids)
                ids.map { MirrorTitleId(platform.ott, it) }
            }
        }.awaitAll().flatten()
    }

    private suspend fun searchAllPlatforms(query: String, options: MirrorFilterOptions): List<MirrorTitleId> = coroutineScope {
        Mirror.PLATFORMS.filter { platform -> options.platform == null || platform.ott == options.platform }.map { platform ->
            async(Dispatchers.IO) {
                val results = runCatching { mirror.search(platform.ott, query) }.getOrDefault(emptyList())
                idStore.rememberSeen(platform.ott, results.map { it.id })
                results.map { MirrorTitleId(platform.ott, it.id) }
            }
        }.awaitAll().flatten().distinctBy { Mirror.encodeId(it.ott, it.rawId) }
    }

    private suspend fun enrich(ids: List<MirrorTitleId>): List<FilmSearchItem> =
        coroutineScope {
            ids.chunked(CONCURRENCY).flatMap { chunk ->
                chunk.map { titleId ->
                    async(Dispatchers.IO) {
                        runCatching {
                            val details = mirror.post(titleId.ott, titleId.rawId)
                            idStore.rememberSeen(titleId.ott, listOf(titleId.rawId) + details.suggestionIds)
                            idStore.rememberDetails(titleId.ott, titleId.rawId, details)
                            details.toFilmSearchItem(titleId.ott, titleId.rawId, providerId)
                        }.getOrNull()
                    }
                }.awaitAll()
            }
        }.filterNotNull()

    private fun mergeIds(primary: List<String>, cached: List<String>): List<String> =
        LinkedHashSet<String>().apply {
            addAll(primary)
            addAll(cached)
        }.toList()

    private fun mergeTitleIds(primary: List<MirrorTitleId>, cached: List<MirrorTitleId>): List<MirrorTitleId> =
        LinkedHashMap<String, MirrorTitleId>().apply {
            (primary + cached).forEach { titleId ->
                putIfAbsent(Mirror.encodeId(titleId.ott, titleId.rawId), titleId)
            }
        }.values.toList()

    private fun FilterList.toMirrorFilterOptions(): MirrorFilterOptions {
        val group = firstOrNull()
        val platformState = (group?.getOrNull(0) as? Filter.Select<*>)?.state ?: 0
        val typeState = (group?.getOrNull(1) as? Filter.Select<*>)?.state ?: 0
        val yearState = (group?.getOrNull(2) as? Filter.Select<*>)?.state ?: 0
        val genreState = (group?.getOrNull(3) as? Filter.Select<*>)?.state ?: 0
        val years = idStore.years()
        val genres = idStore.genres()

        return MirrorFilterOptions(
            platform = Mirror.PLATFORMS.getOrNull(platformState - 1)?.ott,
            type = typeState,
            year = years.getOrNull(yearState - 1),
            genre = genres.getOrNull(genreState - 1),
        )
    }

    private fun List<FilmSearchItem>.applyFilters(options: MirrorFilterOptions): List<FilmSearchItem> =
        filter { item ->
            val matchesType = when (options.type) {
                FILTER_MOVIES -> item.filmType.name.equals("MOVIE", ignoreCase = true)
                FILTER_TV_SHOWS -> item.filmType.name.equals("TV_SHOW", ignoreCase = true)
                else -> true
            }
            val matchesYear = options.year == null || item.year == options.year
            val matchesGenre = options.genre == null || item.genres.any { it.name.equals(options.genre, ignoreCase = true) }
            matchesType && matchesYear && matchesGenre
        }

    private companion object {
        const val CONCURRENCY = 8
        const val FILTER_MOVIES = 1
        const val FILTER_TV_SHOWS = 2
    }
}

private data class MirrorFilterOptions(
    val platform: String?,
    val type: Int,
    val year: Int?,
    val genre: String?,
)