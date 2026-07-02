package com.rvmirror.provider

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

    private fun keyFor(ott: String): String = "ids.$ott"

    private fun timestampKeyFor(ott: String, id: String): String = "seen.$ott.$id"

    private companion object {
        const val PREFERENCES_NAME = "rvmirror_id_store"
    }
}

internal data class MirrorTitleId(
    val ott: String,
    val rawId: String,
)