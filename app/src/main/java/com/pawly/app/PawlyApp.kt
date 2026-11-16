package com.pawly.app

import com.pawly.app.care.data.PetShareInvitation
import com.pawly.app.care.data.PetCareLocalStore
import com.pawly.app.care.data.FirebasePetCareRepository
import com.pawly.app.care.data.FirebasePetProfileRepository
import com.pawly.app.care.export.PetCarePdfExporter
import com.google.firebase.auth.FirebaseAuth
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import android.os.Bundle
import android.content.Intent
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import org.json.JSONArray
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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

internal enum class Tab(val label: String) {
    HOME("Hoy"), EXPLORE("Explorar"), LEARN("Aprender"), HEALTH("Mi perro"), DIARY("Diario"), CALENDAR("Calendario"), COMMUNITY("Comunidad"), MORE("Más"), SERVICES("Profesionales"), LOST("Ayuda")
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
            signedIn = firebaseAuth.currentUser != null
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
            onAuthenticated = { signedIn = repository.currentUid != null },
            onContinueOffline = { localMode = true }
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
    val servicePreferences = remember(context) { context.getSharedPreferences("pawly_services", android.content.Context.MODE_PRIVATE) }
    var cloudinaryCloudName by remember(servicePreferences) { mutableStateOf(servicePreferences.getString("cloudinary_cloud_name", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.CLOUDINARY_CLOUD_NAME) }
    var cloudinaryImagePreset by remember(servicePreferences) { mutableStateOf(servicePreferences.getString("cloudinary_image_preset", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.CLOUDINARY_IMAGE_UPLOAD_PRESET) }
    var cloudinaryVideoPreset by remember(servicePreferences) { mutableStateOf(servicePreferences.getString("cloudinary_video_preset", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.CLOUDINARY_VIDEO_UPLOAD_PRESET) }
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
    var openCommunityNotifications by remember { mutableStateOf(false) }
    var pendingCareAction by remember { mutableStateOf<String?>(null) }
    var pendingPassportType by remember { mutableStateOf<String?>(null) }
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
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        profileSyncStatus = if (granted) "Notificaciones activadas." else "Puedes activarlas más adelante desde los ajustes del móvil."
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
                        val careRepository = FirebasePetCareRepository()
                        careRepository.ensureSharedPet(snapshot.petName, snapshot.petName)
                        careRepository.savePassportEntries(snapshot.petName, snapshot.passportEntries)
                        careRepository.saveCareTasks(snapshot.petName, snapshot.careTasks)
                        FirebasePetProfileRepository(ownerUid = storageScope.orEmpty()).replaceDiary(snapshot.petName, snapshot.diaryEntries)
                    }
                }.onSuccess {
                    profileSyncStatus = "Copia restaurada y sincronizada. El historial compartido de cuidadores se conserva."
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
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 5.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(Tab.HOME, Tab.EXPLORE, Tab.HEALTH, Tab.COMMUNITY, Tab.MORE).forEach { tab ->
                        val label = PawlyI18n.text(tab.label, language)
                        val active = selected == tab || (tab == Tab.MORE && selected in setOf(Tab.SERVICES, Tab.LOST, Tab.CALENDAR, Tab.DIARY, Tab.LEARN))
                        val tabTint = when (tab) {
                            Tab.HOME -> Color(0xFF26836B)
                            Tab.EXPLORE -> Color(0xFF3979B8)
                            Tab.HEALTH -> Color(0xFFD45E70)
                            Tab.COMMUNITY -> Color(0xFF8962B3)
                            Tab.MORE -> Color(0xFFB47A37)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val tabIcon = when (tab) {
                            Tab.HOME -> Icons.Filled.Home
                            Tab.EXPLORE -> Icons.Filled.Explore
                            Tab.LEARN -> Icons.Filled.MenuBook
                            Tab.HEALTH -> Icons.Filled.FavoriteBorder
                            Tab.DIARY -> Icons.Filled.AutoStories
                            Tab.COMMUNITY -> Icons.Filled.Groups
                            Tab.CALENDAR -> Icons.Filled.CalendarMonth
                            Tab.MORE -> Icons.Filled.MoreHoriz
                            Tab.SERVICES -> Icons.Filled.WorkspacePremium
                            Tab.LOST -> Icons.Filled.ReportProblem
                        }
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                .clickable {
                                    if (tab != Tab.COMMUNITY) openCommunityNotifications = false
                                    selected = tab
                                }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                Modifier.width(42.dp).height(29.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) tabTint.copy(alpha = .14f) else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    tabIcon,
                                    contentDescription = label,
                                    tint = tabTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Keep every destination labelled: icon-only navigation
                            // made the app harder to discover for first-time users.
                            Text(
                                label,
                                color = if (active) tabTint else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
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
                    diaryCount = diaryEntries.size,
                    diaryEntries = diaryEntries,
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
                            medicineDone = medicineDone,
                            onMedicineToggle = { medicineDone = it },
                            dates = emptyMap(),
                            onDateChange = { _, _ -> },
                            elegantDesign = elegantDesign,
                            sharedPetOwnerUid = sharedPet.ownerUid,
                            onCloseSharedPet = { selectedSharedPet = null }
                        )
                    } else {
                        PetCareHubScreen(
                            petName = dogName,
                            elegantDesign = elegantDesign,
                            petId = dogName,
                            storageScope = storageScope,
                            medicineDone = medicineDone,
                            onMedicineToggle = { medicineDone = it },
                            dates = healthDates.filterKeys { it.startsWith("$dogName|") }
                                .mapKeys { (key, _) -> key.removePrefix("$dogName|") },
                            onDateChange = { key, value ->
                                val fullKey = "$dogName|$key"
                                healthDates = if (value == null) healthDates - fullKey else healthDates + (fullKey to value)
                            },
                            onOpenSharedPet = { selectedSharedPet = it },
                            initialSection = when {
                                pendingPassportType != null -> "Pasaporte"
                                pendingCareAction != null -> "Cuidados"
                                else -> null
                            },
                            initialPassportType = pendingPassportType,
                            initialCareAction = pendingCareAction,
                            onInitialActionConsumed = {
                                pendingPassportType = null
                                pendingCareAction = null
                            }
                        )
                    }
                }
                Tab.DIARY -> DiaryScreen(
                    dogName = dogName,
                    elegantDesign = elegantDesign,
                    entries = diaryEntries,
                    onEntriesChange = { diaryEntries = it },
                    repository = cloudRepository,
                    openPhotoPickerOnEnter = openDiaryPhotoPicker,
                    onPhotoPickerOpened = { openDiaryPhotoPicker = false }
                )
                Tab.CALENDAR -> PawlyCalendarScreen(reminders = calendarReminders, onRemindersChange = { calendarReminders = it }, repository = cloudRepository, healthDates = healthDates.filterKeys { it.startsWith("$dogName|") }.mapKeys { it.key.removePrefix("$dogName|") }, diaryEntries = diaryEntries, dogName = dogName)
                Tab.COMMUNITY -> CommunityScreen(
                    elegantDesign = elegantDesign,
                    openNotifications = openCommunityNotifications
                )
                Tab.MORE -> MoreHubScreen(
                    onLearn = { selected = Tab.LEARN },
                    onDiary = { selected = Tab.DIARY },
                    onCalendar = { selected = Tab.CALENDAR },
                    onSettings = { showSettings = true },
                    onProfessionals = { selected = Tab.SERVICES },
                    onLostPet = { selected = Tab.LOST },
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
                        runCatching {
                            val petDates = healthDates.filterKeys { it.startsWith("$dogName|") }
                                .mapKeys { (key, _) -> key.removePrefix("$dogName|") }
                            pendingBackupJson = PawlyBackupManager.create(
                                context, dogName, dogProfiles, diaryEntries, calendarReminders,
                                petDates, storageScope, dogPhotoPath
                            )
                            val safeName = dogName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "mascota" }
                            backupSaveLauncher.launch("pawly-backup-$safeName.json")
                        }.onFailure { profileSyncStatus = "No se pudo preparar la copia: ${it.localizedMessage ?: "error"}" }
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
                    language = language,
                    elegantDesign = elegantDesign
                )
                Tab.SERVICES -> ProfessionalServicesScreen(
                    onBack = { selected = Tab.MORE },
                    elegantDesign = elegantDesign,
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

    if (showOnboarding) {
        PawlyOnboardingDialog(
            petName = dogName,
            onPetNameChange = { dogName = it },
            hasPhoto = dogPhoto != null,
            onPickPhoto = { galleryPicker.launch("image/*") },
            onEnableNotifications = {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else profileSyncStatus = "Las notificaciones están disponibles en este dispositivo."
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
            cloudinaryCloudName = cloudinaryCloudName,
            cloudinaryImagePreset = cloudinaryImagePreset,
            cloudinaryVideoPreset = cloudinaryVideoPreset,
            onSaveCloudinary = { cloudName, imagePreset, videoPreset ->
                cloudinaryCloudName = cloudName
                cloudinaryImagePreset = imagePreset
                cloudinaryVideoPreset = videoPreset
                servicePreferences.edit()
                    .putString("cloudinary_cloud_name", cloudName)
                    .putString("cloudinary_image_preset", imagePreset)
                    .putString("cloudinary_video_preset", videoPreset)
                    .apply()
            },
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
