package com.example.distancereporter.ui.navigation

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.distancereporter.data.DistanceDatabase
import com.example.distancereporter.data.UserPreferences
import com.example.distancereporter.data.UserPreferencesRepository
import com.example.distancereporter.ui.screens.CalendarScreen
import com.example.distancereporter.ui.screens.ConfigScreen
import com.example.distancereporter.ui.screens.MainScreen
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Config : Screen("config")
    object Calendar : Screen("calendar")
}

@Composable
fun DistanceReporterNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val preferencesRepository = remember { UserPreferencesRepository(context) }
    val database = remember { DistanceDatabase.getDatabase(context) }

    val preferences by preferencesRepository.userPreferences.collectAsState(
        initial = UserPreferences()
    )

    // TTS for testing voice
    var ttsReady by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            }
        }
        tts = textToSpeech

        onDispose {
            textToSpeech.shutdown()
        }
    }

    // Get distances for the current and surrounding months
    val today = LocalDate.now()
    val startDate = YearMonth.from(today).minusMonths(6).atDay(1)
    val endDate = YearMonth.from(today).plusMonths(1).atEndOfMonth()

    val dailyDistances by database.dailyDistanceDao()
        .getDistancesInRange(startDate, endDate)
        .collectAsState(initial = emptyList())

    val distancesMap = remember(dailyDistances) {
        dailyDistances.associate { it.date to it.distanceMeters }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                preferences = preferences,
                onPauseToggle = {
                    scope.launch {
                        preferencesRepository.updatePaused(!preferences.isPaused)
                    }
                },
                onMuteToggle = {
                    scope.launch {
                        preferencesRepository.updateMuted(!preferences.isMuted)
                    }
                },
                onNavigateToConfig = {
                    navController.navigate(Screen.Config.route)
                },
                onNavigateToCalendar = {
                    navController.navigate(Screen.Calendar.route)
                }
            )
        }

        composable(Screen.Config.route) {
            ConfigScreen(
                preferences = preferences,
                onVolumeChange = { volume ->
                    scope.launch {
                        preferencesRepository.updateVolume(volume)
                    }
                },
                onUnitChange = { unit ->
                    scope.launch {
                        preferencesRepository.updateUnit(unit)
                    }
                },
                onIntervalChange = { interval ->
                    scope.launch {
                        preferencesRepository.updateInterval(interval)
                    }
                },
                onResetDistance = {
                    scope.launch {
                        preferencesRepository.resetCurrentDistance()
                    }
                },
                onTestVoice = {
                    tts?.let { engine ->
                        engine.speak(
                            "Testing. One point five ${preferences.unit.spokenName}.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "test"
                        )
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Calendar.route) {
            CalendarScreen(
                distances = distancesMap,
                unit = preferences.unit,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
