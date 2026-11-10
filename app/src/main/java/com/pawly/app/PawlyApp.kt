package com.pawly.app

import com.pawly.app.care.data.PetShareInvitation
import com.google.firebase.auth.FirebaseAuth
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
    HOME("Inicio"), EXPLORE("Explorar"), LEARN("Aprender"), HEALTH("Salud"), DIARY("Diario"), COMMUNITY("Comunidad"), MORE("Más")
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
    var cloudinaryCloudName by remember(servicePreferences) { mutableStateOf(servicePreferences.getString("cloudinary_cloud_name", BuildConfig.CLOUDINARY_CLOUD_NAME).orEmpty()) }
    var cloudinaryImagePreset by remember(servicePreferences) { mutableStateOf(servicePreferences.getString("cloudinary_image_preset", BuildConfig.CLOUDINARY_IMAGE_UPLOAD_PRESET).orEmpty()) }
    var cloudinaryVideoPreset by remember(servicePreferences) { mutableStateOf(servicePreferences.getString("cloudinary_video_preset", BuildConfig.CLOUDINARY_VIDEO_UPLOAD_PRESET).orEmpty()) }
    val preferences = remember(context, storageScope) { context.getSharedPreferences(storageScope?.let { "pawly_user_$it" } ?: "pawly_local", android.content.Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(Tab.HOME) }
    var selectedSharedPet by remember { mutableStateOf<PetShareInvitation?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val elegantDesign = designStyle == "elegant"
    var showProfileEditor by remember { mutableStateOf(false) }
    var profileDraft by remember { mutableStateOf("") }
    var dogName by remember(preferences) { mutableStateOf(preferences.getString("dog_name", "Nala") ?: "Nala") }
    var dogProfiles by remember(preferences) { mutableStateOf(preferences.getStringSet("dog_profiles", setOf(dogName))?.toSet() ?: setOf(dogName)) }
    var homePetCloudReady by remember(preferences, storageScope) { mutableStateOf(false) }
    var profileSyncStatus by remember(preferences, storageScope) { mutableStateOf("") }
    var dogPhotoPath by remember(preferences) { mutableStateOf(preferences.getString("dog_photo_path", null)) }
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
    val dogPhoto = remember(dogPhotoPath) { dogPhotoPath?.let(BitmapFactory::decodeFile) }
    var walkDone by remember(preferences) { mutableStateOf(preferences.getBoolean("walk_done", false)) }
    var medicineDone by remember(preferences) { mutableStateOf(preferences.getBoolean("medicine_done", false)) }
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
    LaunchedEffect(preferences, dogPhotoPath) { preferences.edit().putString("dog_photo_path", dogPhotoPath).apply() }
    LaunchedEffect(preferences, walkDone) { preferences.edit().putBoolean("walk_done", walkDone).apply() }
    LaunchedEffect(preferences, medicineDone) { preferences.edit().putBoolean("medicine_done", medicineDone).apply() }
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
                        val active = selected == tab
                        val tabIcon = when (tab) {
                            Tab.HOME -> Icons.Filled.Home
                            Tab.EXPLORE -> Icons.Filled.Explore
                            Tab.LEARN -> Icons.Filled.MenuBook
                            Tab.HEALTH -> Icons.Filled.FavoriteBorder
                            Tab.DIARY -> Icons.Filled.AutoStories
                            Tab.COMMUNITY -> Icons.Filled.Groups
                            Tab.MORE -> Icons.Filled.MoreHoriz
                        }
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                .clickable { selected = tab }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                Modifier.width(42.dp).height(29.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    tabIcon,
                                    contentDescription = tab.label,
                                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                tab.label,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
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
                    onEditProfile = { profileDraft = dogName; showProfileEditor = true },
                    diaryCount = diaryEntries.size,
                    dogPhoto = dogPhoto,
                    elegantDesign = elegantDesign
                )
                Tab.EXPLORE -> ExploreScreen(
                    savedPlaces = savedPlaceNames,
                    onToggleSave = { name ->
                        savedPlaceNames = if (name in savedPlaceNames) savedPlaceNames - name else savedPlaceNames + name
                    },
                    elegantDesign = elegantDesign
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
                            onOpenSharedPet = { selectedSharedPet = it }
                        )
                    }
                }
                Tab.DIARY -> DiaryScreen(
                    dogName = dogName,
                    elegantDesign = elegantDesign,
                    entries = diaryEntries,
                    onEntriesChange = { diaryEntries = it }
                )
                Tab.COMMUNITY -> CommunityScreen(elegantDesign = elegantDesign)
                Tab.MORE -> MoreHubScreen(
                    onLearn = { selected = Tab.LEARN },
                    onDiary = { selected = Tab.DIARY },
                    onSettings = { showSettings = true },
                    elegantDesign = elegantDesign
                )
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showSettings) {
        PawlySettingsDialog(
            darkMode = darkMode,
            accent = accent,
            language = preferences.getString("language", "es") ?: "es",
            designStyle = designStyle,
            onDesignStyleChange = onDesignStyleChange,
            onDarkModeChange = onDarkModeChange,
            onAccentChange = onAccentChange,
            onLanguageChange = { preferences.edit().putString("language", it).apply() },
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
