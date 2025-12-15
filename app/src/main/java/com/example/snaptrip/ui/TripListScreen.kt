package com.example.snaptrip.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.snaptrip.data.model.TripResponse
import com.example.snaptrip.viewmodel.TripViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit,
    onTripSelected: () -> Unit
) {
    val trips by viewModel.userTrips.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadUserTrips()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Trips") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
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
                items(trips) { trip ->
                    TripItem(
                        trip = trip,
                        onClick = {
                            viewModel.selectTrip(trip)
                            onTripSelected()
                        },
                        onDelete = {
                            viewModel.deleteTrip(trip)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TripItem(
    trip: TripResponse, 
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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
        modifier = Modifier.fillMaxWidth().height(200.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            // Immagine di copertina
            if (trip.coverPhoto != null) {
                val bitmap = remember(trip.coverPhoto) {
                    try {
                        val imageBytes = android.util.Base64.decode(trip.coverPhoto, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    AsyncImage(
                        model = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer))
                }
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer))
            }
            
            // Overlay scuro per testo
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    ))
            )

            // Pulsante Cancella (in alto a destra)
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Trip",
                    tint = Color.White
                )
            }

            // Info Viaggio
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
                Text(
                    text = "${trip.itinerary.size} Days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}