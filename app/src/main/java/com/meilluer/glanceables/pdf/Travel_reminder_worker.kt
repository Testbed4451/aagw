package com.meilluer.glanceables.pdf
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlin.jvm.java

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AlarmReceiver", "Travel reminder alarm triggered")

        // TODO: Show your travel reminder Toast here.
    }
}

class Travel_reminder_worker {


    object FutureAlarm {

        private const val REQUEST_CODE = 1001
        private const val ACTION = "com.meilluer.glanceables.FUTURE_ALARM"

        private fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION)

            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun schedule(context: Context, triggerAtMillis: Long?) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val pendingIntent = getPendingIntent(context)

            // No date/time means the alarm is not needed.
            if (triggerAtMillis == null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                return
            }

            // Ignore a target time that has already passed.
            if (triggerAtMillis <= System.currentTimeMillis()) {
                alarmManager.cancel(pendingIntent)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                // Fallback: Android may deliver this later than the target.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }
}