package com.example.distancereporter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.distancereporter.data.DistanceUnit
import com.example.distancereporter.data.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    preferences: UserPreferences,
    onVolumeChange: (Float) -> Unit,
    onUnitChange: (DistanceUnit) -> Unit,
    onIntervalChange: (Double) -> Unit,
    onAnnounceTimeChange: (Boolean) -> Unit,
    onResetDistance: () -> Unit,
    onTestVoice: () -> Unit,
    onExitApp: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Volume control
            VolumeSection(
                volume = preferences.volume,
                onVolumeChange = onVolumeChange,
                onTestVoice = onTestVoice
            )

            Divider()

            // Unit selection
            UnitSection(
                selectedUnit = preferences.unit,
                onUnitChange = onUnitChange
            )

            Divider()

            // Interval selection
            IntervalSection(
                interval = preferences.intervalInUnits,
                unit = preferences.unit,
                onIntervalChange = onIntervalChange
            )

            Divider()

            // Time announcement toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Announce Time Every Quarter Hour",
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = preferences.announceTime,
                    onCheckedChange = onAnnounceTimeChange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reset button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset Distance to Zero")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Exit button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = { showExitDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Exit App")
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Distance") },
            text = { Text("Are you sure you want to reset the distance to zero?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetDistance()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit App") },
            text = { Text("Are you sure you want to exit? Distance tracking will stop.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onExitApp()
                    }
                ) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VolumeSection(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onTestVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Volume",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onTestVoice,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Test Voice")
        }
    }
}

@Composable
private fun UnitSection(
    selectedUnit: DistanceUnit,
    onUnitChange: (DistanceUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Unit of Measurement",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        DistanceUnit.entries.forEach { unit ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = unit == selectedUnit,
                    onClick = { onUnitChange(unit) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = unit.displayName)
            }
        }
    }
}

@Composable
private fun IntervalSection(
    interval: Double,
    unit: DistanceUnit,
    onIntervalChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val intervalOptions = listOf(0.25, 0.5, 1.0, 2.0, 5.0)

    Column(modifier = modifier) {
        Text(
            text = "Report Interval",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        intervalOptions.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = interval == option,
                    onClick = { onIntervalChange(option) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$option ${unit.displayName.lowercase()}"
                )
            }
        }
    }
}
