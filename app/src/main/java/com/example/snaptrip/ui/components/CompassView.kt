package com.example.snaptrip.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import com.example.snaptrip.utils.OrientationSensor
import com.example.snaptrip.utils.calculateBearing
import com.google.android.gms.location.LocationServices

@Composable
fun CompassDialog(
    targetLat: Double,
    targetLon: Double,
    targetName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember { OrientationSensor(context) }
    val azimuth by sensorManager.sensorData.collectAsState(initial = 0f)

    // STATO PER LA POSIZIONE GPS REALE
    var myLocation by remember { mutableStateOf<Location?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }

    // RECUPERO POSIZIONE (GPS)
    LaunchedEffect(Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        // Controllo permessi runtime (obbligatorio per Android moderni)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    myLocation = location
                } else {
                    locationError = "GPS attivo ma nessuna posizione recente trovata."
                }
            }
        } else {
            locationError = "Permesso GPS mancante! Vai nelle impostazioni."
            // Nota: In un'app reale qui dovresti lanciare la richiesta permessi (PermissionLauncher)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Navigazione verso", fontSize = 14.sp, color = Color.Gray)
                Text(targetName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)

                Spacer(modifier = Modifier.height(30.dp))

                if (myLocation == null) {
                    // STATO DI CARICAMENTO / ERRORE
                    if (locationError != null) {
                        Text(locationError!!, color = Color.Red, fontSize = 12.sp)
                    } else {
                        CircularProgressIndicator()
                        Text("Cercando segnale GPS...", fontSize = 12.sp, modifier = Modifier.padding(top=8.dp))
                    }
                } else {
                    // CALCOLO REALE CON GPS
                    val bearing = calculateBearing(
                        myLocation!!.latitude,
                        myLocation!!.longitude,
                        targetLat,
                        targetLon
                    )

                    // Rotazione = Angolo Target - Angolo Telefono
                    val rotation = bearing - azimuth

                    // --- 2D GRAPHICS AREA ---
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Quadrante
                            drawCircle(color = Color.LightGray, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10f))

                            // Nord (fisso rispetto al mondo, ruota rispetto allo schermo)
                            rotate(-azimuth) {
                                drawLine(Color.Red, start = center, end = Offset(center.x, center.y - 90), strokeWidth = 8f)
                                // Testo "N" (semplificato con un pallino rosso per ora)
                                drawCircle(Color.Red, radius = 5f, center = Offset(center.x, center.y - 100))
                            }

                            // Freccia Destinazione
                            rotate(rotation) {
                                val path = Path().apply {
                                    moveTo(center.x, center.y - 70f)
                                    lineTo(center.x + 25f, center.y + 20f)
                                    lineTo(center.x, center.y)
                                    lineTo(center.x - 25f, center.y + 20f)
                                    close()
                                }
                                drawPath(path, color = Color(0xFF6200EE))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Dist: ${"%.1f".format(myLocation!!.distanceTo(Location("").apply { latitude=targetLat; longitude=targetLon }) / 1000)} km",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onDismiss) {
                    Text("Chiudi")
                }
            }
        }
    }
}