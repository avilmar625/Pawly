package com.pawly.app

import android.content.Context
import android.util.Base64
import com.pawly.app.care.data.PetCareLocalStore
import com.pawly.app.care.model.CareLogEntry
import com.pawly.app.care.model.CareTask
import com.pawly.app.care.model.PassportEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class PawlyBackupSnapshot(
    val petName: String,
    val dogProfiles: Set<String>,
    val diaryEntries: List<String>,
    val calendarReminders: List<String>,
    val healthDates: Map<String, String>,
    val passportEntries: List<PassportEntry>,
    val careTasks: List<CareTask>,
    val careLogs: List<CareLogEntry>,
    val photoBytes: ByteArray?
)

/**
 * Versioned, user-controlled JSON backup. It contains only the selected pet's local
 * data and can be restored without replacing the rest of the user's profiles.
 */
internal object PawlyBackupManager {
    private const val VERSION = 1
    private const val MAX_BACKUP_BYTES = 15 * 1024 * 1024
    private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024

    fun create(
        context: Context,
        petName: String,
        dogProfiles: Set<String>,
        diaryEntries: List<String>,
        calendarReminders: List<String>,
        healthDates: Map<String, String>,
        storageScope: String?,
        photoPath: String?
    ): String {
        val passport = PetCareLocalStore.loadEntries(context, petName, storageScope)
        val tasks = PetCareLocalStore.loadTasks(context, petName, storageScope)
        val logs = PetCareLocalStore.loadCareLogs(context, petName, storageScope)
        val root = JSONObject()
            .put("format", "pawly-backup")
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("petName", petName)
            .put("dogProfiles", JSONArray(dogProfiles.toList()))
            .put("diaryEntries", JSONArray(diaryEntries))
            .put("calendarReminders", JSONArray(calendarReminders))
            .put("healthDates", JSONArray().apply {
                healthDates.forEach { (key, value) -> put(JSONArray().put(key).put(value)) }
            })
            .put("passportEntries", JSONArray().apply {
                passport.forEach { put(JSONObject().put("id", it.id).put("type", it.type)
                    .put("title", it.title).put("detail", it.detail).put("date", it.date)) }
            })
            .put("careTasks", JSONArray().apply {
                tasks.forEach { put(JSONObject().put("id", it.id).put("title", it.title)
                    .put("responsible", it.responsible).put("frequency", it.frequency)
                    .put("completed", it.completed).put("dose", it.dose).put("time", it.time)
                    .put("completedOn", it.completedOn).put("action", it.action)
                    .put("daysOfWeek", JSONArray(it.daysOfWeek))) }
            })
            .put("careLogs", JSONArray().apply {
                logs.forEach { put(JSONObject().put("id", it.id).put("taskTitle", it.taskTitle)
                    .put("action", it.action).put("actorUid", it.actorUid).put("actorName", it.actorName)
                    .put("occurredAt", it.occurredAt).put("note", it.note)) }
            })
        val photo = photoPath?.let { runCatching { File(it).takeIf(File::isFile)?.readBytes() }.getOrNull() }
            ?.takeIf { it.size <= MAX_PHOTO_BYTES }
        if (photo != null) root.put("photoBase64", Base64.encodeToString(photo, Base64.NO_WRAP))
        val output = root.toString()
        require(output.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "La copia supera el tamaño máximo de 15 MB." }
        return output
    }

    fun restore(context: Context, raw: String, storageScope: String?): PawlyBackupSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "El archivo supera el tamaño máximo de 15 MB." }
        val root = JSONObject(raw)
        require(root.optString("format") == "pawly-backup" && root.optInt("version") == VERSION) {
            "El archivo no es una copia de seguridad Pawly compatible."
        }
        val petName = root.optString("petName").trim()
        require(petName.isNotBlank() && petName.length <= 40) { "El nombre de la mascota no es válido." }
        val profiles = stringList(root.optJSONArray("dogProfiles")).toSet() + petName
        val diary = stringList(root.optJSONArray("diaryEntries")).take(500)
        val calendar = stringList(root.optJSONArray("calendarReminders")).take(500)
        val health = buildMap {
            val rows = root.optJSONArray("healthDates") ?: JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONArray(i) ?: continue
                if (row.length() >= 2) put(row.optString(0), row.optString(1))
            }
        }
        val passport = parsePassport(root.optJSONArray("passportEntries"))
        val tasks = parseTasks(root.optJSONArray("careTasks"))
        val logs = parseLogs(root.optJSONArray("careLogs"))
        PetCareLocalStore.saveEntries(context, petName, passport, storageScope)
        PetCareLocalStore.saveTasks(context, petName, tasks, storageScope)
        PetCareLocalStore.saveCareLogs(context, petName, logs, storageScope)
        val photoBytes = root.optString("photoBase64").takeIf { it.isNotBlank() }?.let {
            runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()
        }?.takeIf { it.size <= MAX_PHOTO_BYTES }
        return PawlyBackupSnapshot(petName, profiles, diary, calendar, health, passport, tasks, logs, photoBytes)
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
    }

    private fun parsePassport(array: JSONArray?): List<PassportEntry> {
        if (array == null) return emptyList()
        return List(array.length()) { i ->
            val row = array.optJSONObject(i) ?: JSONObject()
            PassportEntry(row.optString("id"), row.optString("type"), row.optString("title"),
                row.optString("detail"), row.optString("date"))
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() }.take(450)
    }

    private fun parseTasks(array: JSONArray?): List<CareTask> {
        if (array == null) return emptyList()
        val allowed = setOf("Paseo", "Comida", "Medicación", "Higiene", "Otro")
        return List(array.length()) { i ->
            val row = array.optJSONObject(i) ?: JSONObject()
            CareTask(row.optString("id"), row.optString("title"), row.optString("responsible"),
                row.optString("frequency"), row.optBoolean("completed"), row.optString("dose"),
                row.optString("time"), row.optString("completedOn"),
                row.optString("action").takeIf { it in allowed } ?: "Otro",
                daysOfWeek = com.pawly.app.care.domain.CareTaskSchedule.normalizeDays(
                    row.optJSONArray("daysOfWeek")?.let { days ->
                        List(days.length()) { index -> days.optInt(index, 0) }
                    }.orEmpty()
                ))
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() }.take(450)
    }

    private fun parseLogs(array: JSONArray?): List<CareLogEntry> {
        if (array == null) return emptyList()
        val allowed = setOf("Paseo", "Comida", "Medicación", "Higiene", "Otro")
        return List(array.length()) { i ->
            val row = array.optJSONObject(i) ?: JSONObject()
            CareLogEntry(row.optString("id"), row.optString("taskTitle"),
                row.optString("action").takeIf { it in allowed } ?: "Otro",
                row.optString("actorUid"), row.optString("actorName"),
                row.optLong("occurredAt"), row.optString("note"))
        }.filter { it.id.isNotBlank() && it.taskTitle.isNotBlank() }.take(100)
    }
}
