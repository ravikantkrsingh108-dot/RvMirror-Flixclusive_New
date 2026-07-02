package com.rvmirror.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class MirrorClient(
    private val http: OkHttpClient,
) {
    @Volatile private var cookie: String = ""
    @Volatile private var cookieIssuedAt: Long = 0L
    @Volatile private var newTvApiBase: String = ""

    private val postCache = ConcurrentHashMap<String, PostDetails>()

    private val noRedirectClient: OkHttpClient by lazy {
        http.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun homeIds(ott: String): List<String> {
        val body = get(
            url = "${Mirror.BASE}/mobile/home?app=1",
            headers = Mirror.SITE_HEADERS,
            cookie = siteCookie(ott),
            referer = "${Mirror.BASE}/mobile/home?app=1",
        )

        val ids = LinkedHashSet<String>()
        val document = Jsoup.parse(body)
        for (card in document.select("[data-post], article a, .top10-post a, a[href]")) {
            card.extractPostId()?.let(ids::add)
        }
        return ids.toList()
    }

    suspend fun search(ott: String, query: String): List<SearchItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = get(
            url = "${Mirror.BASE}${Mirror.mobilePath(ott, "search.php")}?s=$encoded&t=${unixTime()}",
            headers = Mirror.SITE_HEADERS,
            cookie = siteCookie(ott),
            referer = "${Mirror.BASE}/home",
        )

        val results = JSONObject(body).optJSONArray("searchResult") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val id = item.optStringOrNull("id") ?: continue
                add(SearchItem(id = id, title = item.optString("t").ifBlank { id }))
            }
        }
    }

    suspend fun post(ott: String, rawId: String): PostDetails {
        val key = Mirror.encodeId(ott, rawId)
        postCache[key]?.let { return it }

        val body = get(
            url = "${Mirror.BASE}${Mirror.mobilePath(ott, "post.php")}?id=$rawId&t=${unixTime()}",
            headers = Mirror.SITE_HEADERS,
            cookie = siteCookie(ott),
            referer = "${Mirror.BASE}/home",
        )
        val details = parsePost(JSONObject(body))
        postCache[key] = details
        return details
    }

    suspend fun allEpisodes(ott: String, rawId: String): List<EpItem> {
        val details = post(ott, rawId)
        val episodes = ArrayList(details.firstPageEpisodes)

        if (details.nextPageShow == 1 && !details.nextPageSeason.isNullOrBlank()) {
            episodes += paginateEpisodes(ott, rawId, details.nextPageSeason, startPage = 2)
        }

        details.seasonRefs.dropLast(1).forEach { season ->
            episodes += paginateEpisodes(ott, rawId, season.id, startPage = 1)
        }
        return episodes.distinctBy { it.id }
    }

    suspend fun playerLink(ott: String, playId: String): PlayerLink? {
        val base = resolveNewTvApiBase()
        val body = get(
            url = "$base/newtv/player.php?id=$playId",
            headers = Mirror.NEW_TV_HEADERS + mapOf("Ott" to ott, "Usertoken" to ""),
            cookie = null,
            referer = null,
        )
        val obj = JSONObject(body)
        if (obj.optString("status") != "ok") return null
        val link = obj.optStringOrNull("video_link") ?: return null
        val referer = obj.optStringOrNull("referer") ?: base
        val origin = referer.substringBefore("://") + "://" + referer.substringAfter("://").substringBefore('/')
        val headers = Mirror.NEW_TV_HEADERS + mapOf(
            "Referer" to referer,
            "Origin" to origin,
            "Cookie" to "hd=on",
        )
        return PlayerLink(url = link, referer = referer, headers = headers)
    }

    private suspend fun paginateEpisodes(
        ott: String,
        rawId: String,
        seasonId: String,
        startPage: Int,
    ): List<EpItem> {
        val out = ArrayList<EpItem>()
        var page = startPage
        while (page < startPage + MAX_EPISODE_PAGES) {
            val body = get(
                url = "${Mirror.BASE}${Mirror.mobilePath(ott, "episodes.php")}?s=$seasonId&series=$rawId&t=${unixTime()}&page=$page",
                headers = Mirror.SITE_HEADERS,
                cookie = siteCookie(ott),
                referer = "${Mirror.BASE}/home",
            )
            val obj = JSONObject(body)
            out += parseEpisodes(ott, obj.optJSONArray("episodes"))
            if (obj.optInt("nextPageShow", 0) == 0) break
            page++
        }
        return out
    }

    private suspend fun resolveNewTvApiBase(): String {
        newTvApiBase.takeIf { it.isNotBlank() }?.let { return it }

        for (encoded in Mirror.NEW_TV_DOMAINS) {
            val base = Mirror.decodeBase64(encoded).trimEnd('/')
            try {
                val body = get(base + "/checknewtv.php", Mirror.NEW_TV_HEADERS, null, null)
                val hash = JSONObject(body).optStringOrNull("token_hash")
                if (!hash.isNullOrBlank()) {
                    val resolved = Mirror.decodeBase64(hash).trimEnd('/')
                    newTvApiBase = resolved
                    return resolved
                }
            } catch (_: Exception) {
                // Try the next candidate domain.
            }
        }
        throw IllegalStateException("Failed to resolve the NewTV API base URL")
    }

    private suspend fun accessCookie(): String {
        val now = System.currentTimeMillis()
        cookie.takeIf { it.isNotEmpty() && now - cookieIssuedAt < COOKIE_TTL_MS }?.let { return it }

        val fresh = withContext(Dispatchers.IO) {
            val form = FormBody.Builder()
                .add("g-recaptcha-response", UUID.randomUUID().toString())
                .build()
            val builder = Request.Builder().url(Mirror.VERIFY_URL).post(form)
            Mirror.VERIFY_HEADERS.forEach { (key, value) -> builder.addHeader(key, value) }

            noRedirectClient.newCall(builder.build()).execute().use { response ->
                response.headers("Set-Cookie")
                    .firstOrNull { it.startsWith("t_hash_t=") }
                    ?.substringAfter("t_hash_t=")
                    ?.substringBefore(";")
                    .orEmpty()
            }
        }

        if (fresh.isNotEmpty()) {
            cookie = fresh
            cookieIssuedAt = now
        }
        return fresh
    }

    private suspend fun siteCookie(ott: String): String =
        "t_hash_t=${accessCookie()}; ott=$ott; hd=on"

    private suspend fun get(
        url: String,
        headers: Map<String, String>,
        cookie: String?,
        referer: String?,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> builder.addHeader(key, value) }
        if (cookie != null) builder.addHeader("Cookie", cookie)
        if (referer != null) builder.addHeader("Referer", referer)

        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GET $url failed with HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun parsePost(obj: JSONObject): PostDetails {
        val episodes = obj.optJSONArray("episodes")
        val isMovie = episodes == null || episodes.length() == 0 || episodes.isNull(0)

        return PostDetails(
            title = obj.optString("title").ifBlank { "Untitled" },
            year = obj.optString("year").toIntOrNull(),
            overview = obj.optStringOrNull("desc"),
            genres = obj.optString("genre").splitCsv(),
            cast = obj.optString("cast").splitCsv(),
            runtime = Mirror.runtimeToMinutes(obj.optStringOrNull("runtime")),
            rating = obj.optStringOrNull("match")?.replace("IMDb", "")?.trim()?.toDoubleOrNull(),
            certification = obj.optStringOrNull("ua"),
            isMovie = isMovie,
            suggestionIds = obj.optJSONArray("suggest").mapIds(),
            seasonRefs = obj.optJSONArray("season").mapSeasonRefs(),
            firstPageEpisodes = parseEpisodes(ott, episodes),
            nextPageShow = obj.optInt("nextPageShow", 0),
            nextPageSeason = obj.optStringOrNull("nextPageSeason"),
        )
    }

    private fun parseEpisodes(ott: String, array: JSONArray?): List<EpItem> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val id = obj.optStringOrNull("id") ?: continue
                add(
                    EpItem(
                        id = id,
                        episode = obj.optString("ep").replace("E", "").toIntOrNull(),
                        season = obj.optString("s").replace("S", "").toIntOrNull(),
                        title = obj.optStringOrNull("t"),
                        runtime = obj.optString("time").replace("m", "").trim().toIntOrNull(),
                        image = Mirror.episodeImageOf(ott, id),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.mapIds(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.optStringOrNull("id")?.let { add(it) }
            }
        }
    }

    private fun JSONArray?.mapSeasonRefs(): List<SeasonRef> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.optStringOrNull("id")?.let { add(SeasonRef(it)) }
            }
        }
    }

    private fun Element.extractPostId(): String? {
        attr("data-post").ifBlank { null }?.let { return it }
        selectFirst("[data-post]")?.attr("data-post")?.ifBlank { null }?.let { return it }
        val href = attr("href")
        return Regex("(?:id|post)=([A-Za-z0-9_-]+)").find(href)?.groupValues?.getOrNull(1)
    }

    private fun unixTime(): Long = System.currentTimeMillis() / 1000L

    private companion object {
        private const val COOKIE_TTL_MS = 54_000_000L
        private const val MAX_EPISODE_PAGES = 100
    }
}

internal data class SearchItem(
    val id: String,
    val title: String,
)

internal data class SeasonRef(
    val id: String,
)

internal data class EpItem(
    val id: String,
    val episode: Int?,
    val season: Int?,
    val title: String?,
    val runtime: Int?,
    val image: String?,
)

internal data class PostDetails(
    val title: String,
    val year: Int?,
    val overview: String?,
    val genres: List<String>,
    val cast: List<String>,
    val runtime: Int?,
    val rating: Double?,
    val certification: String?,
    val isMovie: Boolean,
    val suggestionIds: List<String>,
    val seasonRefs: List<SeasonRef>,
    val firstPageEpisodes: List<EpItem>,
    val nextPageShow: Int,
    val nextPageSeason: String?,
)

internal data class PlayerLink(
    val url: String,
    val referer: String?,
    val headers: Map<String, String>,
)

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).ifBlank { null }
}

private fun String.splitCsv(): List<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }