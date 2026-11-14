package com.meilluer.glanceables.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.meilluer.glanceables.data.TravelInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Schedules / cancels travel alarms using AlarmManager.
 *
 * Two alarms are scheduled:
 * 1. **Journey day alarm** — fires at midnight (00:00) on [TravelInfo.dateOfJourney]
 * 2. **Pre-departure alarm** — fires 4 hours before [TravelInfo.departureTime] on [TravelInfo.dateOfJourney]
 *
 * If [TravelInfo.dateOfJourney] or [TravelInfo.departureTime] is null / blank,
 * the corresponding alarm is **not** scheduled.
 */
class TravelAlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "TravelAlarmScheduler"
        private const val FOUR_HOURS_MS = 4 * 60 * 60 * 1000L

        // Supported date formats for dateOfJourney
        private val DATE_FORMATS = listOf(
            SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH),   // 17-Jun-2024
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),    // 18/11/2026
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),    // 2026-12-05
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),    // 17-06-2024
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),   // 17 Jun 2024
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH),  // Jun 17, 2024
        )

        // Supported time formats for departureTime
        private val TIME_FORMATS = listOf(
            SimpleDateFormat("HH:mm", Locale.ENGLISH),         // 07:35, 14:30
            SimpleDateFormat("hh:mm a", Locale.ENGLISH),       // 10:00 AM
            SimpleDateFormat("h:mm a", Locale.ENGLISH),        // 7:35 AM
            SimpleDateFormat("HH:mm:ss", Locale.ENGLISH),      // 07:35:00
        )
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Schedule both alarms for the given [travelInfo].
     * Cancels any previously scheduled travel alarms first.
     */
    fun scheduleAlarms(travelInfo: TravelInfo) {
        // Always cancel old ones before scheduling new ones
        cancelAlarms()

        val dateOfJourney = travelInfo.dateOfJourney
        if (dateOfJourney.isNullOrBlank()) {
            Log.d(TAG, "dateOfJourney is null/blank — no alarms scheduled")
            return
        }

        val journeyDate = parseDate(dateOfJourney)
        if (journeyDate == null) {
            Log.w(TAG, "Could not parse dateOfJourney: $dateOfJourney")
            return
        }

        // 1. Journey-day alarm at midnight
        scheduleJourneyDayAlarm(journeyDate, travelInfo)

        // 2. Pre-departure alarm (4 hours before departure)
        val departureTime = travelInfo.departureTime
        if (!departureTime.isNullOrBlank()) {
            schedulePreDepartureAlarm(journeyDate, departureTime, travelInfo)
        } else {
            Log.d(TAG, "departureTime is null/blank — pre-departure alarm not scheduled")
        }
    }

    /**
     * Cancel both travel alarms.
     */
    fun cancelAlarms() {
        cancelAlarm(TravelAlarmReceiver.REQUEST_CODE_JOURNEY_DAY, TravelAlarmReceiver.ACTION_JOURNEY_DAY)
        cancelAlarm(TravelAlarmReceiver.REQUEST_CODE_PRE_DEPARTURE, TravelAlarmReceiver.ACTION_PRE_DEPARTURE)
        Log.d(TAG, "All travel alarms cancelled")
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun scheduleJourneyDayAlarm(journeyDate: Date, info: TravelInfo) {
        val calendar = Calendar.getInstance().apply {
            time = journeyDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val triggerAtMillis = calendar.timeInMillis
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Journey-day alarm time is in the past — skipping")
            return
        }

        val intent = buildIntent(TravelAlarmReceiver.ACTION_JOURNEY_DAY, info)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TravelAlarmReceiver.REQUEST_CODE_JOURNEY_DAY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarm(triggerAtMillis, pendingIntent)
        Log.d(TAG, "Journey-day alarm scheduled for ${Date(triggerAtMillis)}")
    }

    private fun schedulePreDepartureAlarm(journeyDate: Date, departureTime: String, info: TravelInfo) {
        val timeParts = parseTime(departureTime)
        if (timeParts == null) {
            Log.w(TAG, "Could not parse departureTime: $departureTime")
            return
        }

        val (hour, minute) = timeParts
        val calendar = Calendar.getInstance().apply {
            time = journeyDate
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val triggerAtMillis = calendar.timeInMillis - FOUR_HOURS_MS
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Pre-departure alarm time is in the past — skipping")
            return
        }

        val intent = buildIntent(TravelAlarmReceiver.ACTION_PRE_DEPARTURE, info)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TravelAlarmReceiver.REQUEST_CODE_PRE_DEPARTURE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarm(triggerAtMillis, pendingIntent)
        Log.d(TAG, "Pre-departure alarm scheduled for ${Date(triggerAtMillis)} (4h before $departureTime)")
    }

    private fun buildIntent(action: String, info: TravelInfo): Intent {
        return Intent(context, TravelAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(TravelAlarmReceiver.EXTRA_BOARDING_STATION, info.boardingStation)
            putExtra(TravelAlarmReceiver.EXTRA_DEBOARDING_STATION, info.deboardingStation)
            putExtra(TravelAlarmReceiver.EXTRA_DEPARTURE_TIME, info.departureTime ?: "")
            putExtra(TravelAlarmReceiver.EXTRA_TRAIN_NAME, info.trainName)
        }
    }

    private fun setExactAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: check permission before scheduling exact alarms
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            } else {
                // Fallback: inexact alarm if exact alarm permission is not granted
                Log.w(TAG, "Exact alarm permission not granted — using inexact alarm")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        }
    }

    private fun cancelAlarm(requestCode: Int, action: String) {
        val intent = Intent(context, TravelAlarmReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    // ── Date / Time Parsing ─────────────────────────────────────────────────

    private fun parseDate(dateStr: String): Date? {
        val trimmed = dateStr.trim()
        for (fmt in DATE_FORMATS) {
            try {
                fmt.isLenient = false
                val date = fmt.parse(trimmed)
                if (date != null) return date
            } catch (_: Exception) {
                // try next format
            }
        }
        return null
    }

    /**
     * Parses a time string into (hour, minute) in 24-hour format.
     */
    private fun parseTime(timeStr: String): Pair<Int, Int>? {
        val trimmed = timeStr.trim()
        for (fmt in TIME_FORMATS) {
            try {
                fmt.isLenient = false
                val date = fmt.parse(trimmed) ?: continue
                val cal = Calendar.getInstance().apply { time = date }
                return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            } catch (_: Exception) {
                // try next format
            }
        }
        return null
    }
}
