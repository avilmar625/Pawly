package com.pawly.app

import com.pawly.app.care.data.PetShareInvitation
import com.pawly.app.care.data.PetCareLocalStore
import com.pawly.app.care.data.FirebasePetCareRepository
import com.pawly.app.care.data.FirebasePetProfileRepository
import com.pawly.app.care.data.FirebasePetAgendaRepository
import com.pawly.app.care.export.PetCarePdfExporter
import com.google.firebase.auth.FirebaseAuth
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.DatePickerDialog
import java.io.File
import java.util.Calendar
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Looper
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


private const val PAWLY_CARE_CHANNEL_ID = "pawly_care_reminders"
private const val PAWLY_TEST_CHANNEL_ID = "pawly_test_notifications"

/** Creates the reminders channel and checks the real app + channel permission state. */
private fun arePawlyNotificationsEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                PAWLY_CARE_CHANNEL_ID,
                "Recordatorios de Pawly",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Avisos locales de cuidados que has programado" }
        )
        if (manager.getNotificationChannel(PAWLY_CARE_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
    }
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun ensurePawlyTestNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                PAWLY_TEST_CHANNEL_ID,
                "Prueba de notificaciones",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Avisos de prueba que envías para comprobar las notificaciones de Pawly" }
        )
    }
}

private fun arePawlyTestNotificationsEnabled(context: Context): Boolean {
    ensurePawlyTestNotificationChannel(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(PAWLY_TEST_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
    }
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openPawlyNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

internal enum class Tab(val label: String) {
    HOME("Hoy"), EXPLORE("Explorar"), LEARN("Aprender"), HEALTH("Salud"), DIARY("Diario"), CALENDAR("Agenda"), COMMUNITY("Comunidad"), MORE("Más"), SERVICES("Profesionales"), LOST("He perdido mi mascota"), MARKET("Pawly Market")
}

@Composable
internal fun PawlyApp(
    darkMode: Boolean = false,
    accent: String = "forest",
    designStyle: String = "simple",
    onDesignStyleChange: (String) -> Unit = {},
    onDarkModeChange: (Boolean) -> Unit = {},
    onAccentChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember(context) { FirebaseCommunityRepository.create(context) }
    var localMode by remember { mutableStateOf(false) }
    var signedIn by remember(repository) { mutableStateOf(repository?.currentUid != null) }
    var authFlowInProgress by remember(repository) { mutableStateOf(false) }
    val currentAuthFlowInProgress by rememberUpdatedState(authFlowInProgress)

    if (localMode) {
        PawlyMainApp(
            darkMode = darkMode,
            accent = accent,
            designStyle = designStyle,
            onDesignStyleChange = onDesignStyleChange,
            onDarkModeChange = onDarkModeChange,
            onAccentChange = onAccentChange
        )
        return
    }

    if (repository == null) {
        FirebaseSetupPanel(onContinueOffline = { localMode = true })
        return
    }

    DisposableEffect(repository) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (!currentAuthFlowInProgress) signedIn = firebaseAuth.currentUser != null
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    if (signedIn) {
        PawlyMainApp(
            storageScope = repository.currentUid,
            cloudRepository = repository,
            darkMode = darkMode,
            accent = accent,
            designStyle = designStyle,
            onDesignStyleChange = onDesignStyleChange,
            onDarkModeChange = onDarkModeChange,
            onAccentChange = onAccentChange
        )
    } else {
        CommunityAuthPanel(
            repo = repository,
            onAuthenticated = { authFlowInProgress = false; signedIn = repository.currentUid != null },
            onContinueOffline = { localMode = true },
            onAuthFlowChanged = { authFlowInProgress = it }
        )
    }
}

@Composable
private fun PawlyMainApp(
    storageScope: String? = null,
    cloudRepository: FirebaseCommunityRepository? = null,
    darkMode: Boolean = false,
    accent: String = "forest",
    designStyle: String = "simple",
    onDesignStyleChange: (String) -> Unit = {},
    onDarkModeChange: (Boolean) -> Unit = {},
    onAccentChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = remember(context, storageScope) { context.getSharedPreferences(storageScope?.let { "pawly_user_$it" } ?: "pawly_local", android.content.Context.MODE_PRIVATE) }
    var language by remember(preferences) { mutableStateOf(preferences.getString("language", "es") ?: "es") }
    var profileSyncStatus by remember(preferences, storageScope) { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var pendingPdfFile by remember { mutableStateOf<File?>(null) }
    val pdfSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val source = pendingPdfFile
        if (uri != null && source != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("No se pudo abrir el destino para guardar el PDF.")
            }.onSuccess {
                    profileSyncStatus = "Ficha PDF guardada en el dispositivo."
                    android.widget.Toast.makeText(context, PawlyI18n.text(profileSyncStatus, language), android.widget.Toast.LENGTH_LONG).show()
                }
                .onFailure {
                    profileSyncStatus = "No se pudo guardar el PDF: " + (it.localizedMessage ?: "error desconocido")
                    android.widget.Toast.makeText(context, profileSyncStatus, android.widget.Toast.LENGTH_LONG).show()
                }
        }
        pendingPdfFile = null
    }
    var selected by remember { mutableStateOf(Tab.HOME) }
    var previousTab by remember { mutableStateOf(Tab.HOME) }
    LaunchedEffect(selected) { if (selected != Tab.DIARY) previousTab = selected }
    BackHandler(enabled = selected == Tab.DIARY) { selected = previousTab }
    var openCommunityNotifications by remember { mutableStateOf(false) }
    var openCommunityProfile by remember { mutableStateOf(false) }
    var pendingCareAction by remember { mutableStateOf<String?>(null) }
    var pendingPassportType by remember { mutableStateOf<String?>(null) }
    var pendingCareSection by remember { mutableStateOf<String?>(null) }
    var openDiaryPhotoPicker by remember { mutableStateOf(false) }
    var selectedSharedPet by remember { mutableStateOf<PetShareInvitation?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val elegantDesign = designStyle == "elegant"
    var showProfileEditor by remember { mutableStateOf(false) }
    var profileDraft by remember { mutableStateOf("") }
    var dogName by remember(preferences) { mutableStateOf(preferences.getString("dog_name", "Nala") ?: "Nala") }
    var dogProfiles by remember(preferences) { mutableStateOf(preferences.getStringSet("dog_profiles", setOf(dogName))?.toSet() ?: setOf(dogName)) }
    var homePetCloudReady by remember(preferences, storageScope) { mutableStateOf(false) }
    val dogPhotoPreferenceKey = remember(dogName) {
        com.pawly.app.care.domain.PetRoutinePreferenceKeys.photoPath(
            com.pawly.app.care.domain.PetRoutinePreferenceKeys.scope(dogName)
        )
    }
    var dogPhotoPath by remember(preferences, dogPhotoPreferenceKey) {
        mutableStateOf(preferences.getString(dogPhotoPreferenceKey, null))
    }
    var photoPreferenceReady by remember(preferences, dogPhotoPreferenceKey) { mutableStateOf(false) }
    var dogPhotoUrl by remember(preferences, dogPhotoPreferenceKey, storageScope) { mutableStateOf<String?>(null) }
    LaunchedEffect(preferences, dogPhotoPreferenceKey) {
        if (!preferences.getBoolean("dog_photo_legacy_migrated", false)) {
            val legacyPath = preferences.getString("dog_photo_path", null)
            if (legacyPath != null && !preferences.contains(dogPhotoPreferenceKey)) {
                preferences.edit().putString(dogPhotoPreferenceKey, legacyPath).apply()
            }
            preferences.edit().putBoolean("dog_photo_legacy_migrated", true).apply()
        }
        dogPhotoPath = preferences.getString(dogPhotoPreferenceKey, null)
        photoPreferenceReady = true
    }
    LaunchedEffect(preferences, dogPhotoPreferenceKey, dogPhotoPath, photoPreferenceReady) {
        if (photoPreferenceReady) {
            preferences.edit().putString(dogPhotoPreferenceKey, dogPhotoPath).apply()
        }
    }
    LaunchedEffect(dogName, storageScope) {
        dogPhotoUrl = if (storageScope.isNullOrBlank()) null else runCatching {
            FirebasePetProfileRepository(ownerUid = storageScope).loadProfile(dogName)?.photoUrl
        }.getOrNull()
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val destination = File(context.filesDir, "dog-profile-${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            dogPhotoPath = destination.absolutePath
        }
    }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) runCatching {
            val destination = File(context.filesDir, "dog-profile-${System.currentTimeMillis()}.jpg")
            destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            dogPhotoPath = destination.absolutePath
        }
    }
    var notificationsEnabled by remember(context) {
        mutableStateOf(arePawlyNotificationsEnabled(context))
    }
    var notificationStatus by remember { mutableStateOf("") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsEnabled = granted && arePawlyNotificationsEnabled(context)
        notificationStatus = if (notificationsEnabled) {
            "Permiso concedido. Pawly ya puede mostrar los recordatorios que programes."
        } else {
            "Android no ha concedido el permiso. Pulsa «Revisar ajustes» para permitir las notificaciones."
        }
        profileSyncStatus = notificationStatus
    }
    val dogPhoto = remember(dogPhotoPath) { dogPhotoPath?.let(BitmapFactory::decodeFile) }
    var showOnboarding by remember(preferences) { mutableStateOf(!preferences.getBoolean("onboarding_completed", false)) }
    // Daily completion is scoped to the selected pet. Migrate the legacy
    // account-wide flags once, assigning them to the pet selected at upgrade.
    val petRoutineScope = remember(dogName) {
        com.pawly.app.care.domain.PetRoutinePreferenceKeys.scope(dogName)
    }
    val routineDayKey = com.pawly.app.care.domain.PetRoutinePreferenceKeys.day(petRoutineScope)
    val walkDoneKey = com.pawly.app.care.domain.PetRoutinePreferenceKeys.walkDone(petRoutineScope)
    val medicineDoneKey = com.pawly.app.care.domain.PetRoutinePreferenceKeys.medicineDone(petRoutineScope)
    val todayRoutineKey = java.time.LocalDate.now().toString()
    val legacyRoutineNotMigrated = !preferences.getBoolean("routine_legacy_migrated", false)
    val savedRoutineDay = remember(preferences, petRoutineScope) {
        preferences.getString(routineDayKey, null)
            ?: if (legacyRoutineNotMigrated) preferences.getString("routine_day", "") ?: "" else ""
    }
    var walkDone by remember(preferences, petRoutineScope) {
        mutableStateOf(savedRoutineDay == todayRoutineKey && preferences.getBoolean(
            walkDoneKey, if (legacyRoutineNotMigrated) preferences.getBoolean("walk_done", false) else false
        ))
    }
    var medicineDone by remember(preferences, petRoutineScope) {
        mutableStateOf(savedRoutineDay == todayRoutineKey && preferences.getBoolean(
            medicineDoneKey, if (legacyRoutineNotMigrated) preferences.getBoolean("medicine_done", false) else false
        ))
    }
    LaunchedEffect(preferences, petRoutineScope, todayRoutineKey) {
        if (!preferences.getBoolean("routine_legacy_migrated", false)) {
            preferences.edit()
                .putString(routineDayKey, preferences.getString("routine_day", "") ?: "")
                .putBoolean(walkDoneKey, preferences.getBoolean("walk_done", false))
                .putBoolean(medicineDoneKey, preferences.getBoolean("medicine_done", false))
                .putBoolean("routine_legacy_migrated", true)
                .apply()
        }
        if ((preferences.getString(routineDayKey, "") ?: "") != todayRoutineKey) {
            walkDone = false
            medicineDone = false
            preferences.edit()
                .putString(routineDayKey, todayRoutineKey)
                .putBoolean(walkDoneKey, false)
                .putBoolean(medicineDoneKey, false)
                .apply()
        }
    }
    var healthDates by remember(preferences) {
        mutableStateOf(runCatching {
            val json = JSONArray(preferences.getString("health_dates", "[]") ?: "[]")
            buildMap { for (i in 0 until json.length()) { val row = json.getJSONArray(i); put(row.getString(0), row.getString(1)) } }
        }.getOrDefault(emptyMap()))
    }
    var savedPlaceNames by remember(preferences) {
        mutableStateOf(preferences.getStringSet("saved_places", emptySet())?.toSet() ?: emptySet())
    }
    val storedEntries = remember(preferences) {
        runCatching {
            val json = JSONArray(preferences.getString("diary_entries", "[]") ?: "[]")
            List(json.length()) { index -> json.getString(index) }
        }.getOrDefault(emptyList())
    }
    var diaryEntries by remember(preferences) { mutableStateOf(storedEntries) }
    val storedCalendarReminders = remember(preferences) {
        runCatching {
            val json = JSONArray(preferences.getString("calendar_reminders", "[]") ?: "[]")
            List(json.length()) { index -> json.getString(index) }
        }.getOrDefault(emptyList())
    }
    var calendarReminders by remember(preferences) { mutableStateOf(storedCalendarReminders) }
    var pendingBackupJson by remember { mutableStateOf("") }
    val backupSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(pendingBackupJson.toByteArray(Charsets.UTF_8))
            } ?: error("No se pudo abrir el destino de la copia.")
        }.onSuccess {
            profileSyncStatus = "Copia de seguridad guardada correctamente."
        }.onFailure {
            profileSyncStatus = "No se pudo guardar la copia: ${it.localizedMessage ?: "error"}"
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) coroutineScope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("No se pudo leer el archivo.")
                }
                PawlyBackupManager.restore(context, raw, storageScope)
            }.onSuccess { snapshot ->
                val previousPet = dogName
                dogName = snapshot.petName
                dogProfiles = snapshot.dogProfiles
                diaryEntries = snapshot.diaryEntries
                calendarReminders = snapshot.calendarReminders
                val retainedDates = healthDates.filterKeys { !it.startsWith("$previousPet|") }
                healthDates = retainedDates + snapshot.healthDates.mapKeys { (key, _) -> "${snapshot.petName}|$key" }
                walkDone = false
                medicineDone = false
                homePetCloudReady = true
                snapshot.photoBytes?.let { bytes ->
                    runCatching {
                        val destination = File(context.filesDir, "dog-profile-restored-${System.currentTimeMillis()}.jpg")
                        destination.writeBytes(bytes)
                        dogPhotoPath = destination.absolutePath
                        val restoredPhotoKey = com.pawly.app.care.domain.PetRoutinePreferenceKeys.photoPath(
                            com.pawly.app.care.domain.PetRoutinePreferenceKeys.scope(snapshot.petName)
                        )
                        preferences.edit().putString(restoredPhotoKey, destination.absolutePath).apply()
                    }
                }
                preferences.edit()
                    .putString("dog_name", snapshot.petName)
                    .putStringSet("dog_profiles", snapshot.dogProfiles)
                    .putString("diary_entries", JSONArray(snapshot.diaryEntries).toString())
                    .putString("calendar_reminders", JSONArray(snapshot.calendarReminders).toString())
                    .putString("health_dates", JSONArray().apply {
                        healthDates.forEach { (key, value) -> put(JSONArray().put(key).put(value)) }
                    }.toString())
                    .putString("routine_day", todayRoutineKey)
                    .putBoolean("walk_done", false)
                    .putBoolean("medicine_done", false)
                    .apply()
                profileSyncStatus = "Copia restaurada localmente. Sincronizando los datos compatibles…"
                runCatching {
                    if (cloudRepository != null && !storageScope.isNullOrBlank()) {
                        cloudRepository.saveHomePetState(snapshot.petName, snapshot.dogProfiles)
                        val careRepository = FirebasePetCareRepository(sharedPetOwnerUid = storageScope)
                        careRepository.ensureSharedPet(snapshot.petName, snapshot.petName)
                        careRepository.savePassportEntries(snapshot.petName, snapshot.passportEntries)
                        careRepository.saveCareTasks(snapshot.petName, snapshot.careTasks)
                        FirebasePetProfileRepository(ownerUid = storageScope.orEmpty()).mergeDiary(snapshot.petName, snapshot.diaryEntries)
                        FirebasePetAgendaRepository(ownerUid = storageScope.orEmpty()).mergeReminders(snapshot.petName, snapshot.calendarReminders)
                    }
                }.onSuccess {
                    profileSyncStatus = "Copia restaurada. Pasaporte, rutinas y diario sincronizados; el historial importado de paseos se conserva en este dispositivo."
                }.onFailure {
                    profileSyncStatus = "Copia restaurada localmente; sincronización pendiente: ${it.localizedMessage ?: "sin conexión"}"
                }
            }.onFailure {
                profileSyncStatus = "No se pudo restaurar la copia: ${it.localizedMessage ?: "archivo no válido"}"
            }
        }
    }

    LaunchedEffect(preferences, dogName) { preferences.edit().putString("dog_name", dogName).apply() }
    LaunchedEffect(preferences, dogProfiles) { preferences.edit().putStringSet("dog_profiles", dogProfiles.toSet()).apply() }
    LaunchedEffect(preferences, storageScope, cloudRepository) {
        val cloud = cloudRepository
        if (cloud == null || storageScope.isNullOrBlank()) return@LaunchedEffect

        val localName = preferences.getString("dog_name", "Nala")?.trim()?.takeIf { it.isNotBlank() } ?: "Nala"
        val localProfiles = (preferences.getStringSet("dog_profiles", setOf(localName))?.toSet().orEmpty() + localName)
            .map { it.trim() }.filter { it.isNotBlank() }.toSet().ifEmpty { setOf(localName) }
        val remoteResult = runCatching { cloud.loadHomePetState() }
        if (remoteResult.isFailure) {
            profileSyncStatus = "No se pudo cargar el perfil de mascota. Revisa la conexión y las reglas de Firestore."
            return@LaunchedEffect
        }

        val remote = remoteResult.getOrNull()
        if (remote != null) {
            dogName = remote.first
            dogProfiles = remote.second
            homePetCloudReady = true
        } else if (localName != "Nala" || localProfiles.any { it != "Nala" }) {
            // Migrate an existing custom name from this device without letting a new
            // install's default "Nala" overwrite an older device's saved profile.
            runCatching { cloud.saveHomePetState(localName, localProfiles) }
                .onSuccess {
                    dogName = localName
                    dogProfiles = localProfiles
                    homePetCloudReady = true
                    profileSyncStatus = "Perfil de mascota guardado en tu cuenta."
                }
                .onFailure {
                    profileSyncStatus = "No se pudo sincronizar: ${it.localizedMessage ?: "revisa las reglas de Firestore"}"
                }
        }
    }
    LaunchedEffect(cloudRepository, storageScope, homePetCloudReady, dogName, dogProfiles) {
        val cloud = cloudRepository
        if (!homePetCloudReady || cloud == null || storageScope.isNullOrBlank()) return@LaunchedEffect
        profileSyncStatus = "Sincronizando perfil de mascota…"
        runCatching { cloud.saveHomePetState(dogName, dogProfiles) }
            .onSuccess { profileSyncStatus = "Perfil de mascota sincronizado con tu cuenta." }
            .onFailure {
                profileSyncStatus = "No se pudo sincronizar: ${it.localizedMessage ?: "revisa las reglas de Firestore"}"
            }
    }
    // Photo state is persisted under the selected pet key above; never share one photo across profiles.
    LaunchedEffect(preferences, petRoutineScope, walkDone) { preferences.edit().putBoolean(walkDoneKey, walkDone).apply() }
    LaunchedEffect(preferences, petRoutineScope, medicineDone) { preferences.edit().putBoolean(medicineDoneKey, medicineDone).apply() }
    LaunchedEffect(preferences, healthDates) {
        val json = JSONArray()
        healthDates.forEach { (key, value) -> json.put(org.json.JSONArray().put(key).put(value)) }
        preferences.edit().putString("health_dates", json.toString()).apply()
    }
    LaunchedEffect(preferences, savedPlaceNames) {
        preferences.edit().putStringSet("saved_places", savedPlaceNames.toSet()).apply()
    }
    LaunchedEffect(preferences, diaryEntries) {
        val json = JSONArray()
        diaryEntries.forEach { entry -> json.put(entry) }
        preferences.edit().putString("diary_entries", json.toString()).apply()
    }
    LaunchedEffect(preferences, calendarReminders) {
        val json = JSONArray()
        calendarReminders.forEach { json.put(it) }
        preferences.edit().putString("calendar_reminders", json.toString()).apply()
    }

    CompositionLocalProvider(LocalPawlyLanguage provides language) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
                val petTabs = listOf(Tab.HOME, Tab.HEALTH, Tab.DIARY, Tab.CALENDAR)
                val moreTabs = listOf(Tab.MORE, Tab.LEARN, Tab.SERVICES)
                val routeTabs = when (selected) {
                    Tab.HOME, Tab.HEALTH, Tab.DIARY, Tab.CALENDAR -> petTabs
                    Tab.MORE, Tab.LEARN, Tab.SERVICES, Tab.LOST, Tab.MARKET -> moreTabs
                    else -> emptyList()
                }
                if (routeTabs.isNotEmpty()) {
                    val selectedRoute = if (selected == Tab.MARKET || selected == Tab.LOST) Tab.MORE else selected
                    PawlySectionNavigationHeader(
                        title = PawlyI18n.text(if (selectedRoute in petTabs) "Mi perro" else "TU ESPACIO PAWLY", language),
                        labels = routeTabs.map { PawlyI18n.text(it.label, language) },
                        selectedIndex = routeTabs.indexOf(selectedRoute).coerceAtLeast(0),
                        onSelect = { index ->
                            val tab = routeTabs[index]
                            selected = tab
                            if (tab != Tab.COMMUNITY) openCommunityNotifications = false
                        }
                    )
                }
        },
        bottomBar = {
            val navigationTabs = listOf(Tab.HOME, Tab.COMMUNITY, Tab.EXPLORE, Tab.MORE)
            val selectedSection = when (selected) {
                Tab.HEALTH, Tab.DIARY, Tab.CALENDAR -> Tab.HOME
                Tab.LEARN, Tab.SERVICES, Tab.LOST, Tab.MARKET -> Tab.MORE
                else -> selected
            }
            fun iconForTab(tab: Tab) = when (tab) {
                Tab.HOME -> Icons.Filled.Pets
                Tab.COMMUNITY -> Icons.Filled.Groups
                Tab.EXPLORE -> Icons.Filled.Explore
                Tab.MORE -> Icons.Filled.MoreHoriz
                else -> Icons.Filled.Pets
            }
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                navigationTabs.forEach { tab ->
                    val label = if (tab == Tab.HOME) "Mi perro" else tab.label
                    NavigationBarItem(
                        selected = selectedSection == tab,
                        onClick = {
                            selected = tab
                            if (tab != Tab.COMMUNITY) openCommunityNotifications = false
                        },
                        icon = { Icon(iconForTab(tab), contentDescription = null) },
                        label = { Text(PawlyI18n.text(label, language), maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // On tablets and foldables, keep reading columns comfortably narrow and centered.
            // Compact phones retain the full available width without horizontal scrolling.
            val contentWidth = if (maxWidth >= 840.dp) Modifier.widthIn(max = 840.dp).align(Alignment.TopCenter) else Modifier.fillMaxWidth()
            Box(contentWidth.fillMaxHeight()) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 120))
                },
                label = "pawly-section-transition"
            ) { destination ->
            when (destination) {
                Tab.HOME -> HomeScreen(
                    dogName = dogName,
                    walkDone = walkDone,
                    medicineDone = medicineDone,
                    onWalkToggle = { walkDone = it },
                    onMedicineToggle = { medicineDone = it },
                    onExplore = { selected = Tab.EXPLORE },
                    onLearn = { selected = Tab.LEARN },
                    onAbout = { showAbout = true },
                    onSettings = { showSettings = true },
                    onEditProfile = { profileDraft = dogName; showProfileEditor = true },
                    dogPhoto = dogPhoto,
                    dogPhotoUrl = dogPhotoUrl,
                    elegantDesign = elegantDesign,
                    onNotifications = { openCommunityNotifications = true; selected = Tab.COMMUNITY },
                    onDiary = { selected = Tab.DIARY },
                    onCalendar = { selected = Tab.CALENDAR },
                    onCommunity = { selected = Tab.COMMUNITY },
                    onHealth = { selected = Tab.HEALTH },
                    onWeight = { pendingPassportType = "Peso"; selected = Tab.HEALTH },
                    onPhoto = { openDiaryPhotoPicker = true; selected = Tab.DIARY },
                    onFood = { pendingCareAction = "Comida"; selected = Tab.HEALTH },
                    healthDates = healthDates.filterKeys { it.startsWith("$dogName|") }
                        .mapKeys { (key, _) -> key.removePrefix("$dogName|") },
                    calendarReminders = calendarReminders,
                    onOpenHealthAgenda = { selected = Tab.CALENDAR },
                    onOpenPassport = { pendingCareSection = "Pasaporte"; selected = Tab.HEALTH },
                    storageScope = storageScope
                )
                Tab.EXPLORE -> ExploreScreen(
                    savedPlaces = savedPlaceNames,
                    onToggleSave = { name ->
                        savedPlaceNames = if (name in savedPlaceNames) savedPlaceNames - name else savedPlaceNames + name
                    },
                    elegantDesign = elegantDesign,
                    repository = cloudRepository
                )
                Tab.LEARN -> LearningHubScreen(dogName = dogName, elegantDesign = elegantDesign)
                Tab.HEALTH -> {
                    val sharedPet = selectedSharedPet
                    if (sharedPet != null) {
                        val sharedPetId = sharedPet.sharedPetId.removePrefix("${sharedPet.ownerUid}_")
                        PetCareHubScreen(
                            petName = sharedPet.petName,
                            petId = sharedPetId,
                            storageScope = storageScope,
                            sharedPetOwnerUid = sharedPet.ownerUid,
                            onCloseSharedPet = { selectedSharedPet = null }
                        )
                    } else {
                        PetCareHubScreen(
                            petName = dogName,
                            petId = dogName,
                            storageScope = storageScope,
                            onOpenSharedPet = { selectedSharedPet = it },
                            initialSection = pendingCareSection ?: when {
                                pendingPassportType != null -> "Pasaporte"
                                pendingCareAction != null -> "Cuidados"
                                else -> null
                            },
                            initialPassportType = pendingPassportType,
                            initialCareAction = pendingCareAction,
                            onInitialActionConsumed = {
                                pendingPassportType = null
                                pendingCareAction = null
                                pendingCareSection = null
                            }
                        )
                    }
                }
                Tab.DIARY -> DiaryScreen(
                    dogName = selectedSharedPet?.petName ?: dogName,
                    entries = diaryEntries,
                    onEntriesChange = { diaryEntries = it },
                    repository = cloudRepository,
                    sharedPetOwnerUid = selectedSharedPet?.ownerUid ?: storageScope,
                    petId = selectedSharedPet?.let { it.sharedPetId.removePrefix("${it.ownerUid}_") } ?: dogName,
                    openPhotoPickerOnEnter = openDiaryPhotoPicker,
                    onPhotoPickerOpened = { openDiaryPhotoPicker = false }
                )
                Tab.CALENDAR -> PawlyCalendarScreen(
                    reminders = calendarReminders,
                    onRemindersChange = { calendarReminders = it },
                    repository = cloudRepository,
                    healthDates = healthDates.filterKeys { it.startsWith("${selectedSharedPet?.petName ?: dogName}|") }.mapKeys { it.key.removePrefix("${selectedSharedPet?.petName ?: dogName}|") },
                    diaryEntries = diaryEntries,
                    dogName = selectedSharedPet?.petName ?: dogName,
                    sharedPetOwnerUid = selectedSharedPet?.ownerUid ?: storageScope,
                    petId = selectedSharedPet?.let { it.sharedPetId.removePrefix("${it.ownerUid}_") } ?: dogName
                )
                Tab.COMMUNITY -> CommunityScreen(
                    openNotifications = openCommunityNotifications,
                    openProfile = openCommunityProfile,
                    onProfileOpened = { openCommunityProfile = false },
                    onLostPet = { selected = Tab.LOST }
                )
                Tab.MORE -> MoreHubScreen(
                    onProfile = { openCommunityProfile = true; selected = Tab.COMMUNITY },
                    onSettings = { showSettings = true },
                    onPawlyMarket = { selected = Tab.MARKET },
                    onSharePetPdf = {
                        coroutineScope.launch {
                            runCatching {
                                val entries = PetCareLocalStore.loadEntries(context, dogName, storageScope)
                                val tasks = PetCareLocalStore.loadTasks(context, dogName, storageScope)
                                val profile = cloudRepository?.getMyDogs()?.firstOrNull { it.name.equals(dogName, ignoreCase = true) }
                                PetCarePdfExporter.shareCompletePetReport(
                                    context, dogName, profile?.breed.orEmpty(), profile?.age.orEmpty(),
                                    profile?.description.orEmpty(), entries, tasks, profile?.species.orEmpty(), profile?.traits.orEmpty()
                                )
                            }.onFailure { profileSyncStatus = "No se pudo generar el informe: " + (it.localizedMessage ?: "error desconocido") }
                        }
                    },
                    onSavePetPdf = {
                        coroutineScope.launch {
                            runCatching {
                                val entries = PetCareLocalStore.loadEntries(context, dogName, storageScope)
                                val tasks = PetCareLocalStore.loadTasks(context, dogName, storageScope)
                                val profile = cloudRepository?.getMyDogs()?.firstOrNull { it.name.equals(dogName, ignoreCase = true) }
                                pendingPdfFile = PetCarePdfExporter.createCompletePetReport(
                                    context, dogName, profile?.breed.orEmpty(), profile?.age.orEmpty(),
                                    profile?.description.orEmpty(), entries, tasks, profile?.species.orEmpty(), profile?.traits.orEmpty()
                                )
                                val safeName = dogName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "mascota" }
                                pdfSaveLauncher.launch("pawly-" + safeName + ".pdf")
                            }.onFailure { profileSyncStatus = "No se pudo generar el informe: " + (it.localizedMessage ?: "error desconocido") }
                        }
                    },
                    onExportBackup = {
                        coroutineScope.launch {
                            runCatching {
                                // Pull the latest shared cloud records first. A backup taken
                                // while signed in must not silently omit another device's
                                // latest passport, routines or care history.
                                var backupDiaryEntries = diaryEntries
                                var backupCalendarReminders = calendarReminders
                                if (cloudRepository != null && !storageScope.isNullOrBlank()) {
                                    val careRepository = FirebasePetCareRepository(sharedPetOwnerUid = storageScope)
                                    careRepository.ensureSharedPet(dogName, dogName)
                                    val latestPassport = careRepository.loadPassportEntries(dogName)
                                    val latestTasks = careRepository.loadCareTasks(dogName)
                                    val latestLogs = careRepository.loadCareLogs(dogName, 500)
                                    val profileRepository = FirebasePetProfileRepository(ownerUid = storageScope)
                                    val sharedDiary = profileRepository.loadDiaryForBackup(dogName)
                                    backupDiaryEntries = (sharedDiary + diaryEntries).distinct().take(500)
                                    val agendaRepository = FirebasePetAgendaRepository(ownerUid = storageScope)
                                    val sharedAgenda = agendaRepository.loadReminders(dogName)
                                    backupCalendarReminders = (sharedAgenda + calendarReminders).distinct().take(500)
                                    val localLogs = PetCareLocalStore.loadCareLogs(context, dogName, storageScope)
                                    val combinedLogs = (latestLogs + localLogs).distinctBy { it.id }
                                        .sortedByDescending { it.occurredAt }.take(500)
                                    PetCareLocalStore.saveEntries(context, dogName, latestPassport, storageScope)
                                    PetCareLocalStore.saveTasks(context, dogName, latestTasks, storageScope)
                                    // Backup-restored walk history is retained locally even when
                                    // it has not yet been imported into the shared cloud timeline.
                                    PetCareLocalStore.saveCareLogs(context, dogName, combinedLogs, storageScope)
                                }
                                val petDates = healthDates.filterKeys { it.startsWith("$dogName|") }
                                    .mapKeys { (key, _) -> key.removePrefix("$dogName|") }
                                pendingBackupJson = PawlyBackupManager.create(
                                    context, dogName, dogProfiles, backupDiaryEntries, backupCalendarReminders,
                                    petDates, storageScope, dogPhotoPath
                                )
                                val safeName = dogName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "mascota" }
                                backupSaveLauncher.launch("pawly-backup-$safeName.json")
                            }.onFailure {
                                profileSyncStatus = "No se pudo preparar la copia completa: ${it.localizedMessage ?: "error"}"
                            }
                        }
                    },
                    onImportBackup = { backupImportLauncher.launch("application/json") },
                    onInviteFriend = {
                        val invite = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, PawlyI18n.text("Descubre Pawly", language))
                            putExtra(Intent.EXTRA_TEXT, PawlyI18n.text("¡Estoy probando Pawly, una aplicación para cuidar, organizar y compartir la vida con nuestras mascotas! Cuando esté disponible para descargar, te pasaré el enlace.", language))
                        }
                        context.startActivity(Intent.createChooser(invite, PawlyI18n.text("Invita a un amigo", language)))
                    },
                    onContact = {
                        val contact = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:alexvilarrubla619@gmail.com")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, PawlyI18n.text("Consulta sobre Pawly", language))
                            putExtra(Intent.EXTRA_TEXT, PawlyI18n.text("Hola, equipo de Pawly:\n\n", language))
                        }
                        runCatching { context.startActivity(Intent.createChooser(contact, PawlyI18n.text("Contacta con nosotros", language))) }
                            .onFailure {
                                val fallback = Intent(Intent.ACTION_SEND).apply {
                                    type = "message/rfc822"
                                    putExtra(Intent.EXTRA_SUBJECT, "Consulta sobre Pawly")
                                    putExtra(Intent.EXTRA_TEXT, "Hola, equipo de Pawly:\n\n")
                                }
                                context.startActivity(Intent.createChooser(fallback, PawlyI18n.text("Contacta con nosotros", language)))
                            }
                    },
                    onLostPet = { selected = Tab.LOST },
                    onPrivacy = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/avilmar625/Pawly/blob/cambios53/docs/PRIVACY_POLICY_DRAFT.md")))
                    },
                    onTerms = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/avilmar625/Pawly/blob/cambios53/docs/TERMS_OF_SERVICE_DRAFT.md")))
                    },
                    onSignOut = {
                        coroutineScope.launch {
                            runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
                            runCatching { cloudRepository?.signOut() }
                                .onFailure { profileSyncStatus = "No se pudo cerrar la sesión: " + (it.localizedMessage ?: "error desconocido") }
                        }
                    },
                    showSignOut = cloudRepository != null,
                    language = language
                )
                Tab.MARKET -> PawlyMarketHubScreen(onBack = { selected = Tab.MORE })
                Tab.SERVICES -> ProfessionalServicesScreen(
                    onBack = { selected = Tab.MORE },
                    language = language,
                    repository = cloudRepository
                )
                Tab.LOST -> LostPetHelpScreen(
                    repository = cloudRepository,
                    defaultPetName = dogName,
                    language = language,
                    onBack = { selected = Tab.MORE },
                    onPublished = { selected = Tab.COMMUNITY }
                )
            }
            }
            }
        }
    }

    if (showOnboarding) {
        PawlyOnboardingDialog(
            petName = dogName,
            onPetNameChange = { dogName = it },
            hasPhoto = dogPhoto != null,
            onPickPhoto = { galleryPicker.launch("image/*") },
            onPickCamera = { cameraCapture.launch(null) },
            selectedPhoto = dogPhoto,
            onEnableNotifications = {
                notificationsEnabled = arePawlyNotificationsEnabled(context)
                if (notificationsEnabled) {
                    notificationStatus = "¡Todo listo! Los recordatorios que programes podrán mostrar notificaciones."
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    val requestedBefore = preferences.getBoolean("notifications_permission_requested", false)
                    if (requestedBefore) {
                        notificationStatus = "Revisa los ajustes de Android y permite las notificaciones de Pawly."
                        openPawlyNotificationSettings(context)
                    } else {
                        preferences.edit().putBoolean("notifications_permission_requested", true).apply()
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    notificationStatus = "Las notificaciones de Pawly están bloqueadas en los ajustes del dispositivo. Ábrelos para permitirlas."
                    openPawlyNotificationSettings(context)
                }
            },
            notificationsEnabled = notificationsEnabled,
            notificationStatus = notificationStatus,
            onTestNotifications = {
                if (arePawlyTestNotificationsEnabled(context)) {
                    val testNotification = NotificationCompat.Builder(context, PAWLY_TEST_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_paw)
                        .setContentTitle("¡Pawly está listo!")
                        .setContentText("Las notificaciones funcionan. Aquí recibirás tus recordatorios.")
                        .setAutoCancel(true)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .build()
                    val notificationManager = NotificationManagerCompat.from(context)
                    preferences.getInt("last_test_notification_id", -1)
                        .takeIf { it >= 0 }
                        ?.let { notificationManager.cancel(it) }
                    val notificationId = (System.currentTimeMillis() and 0x7fffffff).toInt()
                    preferences.edit().putInt("last_test_notification_id", notificationId).apply()
                    notificationManager.notify(notificationId, testNotification)
                    notificationStatus = "Aviso de prueba enviado al canal de prueba. Revisa el panel de notificaciones; Android puede mostrarlo sin sonido si ha silenciado avisos recientes."
                } else {
                    notificationsEnabled = false
                    notificationStatus = "Las notificaciones están bloqueadas. Revisa los ajustes de Android."
                    openPawlyNotificationSettings(context)
                }
            },
            onFinish = {
                val cleanName = dogName.trim().ifBlank { "Mi mascota" }
                dogName = cleanName
                dogProfiles = dogProfiles + cleanName
                preferences.edit().putBoolean("onboarding_completed", true).putString("dog_name", cleanName).apply()
                showOnboarding = false
            }
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showSettings) {
        PawlySettingsDialog(
            darkMode = darkMode,
            accent = accent,
            language = language,
            designStyle = designStyle,
            onDesignStyleChange = onDesignStyleChange,
            onDarkModeChange = onDarkModeChange,
            onAccentChange = onAccentChange,
            onLanguageChange = { language = it; preferences.edit().putString("language", it).apply() },
            onDismiss = { showSettings = false }
        )
    }
    if (showProfileEditor) {
        ProfileDialog(
            name = profileDraft,
            onNameChange = { profileDraft = it },
            onDismiss = { showProfileEditor = false },
            onSave = {
                val cleaned = profileDraft.trim()
                if (cleaned.isNotEmpty()) {
                    dogProfiles = (dogProfiles - dogName) + cleaned
                    val renamedPhotoKey = com.pawly.app.care.domain.PetRoutinePreferenceKeys.photoPath(
                        com.pawly.app.care.domain.PetRoutinePreferenceKeys.scope(cleaned)
                    )
                    if (!preferences.contains(renamedPhotoKey) && !dogPhotoPath.isNullOrBlank()) {
                        preferences.edit().putString(renamedPhotoKey, dogPhotoPath).apply()
                    }
                    dogName = cleaned
                    homePetCloudReady = true
                }
                showProfileEditor = false
            },
            onGallery = { galleryPicker.launch("image/*") },
            onCamera = { cameraCapture.launch(null) },
            hasPhoto = dogPhoto != null,
            photoPreview = dogPhoto,
            syncStatus = profileSyncStatus,
            profiles = dogProfiles,
            onSelectProfile = { dogName = it; homePetCloudReady = true; showProfileEditor = false },
            onAddProfile = {
                val cleaned = profileDraft.trim()
                if (cleaned.isNotEmpty()) {
                    dogProfiles = dogProfiles + cleaned
                    dogName = cleaned
                    homePetCloudReady = true
                    showProfileEditor = false
                }
            },
            onDeleteProfile = { name ->
                if (dogProfiles.size > 1) {
                    dogProfiles = dogProfiles - name
                    if (dogName == name) dogName = dogProfiles.firstOrNull() ?: "Nala"
                    homePetCloudReady = true
                }
            }
        )
    }
    }
}
