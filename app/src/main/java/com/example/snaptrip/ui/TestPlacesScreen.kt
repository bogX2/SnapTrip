package com.example.snaptrip.ui // 1. Assicurati che il package sia corretto (com.example.snaptrip.ui se il file è nella cartella ui)

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
// 2. AGGIUNGI QUESTI IMPORT FONDAMENTALI PER "by"
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
// ------------------------------------------------
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TestPlacesScreen(
    // 3. Se TestPlacesViewModel è nello stesso package (ui), non serve importarlo.
    // Se ti dà ancora errore qui, prova a premere Alt+Invio su TestPlacesViewModel per importarlo.
    viewModel: TestPlacesViewModel = viewModel()
) {
    // Ora "by" funzionerà grazie agli import di getValue/setValue
    var queryText by remember { mutableStateOf("") }

    // collectAsState() richiede che il ViewModel sia riconosciuto correttamente
    val placePhoto by viewModel.placePhoto.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Test Google Places Photos", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it },
            label = { Text("Nome del posto (es. Colosseo)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.searchPlaceAndGetPhoto(queryText) },
            enabled = !isLoading && queryText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Cerca e Mostra Foto")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = if (statusMessage.startsWith("Errore")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        placePhoto?.let { bitmap ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto del posto",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}