package com.v16studio.serviceloop.ui

import android.content.Context
import org.json.JSONArray

/**
 * Presentation-only Search memory. It is deliberately outside Room, backup, export,
 * Dispatch packages, and customer-facing reports.
 */
internal class SearchUiPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isCategoryExpanded(key: String, default: Boolean = true): Boolean =
        if (preferences.contains(categoryKey(key))) preferences.getBoolean(categoryKey(key), default) else default

    fun setCategoryExpanded(key: String, expanded: Boolean) {
        preferences.edit().putBoolean(categoryKey(key), expanded).apply()
    }

    fun recentQueries(): List<String> = runCatching {
        val values = JSONArray(preferences.getString(RECENT_KEY, "[]"))
        buildList {
            for (index in 0 until values.length()) {
                values.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }.distinctBy(String::lowercase).take(MAX_RECENT_QUERIES)
    }.getOrDefault(emptyList())

    fun recordQuery(query: String): List<String> {
        val normalized = normalizeQuery(query) ?: return recentQueries()
        val updated = buildList {
            add(normalized)
            addAll(recentQueries().filterNot { it.equals(normalized, ignoreCase = true) })
        }.take(MAX_RECENT_QUERIES)
        preferences.edit().putString(RECENT_KEY, JSONArray(updated).toString()).apply()
        return updated
    }

    fun clearRecentQueries() {
        preferences.edit().remove(RECENT_KEY).apply()
    }

    private fun categoryKey(key: String): String = "category.${key.trim().uppercase()}"

    companion object {
        const val FILE_NAME = "serviceloop_search_ui_preferences"
        private const val RECENT_KEY = "recentQueries"
        const val MAX_RECENT_QUERIES = 10

        fun normalizeQuery(query: String): String? = query.trim().replace(Regex("\\s+"), " ").takeIf(String::isNotEmpty)
    }
}
