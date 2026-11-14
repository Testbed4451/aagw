package com.meilluer.glanceables.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * BroadcastReceiver that fires when a scheduled travel alarm triggers.
 * Shows a Toast message for the two alarm types:
 * - Journey day alarm: fires at midnight on dateOfJourney
 * - Pre-departure alarm: fires 4 hours before departureTime on dateOfJourney
 */
class TravelAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_JOURNEY_DAY = "com.meilluer.glanceables.alarm.JOURNEY_DAY"
        const val ACTION_PRE_DEPARTURE = "com.meilluer.glanceables.alarm.PRE_DEPARTURE"
        const val EXTRA_BOARDING_STATION = "boarding_station"
        const val EXTRA_DEBOARDING_STATION = "deboarding_station"
        const val EXTRA_DEPARTURE_TIME = "departure_time"
        const val EXTRA_TRAIN_NAME = "train_name"
        const val REQUEST_CODE_JOURNEY_DAY = 9001
        const val REQUEST_CODE_PRE_DEPARTURE = 9002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val trainName = intent.getStringExtra(EXTRA_TRAIN_NAME) ?: ""
        val boarding = intent.getStringExtra(EXTRA_BOARDING_STATION) ?: ""
        val deboarding = intent.getStringExtra(EXTRA_DEBOARDING_STATION) ?: ""
        val departureTime = intent.getStringExtra(EXTRA_DEPARTURE_TIME) ?: ""

        val message = when (intent.action) {
            ACTION_JOURNEY_DAY -> {
                buildString {
                    append("🚂 Today is your travel day!")
                    if (trainName.isNotBlank()) append("\n$trainName")
                    if (boarding.isNotBlank() && deboarding.isNotBlank()) {
                        append("\n$boarding → $deboarding")
                    }
                    if (departureTime.isNotBlank()) append(" at $departureTime")
                }
            }
            ACTION_PRE_DEPARTURE -> {
                buildString {
                    append("⏰ Your departure is in 4 hours!")
                    if (trainName.isNotBlank()) append("\n$trainName")
                    if (boarding.isNotBlank()) append("\nBoarding at $boarding")
                    if (departureTime.isNotBlank()) append(" at $departureTime")
                }
            }
            else -> return
        }

        // Toast must be shown on the main thread
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
