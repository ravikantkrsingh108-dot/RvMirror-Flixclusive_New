package com.rvmirror.provider

import android.content.Context

internal class MirrorIdStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(ott: String): List<String> =
        preferences.getStringSet(keyFor(ott), emptySet())
            .orEmpty()
            .filter { it.isNotBlank() }
            .sortedByDescending { preferences.getLong(timestampKeyFor(ott, it), 0L) }

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