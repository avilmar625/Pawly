package com.pawly.app

import android.net.Uri
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun LostPetHelpScreen(
    repository: FirebaseCommunityRepository?,
    defaultPetName: String,
    language: String,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var petName by remember(defaultPetName) { mutableStateOf(defaultPetName) }
    var breed by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var lastSeen by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var approximateLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var receiveNearbyAlerts by remember { mutableStateOf(false) }
    var alertRadius by remember { mutableStateOf(10) }
    var subscriptionSaved by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }
    fun t(value: String) = PawlyI18n.text(value, language)
    fun requestApproximateLocation() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        } ?: return
        try {
            @Suppress("DEPRECATION")
            manager.getLastKnownLocation(provider)?.let { approximateLocation = it.latitude to it.longitude }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                    if (location != null) approximateLocation = location.latitude to location.longitude
                }
            }
        } catch (_: SecurityException) { }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            requestApproximateLocation()
        }
    }
    LaunchedEffect(repository) {
        runCatching { repository?.loadLostPetSubscription() }.getOrNull()?.let { saved ->
            receiveNearbyAlerts = saved.first
            alertRadius = saved.second
            approximateLocation = saved.third
            subscriptionSaved = true
        }
        requestApproximateLocation()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text("  ${t("Volver")}") }
            Spacer(Modifier.weight(1f))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(30.dp)) {
                Text(tr("PAWLY · AYUDA"), modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.Pets, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(30.dp))
                Text(t("He perdido a mi mascota"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                Text(t("Ayuda a encontrarla") + ". Publicaremos un aviso en la categoría de animales perdidos de Comunidad para que otras personas puedan difundirlo.", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        if (repository == null) {
            Text(tr("Para publicar un aviso necesitas conectar Pawly con Firebase e iniciar sesión."), color = MaterialTheme.colorScheme.error)
        }
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(t("Alertas de mascotas perdidas cerca de ti"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(t("Actívalas solo si quieres recibir avisos de tu zona."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Switch(checked = receiveNearbyAlerts, onCheckedChange = { receiveNearbyAlerts = it; subscriptionSaved = false })
                }
                Text(t("Radio de recepción"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 20).forEach { radius ->
                        FilterChip(selected = alertRadius == radius, onClick = { alertRadius = radius; subscriptionSaved = false }, label = { Text("$radius km") })
                    }
                }
                Text(
                    t("Pawly utiliza una ubicación aproximada (redondeada a unos 1 km). No se comparte tu posición exacta. Los avisos solo se envían a personas que hayan activado esta opción y estén dentro del radio."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                OutlinedButton(
                    onClick = {
                        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (!fine && !coarse) locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        else requestApproximateLocation()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.LocationOn, null); Text("  " + t(if (approximateLocation == null) "Compartir ubicación aproximada" else "Actualizar ubicación aproximada")) }
                if (approximateLocation != null) Text(t("Ubicación aproximada lista."), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Button(
                    onClick = {
                        val point = approximateLocation
                        if (point == null) { error = t("Necesitamos tu ubicación aproximada para configurar alertas."); return@Button }
                        busy = true
                        scope.launch {
                            runCatching { repository?.saveLostPetSubscription(receiveNearbyAlerts, alertRadius, point.first, point.second) }
                                .onSuccess { subscriptionSaved = true; error = null }
                                .onFailure { error = it.localizedMessage ?: t("No se pudo guardar la preferencia.") }
                            busy = false
                        }
                    },
                    enabled = repository != null && !busy && approximateLocation != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t(if (subscriptionSaved) "Preferencias guardadas" else "Guardar preferencias de alertas")) }
            }
        }

                OutlinedTextField(petName, { petName = it }, label = { Text(t("Nombre de la mascota")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(breed, { breed = it }, label = { Text(tr("Especie o raza (opcional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(area, { area = it }, label = { Text(t("Zona donde se perdió")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.LocationOn, null) })
        OutlinedTextField(lastSeen, { lastSeen = it }, label = { Text(t("Última vez vista")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(tr("Fecha, hora y lugar aproximados")) })
        OutlinedTextField(description, { description = it }, label = { Text(t("Descripción y señas")) }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text(tr("Color, tamaño, collar, microchip (no publiques el número completo), comportamiento…")) })
        OutlinedTextField(contact, { contact = it }, label = { Text(t("Teléfono o forma de contacto (opcional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CameraAlt, null); Text("  ${t("Añadir fotografía")}")
        }
        photo?.let { AsyncImage(model = it, contentDescription = "Fotografía seleccionada", modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp))) }
        Text(tr("Consejo: indica una zona concreta y una descripción reconocible. Si compartes un teléfono, recuerda que será visible públicamente en la publicación."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                val repo = repository ?: return@Button
                busy = true
                error = null
                scope.launch {
                    try {
                        val imageUrl = photo?.let { repo.uploadImage(it, "post-photos") }.orEmpty()
                        val body = buildString {
                            append("🚨 AVISO DE MASCOTA PERDIDA\n")
                            append("Nombre: ${petName.trim()}\n")
                            if (breed.isNotBlank()) append("Especie/raza: ${breed.trim()}\n")
                            append("Zona: ${area.trim()}\n")
                            if (lastSeen.isNotBlank()) append("Última vez vista: ${lastSeen.trim()}\n")
                            append("\nDescripción: ${description.trim()}")
                            if (contact.isNotBlank()) append("\n\nContacto: ${contact.trim()}")
                            append("\n\nAviso publicado desde Pawly el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                        }
                        val point = approximateLocation
                        if (point == null) {
                            error = t("Activa la ubicación aproximada para enviar avisos a personas cercanas.")
                        } else {
                            repo.publishLostPetAlert(body, imageUrl, petName.trim(), point.first, point.second)
                            onPublished()
                        }
                    } catch (e: Exception) {
                        error = e.localizedMessage ?: "No se ha podido publicar el aviso."
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = repository != null && !busy && approximateLocation != null && petName.isNotBlank() && area.isNotBlank() && description.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(t("Publicar aviso de pérdida"))
        }
    }
}
