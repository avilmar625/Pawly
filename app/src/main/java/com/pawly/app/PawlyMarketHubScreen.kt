package com.pawly.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PawlyMarketHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var category by remember { mutableStateOf("Todo") }
    var search by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    fun openExternal(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { feedback = "No se ha podido abrir el enlace." }
    }
    fun searchMarket() {
        val query = search.trim()
        if (query.isBlank()) feedback = "Escribe qué producto quieres buscar."
        else openExternal("https://www.google.com/search?tbm=shop&q=" + Uri.encode(query))
    }

    val products = listOf(
        Triple("Pienso y alimentación", "Nutrición diaria y dietas especiales", "pienso para perros"),
        Triple("Comida húmeda", "Texturas y formatos para cada etapa", "comida húmeda perros"),
        Triple("Snacks y premios", "Premios para entrenamiento y disfrute", "snacks naturales perros"),
        Triple("Juguetes y enriquecimiento", "Olfato, masticación y juego", "juguetes enriquecimiento perros"),
        Triple("Paseo y viaje", "Arneses, correas y accesorios", "arneses correas perros"),
        Triple("Higiene y cuidado", "Champús, cepillos y limpieza", "higiene perros"),
        Triple("Camas y descanso", "Camas, mantas y transportines", "camas perros")
    )
    val categories = listOf("Todo", "Pienso", "Comida húmeda", "Snacks", "Juguetes", "Paseo", "Higiene", "Descanso")
    val visibleProducts = products.filter { product ->
        category == "Todo" || when (category) {
            "Pienso" -> product.first.startsWith("Pienso")
            "Comida húmeda" -> product.first.startsWith("Comida")
            "Snacks" -> product.first.startsWith("Snacks")
            "Juguetes" -> product.first.startsWith("Juguetes")
            "Paseo" -> product.first.startsWith("Paseo")
            "Higiene" -> product.first.startsWith("Higiene")
            "Descanso" -> product.first.startsWith("Camas")
            else -> true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Text("  Más")
                }
            }
            Text("Pawly Market", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Productos y accesorios para tu mascota.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("¿Qué estás buscando?") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { searchMarket() }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Buscar productos")
                    }
                }
            )
            TextButton(onClick = { searchMarket() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Buscar en tiendas")
            }
        }
        item {
            PawlyDropdownSelector(
                title = "Categoría",
                selectedLabel = tr(category),
                options = categories.map { it to tr(it) },
                selectedValue = category,
                onSelected = { category = it },
                leadingIcon = Icons.Default.Category
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleProducts.forEach { product ->
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val query = if (search.isBlank()) product.third else search
                                openExternal("https://www.google.com/search?tbm=shop&q=" + Uri.encode(query))
                            }.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.padding(11.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                                Text(product.first, fontWeight = FontWeight.Bold)
                                Text(product.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        item {
            PawlySectionHeading(
                title = "Compra responsable",
                subtitle = "Compara ingredientes, necesidades veterinarias y precio. Los enlaces abren tiendas externas; Pawly no procesa pagos ni pedidos."
            )
        }
        if (feedback != null) item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                Text(feedback!!, modifier = Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}
