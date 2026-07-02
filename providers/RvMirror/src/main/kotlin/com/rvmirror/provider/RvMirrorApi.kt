package com.rvmirror.provider

import android.content.Context
import com.flixclusive.model.film.Film
import com.flixclusive.model.film.FilmMetadata
import com.flixclusive.model.film.FilmSearchItem
import com.flixclusive.model.film.SearchResponseData
import com.flixclusive.model.film.common.tv.Episode
import com.flixclusive.model.provider.ProviderCatalog
import com.flixclusive.model.provider.link.MediaLink
import com.flixclusive.model.provider.link.Stream
import com.flixclusive.provider.Provider
import com.flixclusive.provider.ProviderApi
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

    override val catalogs: List<ProviderCatalog> by lazy {
        Mirror.PLATFORMS.map { platform ->
            ProviderCatalog(
                name = platform.label,
                url = Mirror.catalogUrl(platform.ott),
                canPaginate = false,
                providerId = provider.name,
            )
        }
    }

    override suspend fun getCatalogItems(
        catalog: ProviderCatalog,
        page: Int,
    ): SearchResponseData<FilmSearchItem> {
        val ott = Mirror.ottFromCatalogUrl(catalog.url)
        val liveIds = runCatching { mirror.homeIds(ott) }.getOrDefault(emptyList())
        idStore.rememberSeen(ott, liveIds)

        val ids = mergeIds(liveIds, idStore.load(ott)).take(MAX_CATALOG_ITEMS)
        val items = enrich(ott, ids)

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

        val ott = id?.let(Mirror::ottOf) ?: Mirror.PLATFORMS.first().ott
        val results = mirror.search(ott, query)
        idStore.rememberSeen(ott, results.map { it.id })
        val items = enrich(ott, results.map { it.id }.take(MAX_SEARCH_ITEMS))

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
            ),
        )
    }

    private suspend fun enrich(ott: String, ids: List<String>): List<FilmSearchItem> =
        coroutineScope {
            ids.chunked(CONCURRENCY).flatMap { chunk ->
                chunk.map { rawId ->
                    async(Dispatchers.IO) {
                        runCatching {
                            val details = mirror.post(ott, rawId)
                            idStore.rememberSeen(ott, listOf(rawId) + details.suggestionIds)
                            details.toFilmSearchItem(ott, rawId, providerId)
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

    private companion object {
        const val MAX_CATALOG_ITEMS = 160
        const val MAX_SEARCH_ITEMS = 80
        const val CONCURRENCY = 8
    }
}