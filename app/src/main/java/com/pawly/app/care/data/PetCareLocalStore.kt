package com.pawly.app.care.data

import android.content.Context
import com.pawly.app.care.model.CareTask
import com.pawly.app.care.model.PassportEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistence adapter for pet care data.
 *
 * The v16 preference keys are intentionally preserved to avoid losing existing
 * records after updating. Room migration is a later step; keep this API stable.
 */
internal object PetCareLocalStore {
    private fun preferences(context: Context, storageScope: String?) =\n        context.getSharedPreferences(storageScope?.let { "pawly_user_$it" } ?: "pawly_local", Context.MODE_PRIVATE)

    private fun key(prefix: String, petName: String): String =
        "v16_${prefix}_${petName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")}"

    fun loadEntries(context: Context, petName: String, storageScope: String? = null): List<PassportEntry> = runCatching {
        val raw = preferences(context, storageScope)
            .getString(key("passport", petName), "[]") ?: "[]"
        val json = JSONArray(raw)
        List(json.length()) { index ->
            val row = json.getJSONObject(index)
            PassportEntry(
                id = row.optString("id"),
                type = row.optString("type"),
                title = row.optString("title"),
                detail = row.optString("detail"),
                date = row.optString("date")
            )
        }
    }.getOrDefault(emptyList())

    fun saveEntries(context: Context, petName: String, entries: List<PassportEntry>, storageScope: String? = null) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(
                JSONObject().put("id", entry.id).put("type", entry.type)
                    .put("title", entry.title).put("detail", entry.detail).put("date", entry.date)
            )
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(key("passport", petName), json.toString()).apply()
    }

    fun loadTasks(context: Context, petName: String, storageScope: String? = null): List<CareTask> = runCatching {
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(key("care", petName), "[]") ?: "[]"
        val json = JSONArray(raw)
        List(json.length()) { index ->
            val row = json.getJSONObject(index)
            CareTask(
                id = row.optString("id"),
                title = row.optString("title"),
                responsible = row.optString("responsible"),
                frequency = row.optString("frequency"),
                completed = row.optBoolean("completed")
            )
        }
    }.getOrDefault(emptyList())

    fun saveTasks(context: Context, petName: String, tasks: List<CareTask>, storageScope: String? = null) {
        val json = JSONArray()
        tasks.forEach { task ->
            json.put(
                JSONObject().put("id", task.id).put("title", task.title)
                    .put("responsible", task.responsible).put("frequency", task.frequency)
                    .put("completed", task.completed)
            )
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(key("care", petName), json.toString()).apply()
    }
}
