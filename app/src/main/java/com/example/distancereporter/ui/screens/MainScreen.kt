package com.example.distancereporter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.distancereporter.data.DistanceUnit
import com.example.distancereporter.data.UserPreferences
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Data class to hold distance samples with timestamps
private data class DistanceSample(val distanceMeters: Double, val timeMillis: Long)

@Composable
fun MainScreen(
    preferences: UserPreferences,
    onPauseToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onNavigateToConfig: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Time state that updates every second
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // Track distance samples for speed calculation (last 60 seconds)
    val distanceSamples = remember { mutableStateListOf<DistanceSample>() }
    var averageSpeed by remember { mutableStateOf(0.0) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                currentTime = LocalTime.now()
                val now = System.currentTimeMillis()

                // Add current distance sample
                distanceSamples.add(DistanceSample(preferences.currentDistanceMeters, now))

                // Remove samples older than 60 seconds (use iterator for safe removal)
                val cutoff = now - 60_000
                val iterator = distanceSamples.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().timeMillis < cutoff) {
                        iterator.remove()
                    }
                }

                // Calculate average speed (units per hour) over the last minute
                if (distanceSamples.size >= 2) {
                    val oldest = distanceSamples.firstOrNull()
                    val newest = distanceSamples.lastOrNull()
                    if (oldest != null && newest != null) {
                        val distanceDeltaMeters = newest.distanceMeters - oldest.distanceMeters
                        val timeDeltaSeconds = (newest.timeMillis - oldest.timeMillis) / 1000.0

                        if (timeDeltaSeconds > 0 && distanceDeltaMeters >= 0) {
                            // Convert to units per hour
                            val distanceDeltaUnits = preferences.unit.convertFromMeters(distanceDeltaMeters)
                            averageSpeed = (distanceDeltaUnits / timeDeltaSeconds) * 3600.0
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently handle any errors to prevent crash
            }

            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header row with speed and time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Speed display
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Speed:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Text(
                    text = "${String.format("%.1f", averageSpeed)} ${preferences.unit.abbreviation}/h",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            // Time display
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Time:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Text(
                    text = currentTime.format(timeFormatter),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Distance display
        DistanceDisplay(
            distance = preferences.currentDistanceInUnits,
            unit = preferences.unit
        )

        Spacer(modifier = Modifier.weight(1f))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Pause button
            ControlButton(
                icon = if (preferences.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                label = if (preferences.isPaused) "Resume" else "Pause",
                isActive = preferences.isPaused,
                onClick = onPauseToggle
            )

            // Mute button
            ControlButton(
                icon = if (preferences.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                label = if (preferences.isMuted) "Unmute" else "Mute",
                isActive = preferences.isMuted,
                onClick = onMuteToggle
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Config button
            OutlinedButton(onClick = onNavigateToConfig) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings")
            }

            // Calendar button
            OutlinedButton(onClick = onNavigateToCalendar) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Calendar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DistanceDisplay(
    distance: Double,
    unit: DistanceUnit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = String.format("%.2f", distance),
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = unit.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isActive)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
