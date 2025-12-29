package com.example.snaptrip.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.snaptrip.data.model.JournalEntry
import com.example.snaptrip.viewmodel.TripViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.res.painterResource
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.ui.graphics.vector.ImageVector

//import androidx.activity.result.contract.ActivityResultContracts
//import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelJournalScreen(
    viewModel: TripViewModel,
    tripId: String,
    tripName: String,
    onBack: () -> Unit,
    onViewItinerary: () -> Unit
) {

    // 1. Observe the selected trip to get access to its itinerary/coordinates
    val selectedTrip by viewModel.tripResult.collectAsState()
    val isTripActive = selectedTrip?.lifecycleStatus == "ACTIVE"

    val journalEntries by viewModel.journalEntries.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val weatherTemp by viewModel.weatherTemp.collectAsState()

    val weatherInfo by viewModel.weatherInfo.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }

    val context = LocalContext.current

    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // 1. Setup Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            //viewModel.resetStepCounter()
            // Now We do NOT reset steps. The sensor will simply pick up where it left off.
        }
    }

    // Show a Toast when an error occurs
    LaunchedEffect(error) {
        if (error != null) {
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Trigger Weather Fetch
    LaunchedEffect(tripId) {
        viewModel.loadJournal(tripId)
        //viewModel.resetStepCounter()

        // LOGIC: Find the first valid coordinate from the itinerary
        val targetPlace = selectedTrip?.itinerary
            ?.firstOrNull() // Get first day
            ?.places
            ?.firstOrNull() // Get first place of that day

        if (targetPlace != null) {
            // Fetch weather for the specific trip location
            viewModel.fetchRealTimeWeather(targetPlace.lat, targetPlace.lng)
        } else {
            // Fallback (Optional): If trip has no places, you could default to a known city
            // or try to use the device GPS if you implemented the location permission check here.
        }

        // STEP COUNTER PERMISSION LOGIC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10+, check for ACTIVITY_RECOGNITION
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request permission
                permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                // Permission already granted
                //viewModel.resetStepCounter()
            }
        } else {
            // Older Android versions don't need runtime permission
            //viewModel.resetStepCounter()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedEntry == null) {
                FloatingActionButton(
                    onClick = {
                        // NEW LOGIC: Check connection before opening dialog
                        if (isInternetAvailable(context)) {
                            showAddDialog = true
                        } else {
                            // Show feedback if offline
                            Toast.makeText(context, "You need an internet connection to add memories.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F4F8)) // Sfondo chiaro
        ) {
            // Sfondo Grafico (Onde)
            Canvas(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, size.height * 0.7f)
                    quadraticBezierTo(
                        size.width * 0.5f, size.height * 1.0f,
                        size.width, size.height * 0.6f
                    )
                    lineTo(size.width, 0f)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF4A90E2), Color(0xFF00BFA5))
                    )
                )
            }

            Column(modifier = Modifier.padding(padding)) {
                // HEADER: Titolo e Back
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Side: Back Arrow and Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(
                            text = tripName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // Right Side: END TRIP BUTTON
                    // Only visible if the trip is currently ACTIVE
                    if (isTripActive) {
                        Button(
                            onClick = {
                                // ERROR WAS HERE: viewModel.endTrip(tripId)

                                // FIX: Pass the 'selectedTrip' object safely
                                selectedTrip?.let { trip ->
                                    viewModel.endTrip(trip)
                                }
                                onBack() // Return to home after ending
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("End Trip", fontSize = 12.sp)
                        }
                    }
                }

                // WIDGETS: Meteo e Passi
                if(isTripActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WeatherWidget(weatherInfo = weatherInfo)
                        StepsWidget(steps = steps)
                    }
                } else {
                    // Placeholder for Inactive Trips
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "Start this trip from the list to unlock Weather and Step Counter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Itinerary button
                Button(
                    onClick = onViewItinerary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp), // Softer corners
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp, // Higher shadow
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("View Trip Itinerary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // GRIGLIA DI IMMAGINI
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), // 3 colonne
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(journalEntries, key = { it.firestoreId!! }) { entry ->
                        JournalGridItem(entry) {
                            selectedEntry = entry
                        }
                    }
                }
            }
        }

        // Immagine a schermo intero (se selezionata)
        AnimatedVisibility(
            visible = selectedEntry != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (selectedEntry != null) {
                FullScreenImage(
                    entry = selectedEntry!!,
                    onDismiss = { selectedEntry = null },
                    onEdit = { 
                        // Qui potresti riaprire il dialog di aggiunta precompilato per la modifica
                        // Per ora chiudiamo solo la visualizzazione full screen
                        // (La modifica completa richiederebbe di passare l'entry al dialog)
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddJournalEntryDialog(
            tripName = tripName,
            onDismiss = { showAddDialog = false },
            onConfirm = { text, bitmap ->
                viewModel.addJournalEntry(tripId, text, bitmap)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun WeatherWidget(weatherInfo: com.example.snaptrip.data.model.WeatherInfo?) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        modifier = Modifier.size(140.dp, 100.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Get the data
            val temp = weatherInfo?.temp ?: 0
            val iconCode = weatherInfo?.iconCode
            val description = weatherInfo?.description ?: "Offline"

            // 2. Determine the correct vector icon (for offline/loading)
            var loadFailed by remember { mutableStateOf(false) }

            // 3. Build the online URL
            val iconUrl = if (!iconCode.isNullOrEmpty())
                "https://openweathermap.org/img/wn/${iconCode}@2x.png"
            else null

            if (iconUrl != null) {
                // Try to load online image (or from cache)
                AsyncImage(
                    model = iconUrl,
                    contentDescription = description,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Fit,
                    // IMPORTANT: If offline and not in cache, show the correct Vector!
                    onError = {
                        // If offline, this triggers immediately
                        loadFailed = true
                    }
                )
            } else {
                // Completely offline fallback (no code available)
                // Get the specific icon for the code (e.g., "10d" -> WaterDrop)
                val vectorIcon = getWeatherIconVector(iconCode)

                Icon(
                    imageVector = vectorIcon,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            // 4. Text Info
            Text("$temp°C", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            //Text(text = "Code: ${iconCode ?: "null"}", fontSize = 8.sp, color = Color.Red) // Uncomment to debug
            Text(
                description.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1
            )
        }
    }
}

// Translate OpenWeatherMap codes to Android Icons for offline use
fun getWeatherIconVector(code: String?): ImageVector {
    return when (code) {
        "01d", "01n" -> Icons.Default.WbSunny        // Clear sky
        "02d", "02n" -> Icons.Default.WbCloudy       // Few clouds
        "03d", "03n" -> Icons.Default.Cloud          // Scattered clouds
        "04d", "04n" -> Icons.Outlined.Cloud         // Broken clouds
        "09d", "09n" -> Icons.Default.Umbrella       // Showers
        "10d", "10n" -> Icons.Default.WaterDrop      // Rain
        "11d", "11n" -> Icons.Default.Thunderstorm   // Thunderstorm
        "13d", "13n" -> Icons.Default.AcUnit         // Snow
        "50d", "50n" -> Icons.Default.Waves          // Mist
        else -> Icons.Default.WbSunny                // Default fallback
    }
}

@Composable
fun StepsWidget(steps: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        modifier = Modifier.size(140.dp, 100.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.size(70.dp)) {
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx())
                )
                drawArc(
                    color = Color(0xFFFF4081),
                    startAngle = -90f,
                    sweepAngle = (steps / 10000f) * 360f, // Goal 10k passi
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx())
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$steps", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Steps", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun JournalGridItem(entry: JournalEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f) // Immagini quadrate
            .clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (entry.photoBase64 != null) {
                // SMART CHECK
                val isUrl = entry.photoBase64!!.startsWith("http")

                AsyncImage(
                    model = if (isUrl) {
                        entry.photoBase64
                    } else {
                        // Fallback for old Base64 data
                        remember(entry.photoBase64) {
                            try {
                                val imageBytes = Base64.decode(entry.photoBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            } catch (e: Exception) { null }
                        }
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),

                    // ADD THIS: Visual Feedback for loading/errors
                    error = painterResource(android.R.drawable.ic_menu_report_image), // Built-in android error icon
                    placeholder = painterResource(android.R.drawable.ic_menu_gallery) // Built-in placeholder
                )
            } else {
                // Se non c'è foto, mostra la nota come testo
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    Text(
                        entry.text, 
                        modifier = Modifier.padding(8.dp),
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenImage(entry: JournalEntry, onDismiss: () -> Unit, onEdit: () -> Unit) {
    // Box che copre tutto lo schermo
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            // Questo clickable intercetta i click sullo sfondo per chiudere
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Nessun effetto ripple sul click sfondo
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                // Impediamo che i click sul contenuto chiudano la schermata
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Do nothing */ },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val photoString = entry.photoBase64

            // --- UPDATED IMAGE LOGIC START ---
            if (photoString != null) {
                // 1. Common modifier for both Image types
                val imageModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false) // Occupa spazio ma non forza se non serve
                    .clip(RoundedCornerShape(8.dp))

                // 2. Smart Check: URL vs Base64
                if (photoString.startsWith("http")) {
                    // CASE A: It is a Firebase URL
                    AsyncImage(
                        model = photoString,
                        contentDescription = null,
                        modifier = imageModifier,
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // CASE B: It is Legacy Base64
                    val bitmap = remember(photoString) {
                        try {
                            val imageBytes = Base64.decode(photoString, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        } catch (e: Exception) { null }
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = imageModifier,
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        // Fallback if decoding fails
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }
            } else {
                // Fallback if photo is null
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(120.dp)
                )
            }
            // --- UPDATED IMAGE LOGIC END ---

            Spacer(Modifier.height(24.dp))

            // Testo scorrevole se lungo
            Text(
                entry.text,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.date))
            Text(dateStr, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onEdit,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Edit Note")
            }
        }
    }
}

@Composable
fun AddJournalEntryDialog(tripName: String, onDismiss: () -> Unit, onConfirm: (String, Bitmap?) -> Unit) {
    var text by remember { mutableStateOf("") }

    //var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // STATE 1: The raw photo from Camera/Gallery
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // STATE 2: The photo we show to the user (possibly with effects)
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current

    var isPostcardMode by remember { mutableStateOf(false) }

    // NUOVO: Stato per i tag rilevati
    var detectedTags by remember { mutableStateOf<List<String>>(emptyList()) }


    // 1. Quando cambia l'immagine originale, analizzala con ML Kit
    LaunchedEffect(originalBitmap) {
        if (originalBitmap != null) {
            // Esegue l'analisi in background
            detectedTags = com.example.snaptrip.utils.ImageLabelingHelper.getLabels(originalBitmap!!)
        }
    }
    // REACTIVE LOGIC: Whenever 'isPostcardMode' OR 'originalBitmap' changes, update 'displayBitmap'
    // 2. Quando cambia lo switch O l'immagine O i tag, aggiorna l'anteprima
    LaunchedEffect(isPostcardMode, originalBitmap, detectedTags) {
        if (originalBitmap != null) {
            displayBitmap = if (isPostcardMode) {
                // Genera cartolina con i tag!
                com.example.snaptrip.utils.PostcardUtils.generatePostcard(
                    originalBitmap!!,
                    tripName,
                    detectedTags // Passa i tag qui
                )
            } else {
                originalBitmap
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            var bitmap = if (Build.VERSION.SDK_INT < 28) MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            else ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))

            originalBitmap = bitmap
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            originalBitmap = bitmap
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Memory") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Write something...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Spacer(Modifier.height(16.dp))
                
                if (displayBitmap != null) {
                    Image(
                        bitmap = displayBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // NEW: Postcard Switch UI
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Switch(
                        checked = isPostcardMode,
                        onCheckedChange = { isPostcardMode = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Postcard Mode \uD83D\uDDBC\uFE0F") // Frame icon
                }
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Text("Gallery")
                    }
                    TextButton(onClick = { cameraLauncher.launch() }) {
                        Icon(Icons.Default.CameraAlt, null)
                        Text("Camera")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text, displayBitmap) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}