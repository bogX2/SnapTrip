package com.example.snaptrip.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.snaptrip.viewmodel.TripViewModel
// Import Places
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripScreen(
    navController: NavController,
    viewModel: TripViewModel = viewModel()
) {
    val context = LocalContext.current
    val placesClient = remember { Places.createClient(context) }

    // Stati UI Input
    var tripName by remember { mutableStateOf("") }
    var tripDays by remember { mutableStateOf("2") }

    // Ricerca Hotel
    var hotelQuery by remember { mutableStateOf("") }
    var hotelPredictions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearchingHotel by remember { mutableStateOf(false) }

    // Ricerca Luoghi
    var placeQuery by remember { mutableStateOf("") }
    var placePredictions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedPlaces by remember { mutableStateOf<List<String>>(emptyList()) }

    // Stati ViewModel
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val tripResult by viewModel.tripResult.collectAsState()

    // Navigazione dopo successo
    LaunchedEffect(tripResult) {
        if (tripResult != null) {
            navController.navigate("itinerary")
        }
    }

    // Gestione Errori
    LaunchedEffect(error) {
        if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Create New Trip") }) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // 1. Nome e Giorni
                item {
                    OutlinedTextField(
                        value = tripName,
                        onValueChange = { tripName = it },
                        label = { Text("Trip Name (e.g. Rome 2025)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tripDays,
                        onValueChange = { tripDays = it },
                        label = { Text("Days") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 2. Hotel Search
                item {
                    Text("Where are you staying?", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = hotelQuery,
                        onValueChange = { query ->
                            hotelQuery = query
                            isSearchingHotel = true
                            fetchPredictions(placesClient, query) { hotelPredictions = it }
                        },
                        label = { Text("Search Hotel") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if(hotelQuery.isNotEmpty()) IconButton(onClick = { hotelQuery = "" }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    )
                    if (isSearchingHotel && hotelPredictions.isNotEmpty()) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(Modifier.background(Color.White)) {
                                hotelPredictions.take(3).forEach { prediction ->
                                    Text(
                                        text = prediction,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                hotelQuery = prediction
                                                isSearchingHotel = false
                                            }
                                            .padding(16.dp)
                                    )
                                    Divider()
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // 3. Places Search
                item {
                    Text("Places to Visit", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = placeQuery,
                        onValueChange = { query ->
                            placeQuery = query
                            fetchPredictions(placesClient, query) { placePredictions = it }
                        },
                        label = { Text("Add place (e.g. Colosseum)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (placeQuery.isNotBlank()) {
                                    if (!selectedPlaces.contains(placeQuery)) {
                                        selectedPlaces = selectedPlaces + placeQuery
                                    }
                                    placeQuery = ""
                                    placePredictions = emptyList()
                                }
                            }) { Icon(Icons.Default.Add, null) }
                        }
                    )
                    if (placePredictions.isNotEmpty()) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(Modifier.background(Color.White)) {
                                placePredictions.take(3).forEach { prediction ->
                                    Text(
                                        text = prediction,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!selectedPlaces.contains(prediction)) {
                                                    selectedPlaces = selectedPlaces + prediction
                                                }
                                                placeQuery = ""
                                                placePredictions = emptyList()
                                            }
                                            .padding(16.dp)
                                    )
                                    Divider()
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 4. Lista posti selezionati
                items(selectedPlaces) { place ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = place,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = { selectedPlaces = selectedPlaces - place }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // 5. Bottone Invia
                item {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.createTrip(tripName, tripDays, hotelQuery, selectedPlaces) },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("GENERATE ITINERARY") }
                }
            }
        }
    }
}

// Funzione Helper per Places API
fun fetchPredictions(
    client: com.google.android.libraries.places.api.net.PlacesClient,
    query: String,
    onResult: (List<String>) -> Unit
) {
    val token = AutocompleteSessionToken.newInstance()
    val request = FindAutocompletePredictionsRequest.builder()
        .setSessionToken(token)
        .setQuery(query)
        .build()

    client.findAutocompletePredictions(request)
        .addOnSuccessListener { response ->
            onResult(response.autocompletePredictions.map { it.getFullText(null).toString() })
        }
        .addOnFailureListener { onResult(emptyList()) }
}