package com.example.distancereporter.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.distancereporter.DistanceReporterApp
import com.example.distancereporter.MainActivity
import com.example.distancereporter.R
import com.example.distancereporter.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.*

class LocationTrackingService : Service(), TextToSpeech.OnInitListener {

    private val binder = LocalBinder()
    private lateinit var locationManager: LocationManager
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var database: DistanceDatabase
    private lateinit var textToSpeech: TextToSpeech

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0
    private var lastReportedDistance: Double = 0.0
    private var isTtsReady = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocationUpdate(location)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        preferencesRepository = UserPreferencesRepository(this)
        database = DistanceDatabase.getDatabase(this)
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        initializeLastReportedDistance()
        startLocationUpdates()
        return START_STICKY
    }

    private fun initializeLastReportedDistance() {
        serviceScope.launch {
            val preferences = preferencesRepository.userPreferences.first()
            val currentDistanceInUnits = preferences.unit.convertFromMeters(preferences.currentDistanceMeters)
            lastReportedDistance = currentDistanceInUnits
            Log.d(TAG, "Initialized lastReportedDistance to $lastReportedDistance ${preferences.unit.name}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS language not supported, trying English")
                textToSpeech.setLanguage(Locale.US)
            }
            isTtsReady = true
            Log.d(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DistanceReporterApp.TRACKING_CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startLocationUpdates() {
        try {
            // Use GPS provider for best accuracy
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_INTERVAL_MS,
                LOCATION_UPDATE_MIN_DISTANCE_M,
                locationListener
            )

            // Also use network provider as fallback
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                LOCATION_UPDATE_INTERVAL_MS,
                LOCATION_UPDATE_MIN_DISTANCE_M,
                locationListener
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        serviceScope.launch {
            val preferences = preferencesRepository.userPreferences.first()

            if (preferences.isPaused) {
                lastLocation = location
                lastLocationTime = System.currentTimeMillis()
                return@launch
            }

            // Filter out inaccurate readings
            if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_THRESHOLD_M) {
                Log.d(TAG, "Ignoring inaccurate location: accuracy=${location.accuracy}m")
                return@launch
            }

            val previousLocation = lastLocation
            val previousTime = lastLocationTime
            lastLocation = location
            lastLocationTime = System.currentTimeMillis()

            if (previousLocation != null && previousTime > 0) {
                val distanceIncrementMeters = previousLocation.distanceTo(location).toDouble()
                val timeElapsedSeconds = (lastLocationTime - previousTime) / 1000.0

                // Calculate speed in m/s
                val speed = if (timeElapsedSeconds > 0) distanceIncrementMeters / timeElapsedSeconds else 0.0

                // Filter out GPS jitter and unreasonable movements
                // - Must move more than minimum threshold
                // - Must not teleport (max threshold)
                // - Speed must be reasonable (< 50 m/s = 180 km/h)
                // - Distance must exceed GPS accuracy uncertainty
                val accuracyThreshold = if (location.hasAccuracy()) location.accuracy.toDouble() else MIN_DISTANCE_THRESHOLD_M
                val effectiveMinThreshold = maxOf(MIN_DISTANCE_THRESHOLD_M, accuracyThreshold * 0.5)

                if (distanceIncrementMeters > effectiveMinThreshold &&
                    distanceIncrementMeters < MAX_DISTANCE_THRESHOLD_M &&
                    speed < MAX_REASONABLE_SPEED_MS
                ) {
                    val newTotalMeters = preferences.currentDistanceMeters + distanceIncrementMeters
                    preferencesRepository.updateCurrentDistance(newTotalMeters)

                    // Check if we should announce distance
                    checkAndAnnounceDistance(newTotalMeters, preferences)

                    // Save to daily total
                    saveDailyDistance(newTotalMeters)

                    Log.d(TAG, "Distance added: ${String.format("%.1f", distanceIncrementMeters)}m, " +
                            "speed: ${String.format("%.1f", speed * 3.6)}km/h, " +
                            "total: ${String.format("%.0f", newTotalMeters)}m")
                } else {
                    Log.d(TAG, "Filtered out: dist=${String.format("%.1f", distanceIncrementMeters)}m, " +
                            "speed=${String.format("%.1f", speed * 3.6)}km/h, " +
                            "threshold=${String.format("%.1f", effectiveMinThreshold)}m")
                }
            }
        }
    }

    private suspend fun checkAndAnnounceDistance(
        currentDistanceMeters: Double,
        preferences: UserPreferences
    ) {
        if (preferences.isMuted) {
            Log.d(TAG, "Skipping announcement - muted")
            return
        }
        if (!isTtsReady) {
            Log.d(TAG, "Skipping announcement - TTS not ready")
            return
        }

        val currentDistanceInUnits = preferences.unit.convertFromMeters(currentDistanceMeters)
        val intervalInUnits = preferences.intervalInUnits

        // Calculate how many intervals have been completed
        val intervalsCompleted = (currentDistanceInUnits / intervalInUnits).toInt()
        val lastReportedIntervals = (lastReportedDistance / intervalInUnits).toInt()

        Log.d(TAG, "Distance check: current=$currentDistanceInUnits, interval=$intervalInUnits, " +
                "intervalsCompleted=$intervalsCompleted, lastReportedIntervals=$lastReportedIntervals")

        if (intervalsCompleted > lastReportedIntervals) {
            val distanceToAnnounce = intervalsCompleted * intervalInUnits
            Log.d(TAG, "Announcing distance: $distanceToAnnounce ${preferences.unit.name}")
            announceDistance(distanceToAnnounce, preferences.unit, preferences.volume)
            lastReportedDistance = currentDistanceInUnits
        }
    }

    private fun announceDistance(distance: Double, unit: DistanceUnit, volume: Float) {
        val formattedDistance = formatDistanceForSpeech(distance)
        val text = "$formattedDistance ${unit.spokenName}"

        // Set volume (audio ducking allows other audio to continue)
        textToSpeech.setSpeechRate(1.0f)

        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }

        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "distance_announcement")
    }

    private fun formatDistanceForSpeech(distance: Double): String {
        val intPart = distance.toInt()
        val decimalPart = ((distance - intPart) * 10).toInt()

        return if (decimalPart == 0) {
            numberToWords(intPart)
        } else {
            "${numberToWords(intPart)} point ${numberToWords(decimalPart)}"
        }
    }

    private fun numberToWords(number: Int): String {
        val ones = arrayOf(
            "", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen"
        )
        val tens = arrayOf(
            "", "", "twenty", "thirty", "forty", "fifty",
            "sixty", "seventy", "eighty", "ninety"
        )

        return when {
            number < 20 -> ones[number]
            number < 100 -> {
                val ten = tens[number / 10]
                val one = ones[number % 10]
                if (one.isEmpty()) ten else "$ten $one"
            }
            else -> number.toString()
        }
    }

    private suspend fun saveDailyDistance(totalMeters: Double) {
        val today = LocalDate.now()
        database.dailyDistanceDao().upsertDistance(
            DailyDistance(date = today, distanceMeters = totalMeters)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
        textToSpeech.shutdown()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_ID = 1

        // Location update settings - balance between accuracy and battery
        private const val LOCATION_UPDATE_INTERVAL_MS = 5000L // 5 seconds
        private const val LOCATION_UPDATE_MIN_DISTANCE_M = 10f // 10 meters

        // Distance thresholds to filter GPS noise
        private const val MIN_DISTANCE_THRESHOLD_M = 5.0 // Ignore tiny movements (GPS drift)
        private const val MAX_DISTANCE_THRESHOLD_M = 100.0 // Ignore teleportation (GPS errors)
        private const val MAX_ACCURACY_THRESHOLD_M = 50f // Ignore readings with poor accuracy
        private const val MAX_REASONABLE_SPEED_MS = 50.0 // 50 m/s = 180 km/h max reasonable speed

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
