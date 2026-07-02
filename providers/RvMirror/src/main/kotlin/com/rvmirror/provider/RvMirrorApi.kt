package com.rvmirror.provider

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
        val savedCount = idStore.totalCount()
        listOf(
            ProviderCatalog(
                name = "Saved IDs ($savedCount)",
                url = Mirror.savedCatalogUrl(),
                canPaginate = false,
                providerId = provider.name,
            ),
        ) + Mirror.PLATFORMS.map { platform ->
            ProviderCatalog(
                name = "${platform.label} (${idStore.count(platform.ott)})",
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
        if (Mirror.isSavedCatalogUrl(catalog.url)) {
            val liveIds = loadPlatformHomeIds()
            val ids = mergeTitleIds(liveIds, idStore.loadAll()).take(MAX_CATALOG_ITEMS)
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
            .take(MAX_CATALOG_ITEMS)
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

        val searchIds = searchAllPlatforms(query).take(MAX_SEARCH_ITEMS)
        val items = enrich(searchIds)

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

    private suspend fun searchAllPlatforms(query: String): List<MirrorTitleId> = coroutineScope {
        Mirror.PLATFORMS.map { platform ->
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

    private companion object {
        const val MAX_CATALOG_ITEMS = 300
        const val MAX_SEARCH_ITEMS = 120
        const val CONCURRENCY = 8
    }
}