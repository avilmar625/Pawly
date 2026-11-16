package com.pawly.app.care.data

import android.content.Context
import com.pawly.app.care.model.CareTask
import com.pawly.app.care.model.CareLogEntry
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
    private fun preferences(context: Context, storageScope: String?) =
        context.getSharedPreferences(storageScope?.let { "pawly_user_$it" } ?: "pawly_local", Context.MODE_PRIVATE)

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
        preferences(context, storageScope).edit()
            .putString(key("passport", petName), json.toString()).apply()
    }

    fun loadTasks(context: Context, petName: String, storageScope: String? = null): List<CareTask> = runCatching {
        val raw = preferences(context, storageScope)
            .getString(key("care", petName), "[]") ?: "[]"
        val json = JSONArray(raw)
        List(json.length()) { index ->
            val row = json.getJSONObject(index)
            CareTask(
                id = row.optString("id"),
                title = row.optString("title"),
                responsible = row.optString("responsible"),
                frequency = row.optString("frequency"),
                completed = row.optBoolean("completed"),
                dose = row.optString("dose"),
                time = row.optString("time"),
                completedOn = row.optString("completedOn"),
                action = row.optString("action", "Otro"),
                daysOfWeek = com.pawly.app.care.domain.CareTaskSchedule.normalizeDays(
                    row.optJSONArray("daysOfWeek")?.let { array ->
                        List(array.length()) { index -> array.optInt(index, 0) }
                    }.orEmpty()
                )
            )
        }
    }.getOrDefault(emptyList())

    fun saveTasks(context: Context, petName: String, tasks: List<CareTask>, storageScope: String? = null) {
        val json = JSONArray()
        tasks.forEach { task ->
            json.put(
                JSONObject().put("id", task.id).put("title", task.title)
                    .put("responsible", task.responsible).put("frequency", task.frequency)
                    .put("completed", task.completed).put("dose", task.dose)
                    .put("time", task.time).put("completedOn", task.completedOn)
                    .put("action", task.action)
                    .put("daysOfWeek", JSONArray(task.daysOfWeek))
            )
        }
        preferences(context, storageScope).edit()
            .putString(key("care", petName), json.toString()).apply()
    }

    fun loadCareLogs(context: Context, petName: String, storageScope: String? = null): List<CareLogEntry> = runCatching {
        val raw = preferences(context, storageScope).getString(key("care_logs", petName), "[]") ?: "[]"
        val json = JSONArray(raw)
        List(json.length()) { index ->
            val row = json.getJSONObject(index)
            CareLogEntry(
                id = row.optString("id"), taskTitle = row.optString("taskTitle"),
                action = row.optString("action"), actorUid = row.optString("actorUid"),
                actorName = row.optString("actorName"), occurredAt = row.optLong("occurredAt"),
                note = row.optString("note")
            )
        }.sortedByDescending { it.occurredAt }
    }.getOrDefault(emptyList())

    fun saveCareLogs(context: Context, petName: String, logs: List<CareLogEntry>, storageScope: String? = null) {
        val json = JSONArray()
        logs.take(100).forEach { log ->
            json.put(JSONObject().put("id", log.id).put("taskTitle", log.taskTitle)
                .put("action", log.action).put("actorUid", log.actorUid)
                .put("actorName", log.actorName).put("occurredAt", log.occurredAt)
                .put("note", log.note))
        }
        preferences(context, storageScope).edit().putString(key("care_logs", petName), json.toString()).apply()
    }

    /** Move legacy name-keyed local records when a profile is renamed. */
    fun renamePet(context: Context, oldName: String, newName: String, storageScope: String? = null) {
        if (oldName.trim().equals(newName.trim(), ignoreCase = true)) return
        val prefs = preferences(context, storageScope)
        val prefixes = listOf("passport", "care", "care_logs")
        val editor = prefs.edit()
        prefixes.forEach { prefix ->
            val oldKey = key(prefix, oldName)
            val newKey = key(prefix, newName)
            val oldValue = prefs.getString(oldKey, null)
            if (oldValue != null && !prefs.contains(newKey)) {
                editor.putString(newKey, oldValue).remove(oldKey)
            }
        }
        editor.apply()
    }
}
