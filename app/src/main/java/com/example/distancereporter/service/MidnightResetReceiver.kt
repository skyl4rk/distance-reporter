package com.example.distancereporter.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.distancereporter.data.DailyDistance
import com.example.distancereporter.data.DistanceDatabase
import com.example.distancereporter.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scope = CoroutineScope(Dispatchers.IO)
        val preferencesRepository = UserPreferencesRepository(context)
        val database = DistanceDatabase.getDatabase(context)

        scope.launch {
            // Save yesterday's final distance before resetting
            val preferences = preferencesRepository.userPreferences.first()
            val yesterday = LocalDate.now().minusDays(1)

            database.dailyDistanceDao().upsertDistance(
                DailyDistance(
                    date = yesterday,
                    distanceMeters = preferences.currentDistanceMeters
                )
            )

            // Reset current distance to zero
            preferencesRepository.resetCurrentDistance()
        }

        // Schedule next midnight reset
        scheduleMidnightReset(context)
    }

    companion object {
        private const val REQUEST_CODE = 100

        fun scheduleMidnightReset(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MidnightResetReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Calculate next midnight
            val nextMidnight = LocalDateTime.now()
                .plusDays(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)

            val nextMidnightMillis = nextMidnight
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            // On Android 12+, check if we can schedule exact alarms
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextMidnightMillis,
                        pendingIntent
                    )
                } else {
                    // Fall back to inexact alarm if exact alarm permission not granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextMidnightMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextMidnightMillis,
                    pendingIntent
                )
            }
        }
    }
}
