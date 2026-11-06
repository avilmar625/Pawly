package com.pawly.app

import com.pawly.app.care.data.PetCareLocalStore
import com.pawly.app.care.data.FirebasePetProfileRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

internal enum class Tab(val label: String) {
    HOME("Inicio"), EXPLORE("Explorar"), LEARN("Aprender"), HEALTH("Salud"), DIARY("Diario"), COMMUNITY("Comunidad"), MORE("Más")
}

@Composable
internal fun PawlyApp(
    darkMode: Boolean,
    accent: String,
    onDarkModeChange: (Boolean) -> Unit,
    onAccentChange: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { FirebaseCommunityRepository.create(context) }
    var localMode by remember { mutableStateOf(false) }
    var signedIn by remember(repository) { mutableStateOf(repository?.currentUid != null) }

    if (localMode) {
        PawlyMainApp(darkMode, accent, onDarkModeChange, onAccentChange)
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

    // Keep the current FCM token associated with the signed-in account.
    // This registers the device; it does not send push notifications.
    LaunchedEffect(signedIn, repository) {
        if (signedIn && repository != null) {
            runCatching { repository.syncMessagingToken() }
        }
    }

    if (signedIn) {
        PawlyMainApp(darkMode, accent, onDarkModeChange, onAccentChange, storageScope = repository.currentUid)
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
    darkMode: Boolean,
    accent: String,
    onDarkModeChange: (Boolean) -> Unit,
    onAccentChange: (String) -> Unit,
    storageScope: String? = null
) {
    val context = LocalContext.current
    val preferences = remember(context, storageScope) { context.getSharedPreferences(storageScope?.let { "pawly_user_$it" } ?: "pawly_local", android.content.Context.MODE_PRIVATE) }
    val profileRepository = remember(storageScope) {
        storageScope?.let { runCatching { FirebasePetProfileRepository(ownerUid = it) }.getOrNull() }
    }
    val imageUploader = remember(context) { CloudinaryImageUploader(context) }
    val coroutineScope = rememberCoroutineScope()
    var cloudSyncReadyForPet by remember { mutableStateOf<String?>(null) }
    var cloudSyncMessage by remember { mutableStateOf(if (storageScope == null) "Solo en este dispositivo" else "Preparando sincronización…") }
    var selected by remember { mutableStateOf(Tab.HOME) }
    var showSettings by remember { mutableStateOf(false) }
    val settingsPreferences = remember(context) { context.getSharedPreferences("pawly_appearance", android.content.Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(settingsPreferences.getString("language", "es") ?: "es") }
    var designStyle by remember { mutableStateOf(settingsPreferences.getString("design_style", "simple") ?: "simple") }
    val elegantDesign = designStyle == "elegant"
    var showAbout by remember { mutableStateOf(false) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var profileDraft by remember { mutableStateOf("") }
    var dogName by remember { mutableStateOf(preferences.getString("dog_name", "Nala") ?: "Nala") }
    // Keep the legacy deterministic ID on first use so existing Firestore data remains addressable.
    val activePetId = remember(dogName, preferences) {
        val normalizedName = dogName.trim().lowercase()
        val key = "pet_id_$normalizedName"
        preferences.getString(key, null) ?: java.util.UUID.nameUUIDFromBytes(
            "pawly-pet:$normalizedName".toByteArray()
        ).toString().also { preferences.edit().putString(key, it).apply() }
    }
    var dogProfiles by remember { mutableStateOf(preferences.getStringSet("dog_profiles", setOf(dogName))?.toSet() ?: setOf(dogName)) }
    var dogPhotoPath by remember(activePetId, preferences) {
        mutableStateOf(preferences.getString("dog_photo_path_$activePetId", null) ?: preferences.getString("dog_photo_path", null))
    }
    var remoteDogPhotoUrl by remember(activePetId, preferences) {
        mutableStateOf(preferences.getString("dog_photo_url_$activePetId", null))
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val destination = File(context.filesDir, "dog-profile-${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            dogPhotoPath = destination.absolutePath
            if (storageScope != null && profileRepository != null) {
                coroutineScope.launch {
                    runCatching {
                        val url = imageUploader.upload(uri, "dog-photos", storageScope)
                        profileRepository.saveProfile(activePetId, dogName, url)
                        remoteDogPhotoUrl = url
                        preferences.edit().putString("dog_photo_url_$activePetId", url).apply()
                        cloudSyncMessage = "Foto sincronizada"
                    }.onFailure {
                        cloudSyncMessage = "Foto guardada aquí; no se pudo subir: ${it.localizedMessage ?: "error de red"}"
                    }
                }
            }
        }
    }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) runCatching {
            val destination = File(context.filesDir, "dog-profile-${System.currentTimeMillis()}.jpg")
            destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            dogPhotoPath = destination.absolutePath
            if (storageScope != null && profileRepository != null) {
                coroutineScope.launch {
                    runCatching {
                        val url = imageUploader.upload(Uri.fromFile(destination), "dog-photos", storageScope)
                        profileRepository.saveProfile(activePetId, dogName, url)
                        remoteDogPhotoUrl = url
                        preferences.edit().putString("dog_photo_url_$activePetId", url).apply()
                        cloudSyncMessage = "Foto sincronizada"
                    }.onFailure {
                        cloudSyncMessage = "Foto guardada aquí; no se pudo subir: ${it.localizedMessage ?: "error de red"}"
                    }
                }
            }
        }
    }
    val dogPhoto = remember(dogPhotoPath) { dogPhotoPath?.let(BitmapFactory::decodeFile) }
    var walkDone by remember { mutableStateOf(preferences.getBoolean("walk_done", false)) }
    var medicineDone by remember { mutableStateOf(preferences.getBoolean("medicine_done", false)) }
    var healthDates by remember {
        mutableStateOf(runCatching {
            val json = JSONArray(preferences.getString("health_dates", "[]") ?: "[]")
            buildMap { for (i in 0 until json.length()) { val row = json.getJSONArray(i); put(row.getString(0), row.getString(1)) } }
        }.getOrDefault(emptyMap()))
    }
    var savedPlaceNames by remember {
        mutableStateOf(preferences.getStringSet("saved_places", emptySet())?.toSet() ?: emptySet())
    }
    val storedEntries = remember(preferences) {
        runCatching {
            val json = JSONArray(preferences.getString("diary_entries", "[]") ?: "[]")
            List(json.length()) { index -> json.getString(index) }
        }.getOrDefault(emptyList())
    }
    var diaryEntries by remember { mutableStateOf(storedEntries) }

    LaunchedEffect(dogName) { preferences.edit().putString("dog_name", dogName).apply() }
    LaunchedEffect(dogProfiles) { preferences.edit().putStringSet("dog_profiles", dogProfiles.toSet()).apply() }
    LaunchedEffect(dogPhotoPath, activePetId) { preferences.edit().putString("dog_photo_path_$activePetId", dogPhotoPath).apply() }
    LaunchedEffect(remoteDogPhotoUrl, activePetId) {
        preferences.edit().putString("dog_photo_url_$activePetId", remoteDogPhotoUrl).apply()
    }
    LaunchedEffect(activePetId, dogName, storageScope, profileRepository) {
        cloudSyncReadyForPet = null
        if (profileRepository == null) {
            cloudSyncReadyForPet = activePetId
            cloudSyncMessage = "Solo en este dispositivo"
        } else {
            runCatching {
                profileRepository.ensurePet(activePetId, dogName)
                val cloudProfile = profileRepository.loadProfile(activePetId)
                if (cloudProfile == null) {
                    profileRepository.saveProfile(activePetId, dogName, remoteDogPhotoUrl)
                } else {
                    if (cloudProfile.name.isNotBlank() && cloudProfile.name != dogName) {
                        dogProfiles = (dogProfiles - dogName) + cloudProfile.name
                        dogName = cloudProfile.name
                    }
                    if (!cloudProfile.photoUrl.isNullOrBlank()) {
                        remoteDogPhotoUrl = cloudProfile.photoUrl
                        preferences.edit().putString("dog_photo_url_$activePetId", cloudProfile.photoUrl).apply()
                    }
                }
                val cloudDiary = profileRepository.loadDiary(activePetId)
                if (cloudDiary.isNotEmpty()) {
                    diaryEntries = cloudDiary
                } else if (diaryEntries.isNotEmpty()) {
                    profileRepository.replaceDiary(activePetId, diaryEntries)
                }
                cloudSyncMessage = "Perfil y diario sincronizados"
            }.onFailure {
                cloudSyncMessage = "Sincronización pendiente: ${it.localizedMessage ?: "comprueba la conexión y las reglas de Firestore"}"
            }
            cloudSyncReadyForPet = activePetId
        }
    }
    LaunchedEffect(diaryEntries, activePetId, cloudSyncReadyForPet) {
        if (profileRepository != null && cloudSyncReadyForPet == activePetId) {
            runCatching { profileRepository.replaceDiary(activePetId, diaryEntries) }
                .onSuccess { cloudSyncMessage = "Diario sincronizado" }
                .onFailure { cloudSyncMessage = "Diario pendiente de sincronizar" }
        }
    }
    LaunchedEffect(dogName, activePetId, cloudSyncReadyForPet) {
        if (profileRepository != null && cloudSyncReadyForPet == activePetId) {
            runCatching { profileRepository.saveProfile(activePetId, dogName, remoteDogPhotoUrl) }
                .onFailure { cloudSyncMessage = "Perfil pendiente de sincronizar" }
        }
    }
    LaunchedEffect(walkDone) { preferences.edit().putBoolean("walk_done", walkDone).apply() }
    LaunchedEffect(medicineDone) { preferences.edit().putBoolean("medicine_done", medicineDone).apply() }
    LaunchedEffect(healthDates) {
        val json = JSONArray()
        healthDates.forEach { (key, value) -> json.put(org.json.JSONArray().put(key).put(value)) }
        preferences.edit().putString("health_dates", json.toString()).apply()
    }
    LaunchedEffect(savedPlaceNames) {
        preferences.edit().putStringSet("saved_places", savedPlaceNames.toSet()).apply()
    }
    LaunchedEffect(diaryEntries) {
        val json = JSONArray()
        diaryEntries.forEach { entry -> json.put(entry) }
        preferences.edit().putString("diary_entries", json.toString()).apply()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 0.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 19.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primary) {
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Pets, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("pawly", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp, fontSize = 22.sp)
                        Text("MÁS VIDA JUNTOS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, fontSize = 8.sp)
                    }
                    Surface(
                        onClick = { showSettings = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Tune, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp),
                shadowElevation = 12.dp
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                ) {
                    // Five focused destinations; secondary areas stay grouped under More.
                    listOf(Tab.HOME, Tab.EXPLORE, Tab.HEALTH, Tab.COMMUNITY, Tab.MORE).forEach { tab ->
                        val active = if (tab == Tab.MORE) selected == Tab.MORE || selected == Tab.LEARN || selected == Tab.DIARY else selected == tab
                        val tabIcon = when (tab) {
                            Tab.HOME -> Icons.Filled.Home
                            Tab.EXPLORE -> Icons.Filled.Explore
                            Tab.LEARN -> Icons.Filled.MenuBook
                            Tab.HEALTH -> Icons.Filled.FavoriteBorder
                            Tab.DIARY -> Icons.Filled.AutoStories
                            Tab.COMMUNITY -> Icons.Filled.Groups
                            Tab.MORE -> Icons.Filled.GridView
                        }
                        NavigationBarItem(
                            selected = active,
                            onClick = { selected = tab },
                            icon = {
                                Icon(tabIcon, contentDescription = tab.label, modifier = Modifier.size(21.dp))
                            },
                            label = { Text(tab.label, maxLines = 1, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
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
                    dogPhotoUrl = remoteDogPhotoUrl,
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
                Tab.HEALTH -> PetCareHubScreen(
                    petName = dogName,
                    petId = activePetId,
                    storageScope = storageScope,
                    medicineDone = medicineDone,
                    onMedicineToggle = { medicineDone = it },
                    dates = healthDates.filterKeys { it.startsWith("$dogName|") }.mapKeys { (key, _) -> key.removePrefix("$dogName|") },
                    onDateChange = { key, value ->
                        val fullKey = "$dogName|$key"
                        healthDates = if (value == null) healthDates - fullKey else healthDates + (fullKey to value)
                    },
                    elegantDesign = elegantDesign
                )
                Tab.DIARY -> DiaryScreen(
                    dogName = dogName,
                    entries = diaryEntries,
                    onEntriesChange = { diaryEntries = it },
                    elegantDesign = elegantDesign
                )
                Tab.COMMUNITY -> CommunityScreen(elegantDesign = elegantDesign)
                Tab.MORE -> MoreHubScreen(
                    onLearn = { selected = Tab.LEARN },
                    onDiary = { selected = Tab.DIARY },
                    elegantDesign = elegantDesign
                )
            }
        }
    }

    if (showSettings) {
        PawlySettingsDialog(
            darkMode = darkMode,
            accent = accent,
            language = language,
            designStyle = designStyle,
            onDesignStyleChange = { designStyle = it; settingsPreferences.edit().putString("design_style", it).apply() },
            onDarkModeChange = onDarkModeChange,
            onAccentChange = onAccentChange,
            onLanguageChange = { language = it; settingsPreferences.edit().putString("language", it).apply() },
            onDismiss = { showSettings = false }
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showProfileEditor) {
        ProfileDialog(
            name = profileDraft,
            onNameChange = { profileDraft = it },
            onDismiss = { showProfileEditor = false },
            onSave = {
                val cleaned = profileDraft.trim()
                if (cleaned.isNotEmpty()) {
                    if (cleaned != dogName) {
                        PetCareLocalStore.renamePet(context, dogName, cleaned, storageScope)
                        val oldHealthPrefix = "${dogName}|"
                        healthDates = healthDates.mapKeys { (key, value) ->
                            if (key.startsWith(oldHealthPrefix)) "${cleaned}|${key.removePrefix(oldHealthPrefix)}" else key
                        }
                        val oldKey = "pet_id_${dogName.trim().lowercase()}"
                        val newKey = "pet_id_${cleaned.lowercase()}"
                        val stableId = preferences.getString(oldKey, null)
                            ?: java.util.UUID.nameUUIDFromBytes(
                                "pawly-pet:${dogName.trim().lowercase()}".toByteArray()
                            ).toString()
                        if (!preferences.contains(newKey)) {
                            preferences.edit().putString(newKey, stableId).apply()
                        }
                        preferences.edit().remove(oldKey).apply()
                    }
                    dogProfiles = (dogProfiles - dogName) + cleaned
                    dogName = cleaned
                }
                showProfileEditor = false
            },
            onGallery = { galleryPicker.launch("image/*") },
            onCamera = { cameraCapture.launch(null) },
            hasPhoto = dogPhoto != null || !remoteDogPhotoUrl.isNullOrBlank(),
            syncStatus = cloudSyncMessage,
            profiles = dogProfiles,
            onSelectProfile = { dogName = it; showProfileEditor = false },
            onAddProfile = {
                val cleaned = profileDraft.trim()
                if (cleaned.isNotEmpty()) { dogProfiles = dogProfiles + cleaned; dogName = cleaned; showProfileEditor = false }
            },
            onDeleteProfile = { name ->
                if (dogProfiles.size > 1) {
                    dogProfiles = dogProfiles - name
                    if (dogName == name) dogName = dogProfiles.firstOrNull() ?: "Nala"
                }
            }
        )
    }
}
