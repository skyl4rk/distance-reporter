package com.example.distancereporter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule midnight reset alarm
            MidnightResetReceiver.scheduleMidnightReset(context)
            // Restart tracking service after device reboot
            LocationTrackingService.start(context)
        }
    }
}
