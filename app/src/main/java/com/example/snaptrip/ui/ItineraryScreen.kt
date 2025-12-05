package com.example.snaptrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.snaptrip.data.model.PlaceDetail
import com.example.snaptrip.viewmodel.TripViewModel

// IMPORTANTE: Metti qui la tua chiave API per caricare le foto
const val GOOGLE_API_KEY = "MAPS_API_KEY"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit
) {
    // Raccogliamo lo stato
    val tripResult by viewModel.tripResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tripResult?.trip_name ?: "Caricamento...") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            // DIAGNOSI: Controlliamo cosa abbiamo ricevuto
            if (tripResult == null) {
                Text("ERRORE: tripResult è NULL! Riprova.", color = Color.Red)
            } else if (tripResult!!.itinerary.isEmpty()) {
                Text("ERRORE: L'itinerario è VUOTO. Controlla il server.", color = Color.Red)
            } else {
                // SE SIAMO QUI, I DATI CI SONO!
                // Mostriamo il contenuto vero
                val trip = tripResult!!
                val pagerState = rememberPagerState(pageCount = { trip.itinerary.size })

                Column(Modifier.fillMaxSize()) {
                    // Tab Giorni
                    ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                        trip.itinerary.forEachIndexed { index, day ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { /* TODO */ },
                                text = { Text("Giorno ${day.day}") }
                            )
                        }
                    }

                    // Contenuto
                    HorizontalPager(state = pagerState) { page ->
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(trip.itinerary[page].places) { place ->
                                PlaceCard(place, index = trip.itinerary[page].places.indexOf(place))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceCard(place: PlaceDetail, index: Int) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // FOTO (Se esiste reference)
            if (place.photoReference != null) {
                val photoUrl = "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photo_reference=${place.photoReference}&key=$GOOGLE_API_KEY"

                AsyncImage(
                    model = photoUrl,
                    contentDescription = place.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }

            // INFO
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pallino numero tappa
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(place.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(4.dp))
                if (place.rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                        Text(" ${place.rating}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(place.address ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}