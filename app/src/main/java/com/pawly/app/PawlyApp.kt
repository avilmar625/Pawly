package com.pawly.app

import com.pawly.app.care.data.PetShareInvitation
import com.pawly.app.care.data.PetCareLocalStore
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
    HOME("Inicio"), EXPLORE("Explorar"), LEARN("Aprender"), HEALTH("Salud"), DIARY("Diario"), COMMUNITY("Comunidad"), MORE("Más"), SERVICES("Profesionales"), LOST("Ayuda")
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
    var selectedSharedPet by remember { mutableStateOf<PetShareInvitation?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val elegantDesign = designStyle == "elegant"
    var showProfileEditor by remember { mutableStateOf(false) }
    var profileDraft by remember { mutableStateOf("") }
    var dogName by remember(preferences) { mutableStateOf(preferences.getString("dog_name", "Nala") ?: "Nala") }
    var dogProfiles by remember(preferences) { mutableStateOf(preferences.getStringSet("dog_profiles", setOf(dogName))?.toSet() ?: setOf(dogName)) }
    var homePetCloudReady by remember(preferences, storageScope) { mutableStateOf(false) }
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
                        val active = selected == tab || (tab == Tab.MORE && selected == Tab.SERVICES)
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
                            Tab.MORE -> Icons.Filled.MoreHoriz
                            Tab.SERVICES -> Icons.Filled.WorkspacePremium
                            Tab.LOST -> Icons.Filled.ReportProblem
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
                            // Keep the bottom navigation compact: the selected destination
                            // retains its label while the other destinations use icon-only items.
                            if (active) {
                                Text(
                                    label,
                                    color = tabTint,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
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
                    language = language
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
