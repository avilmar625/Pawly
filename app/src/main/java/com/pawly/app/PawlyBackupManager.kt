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
 * Versioned, user-controlled backup. In addition to the explicit pet snapshot,
 * it carries the safe local Pawly preferences so new modules do not silently
 * lose data merely because they add a preference key. Credentials and
 * device-specific file paths are deliberately excluded.
 */
internal object PawlyBackupManager {
    private const val VERSION = 2
    private const val MIN_SUPPORTED_VERSION = 1
    private const val MAX_BACKUP_BYTES = 25 * 1024 * 1024
    private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
    private val excludedPreferenceFragments = listOf(
        "token", "password", "email", "auth", "secret", "credential", "fcm",
        "path", "filepath", "uid", "scope"
    )

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
        val preferences = context.getSharedPreferences(
            storageScope?.let { "pawly_user_$it" } ?: "pawly_local", Context.MODE_PRIVATE
        )
        val safePreferences = JSONObject()
        preferences.all.forEach { (key, value) ->
            if (excludedPreferenceFragments.any { key.lowercase().contains(it) }) return@forEach
            when (value) {
                is String -> safePreferences.put(key, value)
                is Boolean, is Int, is Long, is Float -> safePreferences.put(key, value)
                is Set<*> -> safePreferences.put(key, JSONArray(value.filterIsInstance<String>()))
            }
        }
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
            .put("appPreferences", safePreferences)
            .put("passportEntries", JSONArray().apply {
                passport.forEach { entry ->
                    put(JSONObject()
                        .put("id", entry.id).put("type", entry.type).put("title", entry.title)
                        .put("detail", entry.detail).put("date", entry.date)
                        .put("product", entry.product).put("dose", entry.dose)
                        .put("frequency", entry.frequency).put("clinic", entry.clinic)
                        .put("nextDoseDate", entry.nextDoseDate).put("status", entry.status)
                        .put("reminderEnabled", entry.reminderEnabled).put("reminderTime", entry.reminderTime))
                }
            })
            .put("careTasks", JSONArray().apply {
                tasks.forEach { task ->
                    put(JSONObject().put("id", task.id).put("title", task.title)
                        .put("responsible", task.responsible).put("frequency", task.frequency)
                        .put("completed", task.completed).put("dose", task.dose).put("time", task.time)
                        .put("completedOn", task.completedOn).put("action", task.action)
                        .put("daysOfWeek", JSONArray(task.daysOfWeek)))
                }
            })
            .put("careLogs", JSONArray().apply {
                logs.forEach { log ->
                    put(JSONObject().put("id", log.id).put("taskTitle", log.taskTitle)
                        .put("action", log.action).put("actorUid", log.actorUid).put("actorName", log.actorName)
                        .put("occurredAt", log.occurredAt).put("note", log.note)
                        .put("distanceKm", log.distanceKm).put("mapUrl", log.mapUrl)
                        .put("description", log.description).put("walker", log.walker)
                        .put("photoUrls", JSONArray(log.photoUrls)))
                }
            })
        val photo = photoPath?.let { runCatching { File(it).takeIf(File::isFile)?.readBytes() }.getOrNull() }
            ?.takeIf { it.size <= MAX_PHOTO_BYTES }
        if (photo != null) root.put("photoBase64", Base64.encodeToString(photo, Base64.NO_WRAP))
        val output = root.toString()
        require(output.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "La copia supera el tamaño máximo de 25 MB."
        }
        return output
    }

    fun restore(context: Context, raw: String, storageScope: String?): PawlyBackupSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "El archivo supera el tamaño máximo de 25 MB."
        }
        val root = JSONObject(raw)
        val version = root.optInt("version", -1)
        require(root.optString("format") == "pawly-backup" && version in MIN_SUPPORTED_VERSION..VERSION) {
            "El archivo no es una copia de seguridad Pawly compatible."
        }
        val petName = root.optString("petName").trim()
        require(petName.isNotBlank() && petName.length <= 40) { "El nombre de la mascota no es válido." }
        val profiles = stringList(root.optJSONArray("dogProfiles")).toSet() + petName
        val diary = stringList(root.optJSONArray("diaryEntries")).take(500)
        val calendar = stringList(root.optJSONArray("calendarReminders")).take(500)
        val health = buildMap {
            val rows = root.optJSONArray("healthDates") ?: JSONArray()
            require(rows.length() <= 1000) { "La copia contiene demasiados registros de salud." }
            for (i in 0 until rows.length()) {
                val row = rows.optJSONArray(i) ?: continue
                if (row.length() >= 2) put(row.optString(0).take(120), row.optString(1).take(120))
            }
        }
        val passport = parsePassport(root.optJSONArray("passportEntries"))
        val tasks = parseTasks(root.optJSONArray("careTasks"))
        val logs = parseLogs(root.optJSONArray("careLogs"))
        val photoBytes = root.optString("photoBase64").takeIf { it.isNotBlank() }?.let {
            runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrElse {
                throw IllegalArgumentException("La fotografía de la copia está dañada.")
            }.also { bytes ->
                require(bytes.size in 1..MAX_PHOTO_BYTES) {
                    "La fotografía de la copia supera los 5 MB o está vacía."
                }
            }
        }

        // Restore preferences only after every structured section and binary asset
        // has been parsed and validated, so a corrupt file cannot leave a half-restore.
        val prefs = context.getSharedPreferences(
            storageScope?.let { "pawly_user_$it" } ?: "pawly_local", Context.MODE_PRIVATE
        )
        val values = root.optJSONObject("appPreferences")
        if (values != null) {
            require(values.length() <= 500) { "La copia contiene demasiadas preferencias." }
            val editor = prefs.edit()
            val keys = values.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (excludedPreferenceFragments.any { key.lowercase().contains(it) }) continue
                when (val value = values.opt(key)) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is JSONArray -> editor.putStringSet(
                        key, List(value.length()) { index -> value.optString(index) }.toSet()
                    )
                }
            }
            editor.apply()
        }
        PetCareLocalStore.saveEntries(context, petName, passport, storageScope)
        PetCareLocalStore.saveTasks(context, petName, tasks, storageScope)
        PetCareLocalStore.saveCareLogs(context, petName, logs, storageScope)
        return PawlyBackupSnapshot(petName, profiles, diary, calendar, health, passport, tasks, logs, photoBytes)
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        require(array.length() <= 1000) { "La copia contiene demasiados elementos." }
        return List(array.length()) { array.optString(it).take(2000) }.filter { it.isNotBlank() }
    }

    private fun parsePassport(array: JSONArray?): List<PassportEntry> {
        if (array == null) return emptyList()
        require(array.length() <= 450) { "La copia contiene demasiados registros del pasaporte." }
        return List(array.length()) { i ->
            val row = array.optJSONObject(i) ?: JSONObject()
            PassportEntry(
                id = row.optString("id"), type = row.optString("type"), title = row.optString("title"),
                detail = row.optString("detail"), date = row.optString("date"),
                product = row.optString("product"), dose = row.optString("dose"),
                frequency = row.optString("frequency"), clinic = row.optString("clinic"),
                nextDoseDate = row.optString("nextDoseDate"),
                status = row.optString("status", com.pawly.app.care.model.PassportEntryStates.REALIZADO)
                    .takeIf { it in com.pawly.app.care.model.PassportEntryStates.all }
                    ?: com.pawly.app.care.model.PassportEntryStates.REALIZADO,
                reminderEnabled = row.optBoolean("reminderEnabled", false),
                reminderTime = row.optString("reminderTime", "09:00")
                    .takeIf { it.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")) } ?: "09:00"
            )
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() }
    }

    private fun parseTasks(array: JSONArray?): List<CareTask> {
        if (array == null) return emptyList()
        require(array.length() <= 450) { "La copia contiene demasiadas rutinas." }
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
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() }
    }

    private fun parseLogs(array: JSONArray?): List<CareLogEntry> {
        if (array == null) return emptyList()
        require(array.length() <= 100) { "La copia contiene demasiados registros de actividad." }
        val allowed = setOf("Paseo", "Comida", "Medicación", "Higiene", "Otro")
        return List(array.length()) { i ->
            val row = array.optJSONObject(i) ?: JSONObject()
            val distance = if (row.isNull("distanceKm")) null else row.optDouble("distanceKm", Double.NaN)
            val photos = row.optJSONArray("photoUrls")?.let { array ->
                List(array.length()) { index -> array.optString(index) }
            }.orEmpty()
            require(distance == null || (distance.isFinite() && distance in 0.01..1000.0)) {
                "La distancia de un paseo en la copia no es válida."
            }
            require(row.optString("mapUrl").let { value ->
                value.isBlank() || (value.length <= 2048 && runCatching {
                    val parsed = android.net.Uri.parse(value)
                    parsed.scheme in setOf("https", "http") && !parsed.host.isNullOrBlank()
                }.getOrDefault(false))
            }) { "El enlace del mapa en la copia no es válido." }
            require(row.optString("description").length <= 600 && row.optString("walker").length <= 80) {
                "Los datos del paseo en la copia superan los límites permitidos."
            }
            require(photos.size <= 4 && photos.all { it.startsWith("https://") && it.length <= 2048 }) {
                "Las fotografías del paseo en la copia no son válidas."
            }
            CareLogEntry(row.optString("id"), row.optString("taskTitle"),
                row.optString("action").takeIf { it in allowed } ?: "Otro",
                row.optString("actorUid"), row.optString("actorName"),
                row.optLong("occurredAt"), row.optString("note"),
                distance, row.optString("mapUrl"), row.optString("description"),
                row.optString("walker"), photos)
        }.filter { it.id.isNotBlank() && it.taskTitle.isNotBlank() }
    }
}
