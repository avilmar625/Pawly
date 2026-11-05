package com.pawly.app

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.core.content.ContextCompat
import org.json.JSONArray

internal enum class Tab(val label: String) {
    HOME("Inicio"), EXPLORE("Explorar"), HEALTH("Salud"), DIARY("Diario")
}

@Composable
internal fun PawlyApp() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("pawly_local", android.content.Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(Tab.HOME) }
    var showAbout by remember { mutableStateOf(false) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var profileDraft by remember { mutableStateOf("") }
    var dogName by remember { mutableStateOf(preferences.getString("dog_name", "Nala") ?: "Nala") }
    var dogProfiles by remember { mutableStateOf(preferences.getStringSet("dog_profiles", setOf(dogName))?.toSet() ?: setOf(dogName)) }
    var dogPhotoPath by remember { mutableStateOf(preferences.getString("dog_photo_path", null)) }
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
    LaunchedEffect(dogPhotoPath) { preferences.edit().putString("dog_photo_path", dogPhotoPath).apply() }
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
        containerColor = Cream,
        bottomBar = {
            NavigationBar(
                containerColor = Paper,
                contentColor = Forest,
                tonalElevation = 0.dp
            ) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.HOME -> Icons.Filled.Home
                                    Tab.EXPLORE -> Icons.Filled.Explore
                                    Tab.HEALTH -> Icons.Filled.FavoriteBorder
                                    Tab.DIARY -> Icons.Filled.AutoStories
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Forest,
                            selectedTextColor = Forest,
                            indicatorColor = Lime.copy(alpha = 0.65f),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
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
                    onAbout = { showAbout = true },
                    onEditProfile = { profileDraft = dogName; showProfileEditor = true },
                    diaryCount = diaryEntries.size,
                    dogPhoto = dogPhoto
                )
                Tab.EXPLORE -> ExploreScreen(
                    savedPlaces = savedPlaceNames,
                    onToggleSave = { name ->
                        savedPlaceNames = if (name in savedPlaceNames) savedPlaceNames - name else savedPlaceNames + name
                    }
                )
                Tab.HEALTH -> HealthScreen(
                    medicineDone = medicineDone,
                    onMedicineToggle = { medicineDone = it },
                    dates = healthDates.filterKeys { it.startsWith("$dogName|") }.mapKeys { (key, _) -> key.removePrefix("$dogName|") },
                    onDateChange = { key, value ->
                        val fullKey = "$dogName|$key"
                        healthDates = if (value == null) healthDates - fullKey else healthDates + (fullKey to value)
                    }
                )
                Tab.DIARY -> DiaryScreen(
                    dogName = dogName,
                    entries = diaryEntries,
                    onEntriesChange = { diaryEntries = it }
                )
            }
        }
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
                    dogProfiles = (dogProfiles - dogName) + cleaned
                    dogName = cleaned
                }
                showProfileEditor = false
            },
            onGallery = { galleryPicker.launch("image/*") },
            onCamera = { cameraCapture.launch(null) },
            hasPhoto = dogPhoto != null,
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

@Composable
