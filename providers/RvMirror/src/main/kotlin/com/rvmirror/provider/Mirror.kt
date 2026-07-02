package com.rvmirror.provider

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Calendar

internal object Mirror {
    const val BASE = "https://net52.cc"
    const val VERIFY_URL = "$BASE/verify.php"
    const val POSTER_CDN = "https://imgcdn.kim/poster/v"
    const val EPISODE_CDN = "https://imgcdn.kim/epimg/150"

    val PLATFORMS: List<Platform> = listOf(
        Platform(ott = "nf", label = "Netflix"),
        Platform(ott = "pv", label = "Prime Video"),
        Platform(ott = "hs", label = "Hotstar"),
    )

    data class Platform(val ott: String, val label: String)

    val SITE_HEADERS: Map<String, String> = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-IN,en-US;q=0.9,en;q=0.8",
        "Cache-Control" to "max-age=0",
        "Connection" to "keep-alive",
        "sec-ch-ua" to "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Android WebView\";v=\"144\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Android\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "same-origin",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Pixel 5 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/144.0.7559.132 Safari/537.36 /OS.Gatu v3.0",
        "X-Requested-With" to "XMLHttpRequest",
    )

    val VERIFY_HEADERS: Map<String, String> = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Cache-Control" to "max-age=0",
        "Connection" to "keep-alive",
        "Content-Type" to "application/x-www-form-urlencoded",
        "Origin" to BASE,
        "Referer" to "$BASE/verify2",
        "sec-ch-ua" to "\"Google Chrome\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Windows\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "same-origin",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
    )

    val NEW_TV_HEADERS: Map<String, String> = mapOf(
        "Cache-Control" to "no-cache, no-store, must-revalidate",
        "Pragma" to "no-cache",
        "Expires" to "0",
        "X-Requested-With" to "NetmirrorNewTV v1.0",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",
        "Accept" to "application/json, text/plain, */*",
    )

    val NEW_TV_DOMAINS: List<String> = listOf(
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3RzLmNvbQ==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmFwcA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmFydA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNj",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmxpdmU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnBybw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNpdGU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNwYWNl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnZpcA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lnh5eg==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5hcnQ=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5jYw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbmZv",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbms=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5saXZl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5wcm8=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5zdG9yZQ==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy50b3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy54eXo=",
    )

    fun posterOf(ott: String, id: String): String = when (ott) {
        "pv" -> "https://imgcdn.kim/pv/v/$id.jpg"
        "hs" -> "https://imgcdn.kim/hs/v/$id.jpg"
        else -> "$POSTER_CDN/$id.jpg"
    }

    fun backdropOf(ott: String, id: String): String = when (ott) {
        "pv" -> "https://imgcdn.kim/pv/h/$id.jpg"
        "hs" -> "https://imgcdn.kim/hs/h/$id.jpg"
        else -> posterOf(ott, id)
    }

    fun episodeImageOf(ott: String, id: String): String = when (ott) {
        "pv" -> "https://imgcdn.kim/pvepimg/$id.jpg"
        "hs" -> "https://imgcdn.kim/hsepimg/$id.jpg"
        else -> "$EPISODE_CDN/$id.jpg"
    }

    fun mobilePath(ott: String, endpoint: String): String = when (ott) {
        "pv", "hs" -> "/mobile/$ott/$endpoint"
        else -> "/mobile/$endpoint"
    }

    fun decodeBase64(value: String): String =
        String(Base64.decode(value, Base64.DEFAULT)).trim()

    fun encodeId(ott: String, rawId: String): String = "$ott:$rawId"

    fun decodeId(id: String): Pair<String, String> {
        val separator = id.indexOf(':')
        if (separator < 0) return PLATFORMS.first().ott to id
        return id.substring(0, separator) to id.substring(separator + 1)
    }

    fun rawIdOf(id: String): String = decodeId(id).second

    fun ottOf(id: String): String = decodeId(id).first

    fun savedCatalogUrl(): String = "rvmirror://saved"

    fun isSavedCatalogUrl(url: String): Boolean = ottFromCatalogUrl(url) == "saved"

    fun savedYearCatalogUrl(year: Int): String = "rvmirror://saved/year/$year"

    fun savedGenreCatalogUrl(genre: String): String = "rvmirror://saved/genre/${genre.urlEncode()}"

    fun savedTypeCatalogUrl(type: String): String = "rvmirror://saved/type/$type"

    fun savedCatalogKind(url: String): String? =
        url.substringAfter("rvmirror://saved/", "").substringBefore('/').takeIf { it.isNotBlank() }

    fun savedCatalogValue(url: String): String? =
        url.substringAfter("rvmirror://saved/${savedCatalogKind(url)}/", "").takeIf { it.isNotBlank() }?.urlDecode()

    fun catalogUrl(ott: String): String = "rvmirror://$ott"

    fun ottFromCatalogUrl(url: String): String = url.substringAfter("://").substringBefore('/')

    fun yearToReleaseDate(year: Int?): Long? {
        if (year == null) return null
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, Calendar.JANUARY, 1)
        return calendar.timeInMillis
    }

    fun runtimeToMinutes(runtime: String?): Int? {
        if (runtime.isNullOrBlank()) return null
        var total = 0
        for (part in runtime.split(" ")) {
            when {
                part.endsWith("h") -> total += (part.removeSuffix("h").trim().toIntOrNull() ?: 0) * 60
                part.endsWith("m") -> total += part.removeSuffix("m").trim().toIntOrNull() ?: 0
            }
        }
        return total.takeIf { it > 0 }
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    private fun String.urlDecode(): String = URLDecoder.decode(this, "UTF-8")
}