package com.example.snaptrip.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.snaptrip.data.model.TripResponse
import com.example.snaptrip.viewmodel.TripViewModel
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onTripSelected: () -> Unit,
    onOpenJournal: (TripResponse) -> Unit,
    filterStatus: String // "SAVED" or "COMPLETED"
) {
    // Observe all trips
    val allTrips by viewModel.userTrips.collectAsState()

    // Show only trips matching the requested status
    val trips = remember(allTrips, filterStatus) {
        if (filterStatus == "SAVED") {
            // --- PLANNED TRIPS ---
            allTrips.filter {
                it.lifecycleStatus != "ACTIVE" &&
                        it.lifecycleStatus != "FINISHED" &&
                        it.lifecycleStatus != "COMPLETED"
            }
        } else {
            // --- PAST MEMORIES ---
            if (filterStatus == "FINISHED" || filterStatus == "COMPLETED") {
                allTrips.filter {
                    it.lifecycleStatus == "FINISHED" || it.lifecycleStatus == "COMPLETED"
                }
            } else {
                // Fallback per altri filtri specifici (se ne avrai in futuro)
                allTrips.filter { it.lifecycleStatus == filterStatus }
            }
        }
    }

    val isLoading by viewModel.isLoading.collectAsState()

    // Stato per tenere traccia della card espansa
    var selectedTripId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadUserTrips()
    }


    // OBSERVE THE ERROR STATE
    val errorMessage by viewModel.error.collectAsState()

    // CREATE SNACKBAR HOST STATE
    val snackbarHostState = remember { SnackbarHostState() }

    // TRIGGER SNACKBAR WHEN ERROR OCCURS
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // 1. Show the message
            snackbarHostState.showSnackbar(
                message = errorMessage!!,
                duration = SnackbarDuration.Short
            )
            // 2. IMMEDIATELY clear it from the ViewModel so it doesn't show again
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (filterStatus == "SAVED") "Planned Trips" else "Past Memories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        // ADD SNACKBAR HOST TO SCAFFOLD
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (trips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No trips found. Create one!")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(trips, key = { it.firestoreId ?: it.hashCode() }) { trip ->
                    TripItem(
                        trip = trip,
                        isSelected = selectedTripId == trip.firestoreId,
                        onClick = {
                            // Se clicco su una già selezionata, la chiudo, altrimenti la apro
                            selectedTripId = if (selectedTripId == trip.firestoreId) null else trip.firestoreId
                        },
                        onViewItinerary = {
                            viewModel.selectTrip(trip)
                            onTripSelected()
                        },
                        onOpenJournal = {
                            onOpenJournal(trip)
                        },
                        onDelete = {
                            viewModel.deleteTrip(trip)
                        },
                        onActivate = {
                            viewModel.activateTrip(trip)
                        }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripItem(
    trip: TripResponse,
    isSelected: Boolean,
    onClick: () -> Unit,
    onViewItinerary: () -> Unit,
    onOpenJournal: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Dialogo di conferma eliminazione
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Trip") },
            text = { Text("Are you sure you want to delete '${trip.trip_name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp) // Aggiunto un po' di margine esterno
            .animateContentSize() // Anima il cambio di dimensione
    ) {
        Column {
            Box(Modifier.height(200.dp)) {
                // --- Immagine di Copertina ---
                if (trip.coverPhoto != null) {
                    // Controllo intelligente: è un URL (nuovo) o Base64 (vecchio)?
                    val isUrl = trip.coverPhoto!!.startsWith("http")

                    AsyncImage(
                        model = if (isUrl) {
                            trip.coverPhoto // Carica URL direttamente
                        } else {
                            // Compatibilità passata: Decodifica Base64 al volo
                            remember(trip.coverPhoto) {
                                try {
                                    val imageBytes = android.util.Base64.decode(trip.coverPhoto, android.util.Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                } catch (e: Exception) { null }
                            }
                        },
                        contentDescription = "Trip Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder se non c'è immagine
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer))
                }

                // Overlay scuro per migliorare la leggibilità del testo
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        ))
                )

                // --- BANNER DI STATO (PLANNED / ACTIVE / FINISHED) ---
                // Determina colore e testo in base al lifecycleStatus
                val status = trip.lifecycleStatus ?: "PLANNED"
                val (badgeColor, badgeText) = when (status) {
                    "ACTIVE" -> MaterialTheme.colorScheme.primary to "LIVE TRIP" // Verde/Primary per attivo
                    "FINISHED" -> MaterialTheme.colorScheme.error to "FINISHED"  // Rosso/Error per finito
                    else -> Color.Black.copy(alpha = 0.6f) to "PLANNED"          // Scuro per pianificato
                }

                Surface(
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                    shape = MaterialTheme.shapes.small,
                    color = badgeColor
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Pulsante Cancella (Cestino)
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.3f), shape = MaterialTheme.shapes.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Trip",
                        tint = Color.White
                    )
                }

                // --- Info Viaggio in basso ---
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = trip.trip_name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Durata
                        Text(
                            text = "${trip.itinerary.size} Days",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        // Mostra i passi se presenti (utile per viaggi attivi o finiti)
                        if (trip.steps > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• 👣 ${trip.steps} steps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Green // Evidenzia i passi
                            )
                        }
                    }
                }
            }

            // --- Sezione Espandibile (Pulsanti) ---
            AnimatedVisibility(visible = isSelected) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Button(onClick = onViewItinerary) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("View Itinerary")
                        }
                        Button(onClick = onOpenJournal) {
                            Icon(
                                Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Travel Journal")
                        }
                    }

                    // --- LOGICA PULSANTE START ---
                    // Mostra "START TRIP NOW" SOLO se il viaggio NON è attivo e NON è finito (quindi solo se è PLANNED o DRAFT)
                    if (trip.lifecycleStatus != "ACTIVE" && trip.lifecycleStatus != "FINISHED") {
                        Button(
                            onClick = onActivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("START TRIP NOW")
                        }
                    } else if (trip.lifecycleStatus == "FINISHED") {
                        // Opzionale: Messaggio informativo per i viaggi finiti
                        Text(
                            text = "This trip is completed",
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}