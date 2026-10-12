package com.textfilereader

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores recent file history (URI + display name + timestamp).
 * Uses SharedPreferences + JSON. Keeps the most recent [MAX_ITEMS] entries.
 */
class HistoryManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class HistoryItem(
        val uriString: String,
        val displayName: String,
        val openedAt: Long
    ) {
        val uri: Uri get() = Uri.parse(uriString)
    }

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val uri = obj.optString("uri", "")
                val name = obj.optString("name", "Unknown")
                val time = obj.optLong("time", 0L)
                if (uri.isBlank()) null else HistoryItem(uri, name, time)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(uri: Uri, displayName: String) {
        val uriString = uri.toString()
        val items = getHistory().toMutableList()
        // Remove existing entry with same URI (move to top)
        items.removeAll { it.uriString == uriString }
        items.add(0, HistoryItem(uriString, displayName, System.currentTimeMillis()))
        // Cap size
        while (items.size > MAX_ITEMS) {
            items.removeAt(items.lastIndex)
        }
        save(items)
    }

    fun remove(uriString: String) {
        val items = getHistory().filter { it.uriString != uriString }
        save(items)
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun save(items: List<HistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("uri", item.uriString)
                put("name", item.displayName)
                put("time", item.openedAt)
            })
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "text_file_reader_history"
        private const val KEY_HISTORY = "history"
        const val MAX_ITEMS = 20
    }
}
