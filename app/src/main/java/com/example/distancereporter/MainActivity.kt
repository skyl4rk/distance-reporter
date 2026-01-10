package com.example.distancereporter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.distancereporter.service.LocationTrackingService
import com.example.distancereporter.service.MidnightResetReceiver
import com.example.distancereporter.ui.navigation.DistanceReporterNavHost
import com.example.distancereporter.ui.theme.DistanceReporterTheme

class MainActivity : ComponentActivity() {

    private var hasRequestedBackgroundLocation = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // Try to get background location on API 29+, but only once
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasRequestedBackgroundLocation) {
                hasRequestedBackgroundLocation = true
                requestBackgroundLocationPermission()
            } else {
                startTrackingService()
            }
        }
        // If denied, just don't start the service - user can try again later
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start service regardless of result - foreground tracking still works
        startTrackingService()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue to location permissions regardless of result
        checkLocationPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule midnight reset
        MidnightResetReceiver.scheduleMidnightReset(this)

        // Check permissions and start service if already granted
        initializePermissionsAndService()

        setContent {
            DistanceReporterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DistanceReporterNavHost()
                }
            }
        }
    }

    private fun initializePermissionsAndService() {
        // Check if we already have location permission
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission) {
            // Already have permission, just start the service
            startTrackingService()
            return
        }

        // Need to request permissions
        // On Android 13+, check notification permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        checkLocationPermissions()
    }

    private fun checkLocationPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted) {
            startTrackingService()
        } else {
            // Request location permissions
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!backgroundGranted) {
                backgroundLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            } else {
                startTrackingService()
            }
        } else {
            startTrackingService()
        }
    }

    private fun startTrackingService() {
        LocationTrackingService.start(this)
    }
}
