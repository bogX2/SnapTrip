
    package com.example.snaptrip.ui.components

    import androidx.compose.foundation.Canvas
    import androidx.compose.foundation.background
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
    import com.example.snaptrip.utils.OrientationSensor
    import com.example.snaptrip.utils.calculateBearing

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

        // TODO: In futuro sostituire con la vera posizione GPS dell'utente
        // Per ora MOCKIAMO la posizione (es. Roma Centro) per far funzionare la demo
        val myLat = 41.9028
        val myLon = 12.4964

        // Calcoliamo la direzione target
        val bearing = calculateBearing(myLat, myLon, targetLat, targetLon)

        // La rotazione della freccia: (Destinazione - Dove guardo io)
        val rotation = bearing - azimuth

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

                    // --- 2D GRAPHICS AREA (Slide 16) ---
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // 1. Disegna Quadrante Nord
                            drawCircle(color = Color.LightGray, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10f))

                            // Tacche cardinali (N, E, S, W) ruotano con il telefono (simula bussola vera)
                            rotate(-azimuth) {
                                drawLine(Color.Red, start = center, end = Offset(center.x, center.y - 90), strokeWidth = 8f) // N
                            }

                            // 2. Disegna Freccia Direzionale (Punta sempre alla destinazione)
                            rotate(rotation) {
                                val path = Path().apply {
                                    moveTo(center.x, center.y - 70f) // Punta
                                    lineTo(center.x + 25f, center.y + 20f) // Base Dx
                                    lineTo(center.x, center.y)           // Centro
                                    lineTo(center.x - 25f, center.y + 20f) // Base Sx
                                    close()
                                }
                                drawPath(path, color = Color(0xFF6200EE)) // Colore viola/brand
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Ruota il telefono per seguire la freccia!", fontSize = 12.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onDismiss) {
                        Text("Chiudi")
                    }
                }
            }
        }
    }
