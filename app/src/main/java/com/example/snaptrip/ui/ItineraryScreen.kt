package com.example.snaptrip.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.snaptrip.BuildConfig
import com.example.snaptrip.data.model.PlaceDetail
import com.example.snaptrip.ui.components.CompassDialog // Assicurati che questo import esista
import com.example.snaptrip.viewmodel.TripViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    viewModel: TripViewModel,
    onBack: () -> Unit
) {
    val tripResult by viewModel.tripResult.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val error by viewModel.error.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isTripActive = tripResult?.lifecycleStatus == "ACTIVE"

    var isEditing by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var moveSourceDayIndex by remember { mutableIntStateOf(-1) }
    var moveSourcePlaceIndex by remember { mutableIntStateOf(-1) }

    // MODIFICA 1: Usiamo PlaceDetail perché è quello che usa la tua lista
    var selectedStopForCompass by remember { mutableStateOf<PlaceDetail?>(null) }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surface
        )
    )

    // MODIFICA 2: Mostra il Dialog se una tappa è selezionata
    if (selectedStopForCompass != null) {
        // Assumo che PlaceDetail abbia latitude e longitude.
        // Se si chiamano "lat" e "lng", correggi qui sotto.
        CompassDialog(
            targetLat = selectedStopForCompass!!.lat,
            targetLon = selectedStopForCompass!!.lng,
            targetName = selectedStopForCompass!!.name,
            onDismiss = { selectedStopForCompass = null }
        )
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            Toast.makeText(context, "Trip saved successfully!", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    if (showMoveDialog && tripResult != null) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move to another day") },
            text = {
                Column {
                    tripResult!!.itinerary.forEachIndexed { index, day ->
                        if (index != moveSourceDayIndex) {
                            TextButton(
                                onClick = {
                                    viewModel.movePlaceToDay(moveSourceDayIndex, moveSourcePlaceIndex, index)
                                    showMoveDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Day ${day.day}")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        tripResult?.trip_name?.uppercase() ?: "YOUR TRIP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { isEditing = !isEditing }) {
                        Icon(
                            if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isEditing) "Done" else "Edit")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (!isEditing) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.saveCurrentTrip() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("SAVE TRIP", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
        ) {
            if (tripResult == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val trip = tripResult!!
                val pagerState = rememberPagerState(pageCount = { trip.itinerary.size })

                Column(Modifier.fillMaxSize()) {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 16.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {}
                    ) {
                        trip.itinerary.forEachIndexed { index, _ ->
                            val isSelected = pagerState.currentPage == index
                            Tab(
                                selected = isSelected,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    tonalElevation = if (isSelected) 4.dp else 0.dp
                                ) {
                                    Text(
                                        text = "Day ${index + 1}",
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                        val places = trip.itinerary[page].places

                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(places) { index, place ->
                                if (isEditing) {
                                    EditModeItem(
                                        place = place,
                                        onMoveUp = { viewModel.movePlaceUp(page, index) },
                                        onMoveDown = { viewModel.movePlaceDown(page, index) },
                                        onRemove = { viewModel.removePlace(page, index) },
                                        onChangeDay = {
                                            moveSourceDayIndex = page
                                            moveSourcePlaceIndex = index
                                            showMoveDialog = true
                                        }
                                    )
                                } else {
                                    // MODIFICA 3: Passiamo la callback per aprire la bussola
                                    EnhancedTimelineItem(
                                        place = place,
                                        index = index,
                                        isLast = index == places.lastIndex,
                                        onNavigateClick = {
                                            if (isTripActive) {
                                                selectedStopForCompass = place
                                            } else {
                                                Toast.makeText(context, "Start the trip to use Navigation", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditModeItem(
    place: PlaceDetail,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onChangeDay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up")
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down")
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, fontWeight = FontWeight.Bold)
                TextButton(onClick = onChangeDay, contentPadding = PaddingValues(0.dp)) {
                    Text("Move to another day", fontSize = 12.sp)
                }
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// MODIFICA 4: Aggiunto parametro onNavigateClick
@Composable
fun EnhancedTimelineItem(
    place: PlaceDetail,
    index: Int,
    isLast: Boolean,
    onNavigateClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = if (index == 0) BorderStroke(2.dp, MaterialTheme.colorScheme.primaryContainer) else null
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            if (!isLast) {
                Canvas(modifier = Modifier
                    .weight(1f)
                    .width(2.dp)
                ) {
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.6f),
                        start = Offset(center.x, 8f),
                        end = Offset(center.x, size.height - 8f),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Card(
            modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                if (place.photoReference != null) {
                    val photoUrl = "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photo_reference=${place.photoReference}&key=${BuildConfig.MAPS_API_KEY}"

                    AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    if (place.rating != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                            Text(" ${place.rating}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    // MODIFICA 5: Indirizzo Cliccabile che apre la Bussola
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { onNavigateClick() } // CLICCA QUI PER BUSSOLA
                            .padding(4.dp), // Aumenta area cliccabile
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cambiata icona in Explore per suggerire azione
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = "Navigate",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            place.address ?: "Navigate to location",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, // Colore Primary per indicare link
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}