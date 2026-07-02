package com.flixclusive.provider.rvmirror

import android.content.Context

internal class MirrorIdStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun count(ott: String): Int = load(ott).size

    fun totalCount(): Int = Mirror.PLATFORMS.sumOf { count(it.ott) }

    fun load(ott: String): List<String> =
        preferences.getStringSet(keyFor(ott), emptySet())
            .orEmpty()
            .filter { it.isNotBlank() }
            .sortedByDescending { preferences.getLong(timestampKeyFor(ott, it), 0L) }

    fun loadAll(): List<MirrorTitleId> =
        Mirror.PLATFORMS.flatMap { platform ->
            load(platform.ott).map { rawId -> MirrorTitleId(platform.ott, rawId) }
        }

    fun loadByYear(year: Int): List<MirrorTitleId> =
        loadAll().filter { titleId -> metadata(titleId)?.year == year }

    fun loadByGenre(genre: String): List<MirrorTitleId> =
        loadAll().filter { titleId ->
            metadata(titleId)?.genres.orEmpty().any { it.equals(genre, ignoreCase = true) }
        }

    fun loadByType(isMovie: Boolean): List<MirrorTitleId> =
        loadAll().filter { titleId -> metadata(titleId)?.isMovie == isMovie }

    fun years(): List<Int> =
        loadAll().mapNotNull { metadata(it)?.year }.distinct().sortedDescending()

    fun genres(): List<String> =
        loadAll()
            .flatMap { metadata(it)?.genres.orEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()

    fun countByYear(year: Int): Int = loadByYear(year).size

    fun countByGenre(genre: String): Int = loadByGenre(genre).size

    fun countByType(isMovie: Boolean): Int = loadByType(isMovie).size

    fun rememberSeen(ott: String, ids: Iterable<String>) {
        val now = System.currentTimeMillis()
        val cleanIds = ids.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        if (cleanIds.isEmpty()) return

        val editor = preferences.edit()
        val merged = LinkedHashSet(load(ott))
        cleanIds.forEach { id ->
            merged.add(id)
            editor.putLong(timestampKeyFor(ott, id), now)
        }
        editor.putStringSet(keyFor(ott), merged).apply()
    }

    fun rememberDetails(ott: String, rawId: String, details: PostDetails) {
        preferences.edit()
            .putString(metadataKeyFor(ott, rawId), StoredMetadata(details.year, details.genres, details.isMovie).encode())
            .apply()
    }

    private fun metadata(titleId: MirrorTitleId): StoredMetadata? =
        preferences.getString(metadataKeyFor(titleId.ott, titleId.rawId), null)?.let(StoredMetadata::decode)

    private fun keyFor(ott: String): String = "ids.$ott"

    private fun timestampKeyFor(ott: String, id: String): String = "seen.$ott.$id"

    private fun metadataKeyFor(ott: String, id: String): String = "meta.$ott.$id"

    private companion object {
        const val PREFERENCES_NAME = "rvmirror_id_store"
    }
}

internal data class MirrorTitleId(
    val ott: String,
    val rawId: String,
)

private data class StoredMetadata(
    val year: Int?,
    val genres: List<String>,
    val isMovie: Boolean?,
) {
    fun encode(): String = "${year ?: ""}\t${genres.joinToString("|")}\t${isMovie ?: ""}"

    companion object {
        fun decode(value: String): StoredMetadata {
            val parts = value.split('\t', limit = 3)
            val year = parts.getOrNull(0)?.toIntOrNull()
            val genres = parts.getOrNull(1)
                ?.split('|')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val isMovie = parts.getOrNull(2)?.toBooleanStrictOrNull()
            return StoredMetadata(year = year, genres = genres, isMovie = isMovie)
        }
    }
}