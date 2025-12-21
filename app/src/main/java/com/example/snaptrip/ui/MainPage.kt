package com.example.snaptrip.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snaptrip.R
import com.example.snaptrip.data.model.TripResponse
import androidx.compose.material.icons.filled.DirectionsWalk // For the "Resume" icon
import androidx.compose.material.icons.filled.History       // For "Past Trips"
import androidx.compose.material.icons.filled.Map           // For "Planned"
import androidx.compose.runtime.LaunchedEffect
import com.example.snaptrip.viewmodel.TripViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun MainPage(
    viewModel: TripViewModel,
    userName: String,
    onCreateTrip: () -> Unit,
    onViewPlanned: () -> Unit,
    onViewPast: () -> Unit,
    onResumeActive: (TripResponse) -> Unit,
    onLogout: () -> Unit
) {
    // Observe trips to check if one is active
    val userTrips by viewModel.userTrips.collectAsState()
    val activeTrip = userTrips.find { it.lifecycleStatus == "ACTIVE" }

    // Load trips when entering the screen to ensure we have fresh data
    LaunchedEffect(Unit) {
        viewModel.loadUserTrips()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(40.dp)) // Push the logo down by 40dp (Adjust this value if you want it lower/higher)

        // Header: Logo e nome app in alto
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,  // This centers the Logo and Text horizontally
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.snaptrip_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SnapTrip",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Content: Welcome message
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome $userName",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pulsanti
            MenuButton(
                text = "Create a new Trip",
                icon = Icons.Default.Add,
                onClick = onCreateTrip
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. RESUME ACTIVE TRIP (Visible only if there is an active trip)
            if (activeTrip != null) {
                Button(
                    onClick = { onResumeActive(activeTrip) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsWalk, null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Resume Trip", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(activeTrip.trip_name, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. SPLIT BUTTONS: PLANNED & PAST
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Planned Button
                Button(
                    onClick = onViewPlanned,
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Map, null)
                        Text("Planned", fontSize = 14.sp)
                    }
                }

                // Past Button
                Button(
                    onClick = onViewPast,
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null)
                        Text("Past", fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            TextButton(onClick = onLogout) {
                Text("Logout")
            }
        }
    }
}

@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary, // Usa il colore primario del tema
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}