package dev.kkrow.calorietracker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CalorieRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getDailyLimit(): Int = prefs.getInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)

    fun setDailyLimit(value: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT, value).apply()
    }

    fun addEntry(dateKey: String, value: Int) {
        val entries = loadEntries()
        val dayEntries = entries[dateKey] ?: mutableListOf()
        dayEntries.add(value)
        entries[dateKey] = dayEntries
        saveEntries(entries)
    }

    fun getDayTotal(dateKey: String): Int {
        return loadEntries()[dateKey]?.sum() ?: 0
    }

    fun getDayEntries(dateKey: String): List<Int> {
        return loadEntries()[dateKey]?.toList() ?: emptyList()
    }

    fun getDatesWithRecords(): Set<String> {
        return loadEntries().keys
    }

    fun exportToJson(): String {
        val root = JSONObject()
        root.put(KEY_DAILY_LIMIT, getDailyLimit())

        val entriesJson = JSONObject()
        for ((date, values) in loadEntries()) {
            val arr = JSONArray()
            values.forEach { arr.put(it) }
            entriesJson.put(date, arr)
        }
        root.put(KEY_ENTRIES, entriesJson)
        return root.toString()
    }

    fun importFromJson(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val limit = root.optInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)
            val entriesJson = root.optJSONObject(KEY_ENTRIES) ?: JSONObject()
            val parsed = mutableMapOf<String, MutableList<Int>>()

            val keys = entriesJson.keys()
            while (keys.hasNext()) {
                val dateKey = keys.next()
                val arr = entriesJson.optJSONArray(dateKey) ?: JSONArray()
                val values = mutableListOf<Int>()
                for (i in 0 until arr.length()) {
                    values.add(arr.optInt(i, 0))
                }
                parsed[dateKey] = values
            }

            prefs.edit()
                .putInt(KEY_DAILY_LIMIT, limit)
                .putString(KEY_ENTRIES, serializeEntries(parsed))
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadEntries(): MutableMap<String, MutableList<Int>> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return mutableMapOf()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, MutableList<Int>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val dateKey = keys.next()
                val arr = root.optJSONArray(dateKey) ?: JSONArray()
                val values = mutableListOf<Int>()
                for (i in 0 until arr.length()) {
                    values.add(arr.optInt(i, 0))
                }
                result[dateKey] = values
            }
            result
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveEntries(entries: Map<String, List<Int>>) {
        prefs.edit().putString(KEY_ENTRIES, serializeEntries(entries)).apply()
    }

    private fun serializeEntries(entries: Map<String, List<Int>>): String {
        val root = JSONObject()
        for ((date, values) in entries) {
            val arr = JSONArray()
            values.forEach { arr.put(it) }
            root.put(date, arr)
        }
        return root.toString()
    }

    companion object {
        private const val PREF_NAME = "calorie_tracker_prefs"
        private const val KEY_DAILY_LIMIT = "daily_limit"
        private const val KEY_ENTRIES = "entries"
        private const val DEFAULT_DAILY_LIMIT = 2000
    }
}
