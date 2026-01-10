package com.example.distancereporter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DistanceReporterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRACKING_CHANNEL_ID,
                "Distance Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when distance tracking is active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TRACKING_CHANNEL_ID = "distance_tracking"
    }
}
