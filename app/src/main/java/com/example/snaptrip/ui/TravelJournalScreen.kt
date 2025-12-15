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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.WbSunny
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelJournalScreen(
    viewModel: TripViewModel,
    tripId: String,
    tripName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val journalEntries by viewModel.journalEntries.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val weatherTemp by viewModel.weatherTemp.collectAsState()

    // Stati per aggiunta nuova voce
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(tripId) {
        viewModel.loadJournal(tripId)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Entry")
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
                        colors = listOf(
                            Color(0xFF4A90E2),
                            Color(0xFF00BFA5)
                        )
                    )
                )
            }

            Column(modifier = Modifier.padding(padding)) {
                // HEADER: Titolo e Back
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = tripName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // WIDGETS: Meteo e Passi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Card Meteo
                    WeatherWidget(temp = weatherTemp)

                    // Card Passi
                    StepsWidget(steps = steps)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // LISTA DIARIO
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (journalEntries.isEmpty()) {
                        item {
                            Text(
                                "No memories yet. Add your first photo!",
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        items(journalEntries) { entry ->
                            JournalItem(entry)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddJournalEntryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { text, bitmap ->
                viewModel.addJournalEntry(tripId, text, bitmap)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun WeatherWidget(temp: Int) {
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
            Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(4.dp))
            Text("$temp°C", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Sunny", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
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
fun JournalItem(entry: JournalEntry) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (entry.photoBase64 != null) {
                val bitmap = remember(entry.photoBase64) {
                    try {
                        val imageBytes = Base64.decode(entry.photoBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    } catch (e: Exception) { null }
                }
                if (bitmap != null) {
                    AsyncImage(
                        model = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            
            Column(Modifier.padding(16.dp)) {
                val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.date))
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text(entry.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun AddJournalEntryDialog(onDismiss: () -> Unit, onConfirm: (String, Bitmap?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bitmap = if (Build.VERSION.SDK_INT < 28) MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            else ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
            selectedBitmap = bitmap
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) selectedBitmap = bitmap
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
                
                if (selectedBitmap != null) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
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
            Button(onClick = { onConfirm(text, selectedBitmap) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}