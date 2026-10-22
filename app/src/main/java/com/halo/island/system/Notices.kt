package com.halo.island.system

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import com.halo.island.MainActivity
import com.halo.island.R
import com.halo.island.halo
import kotlinx.coroutines.launch

object Notices {
    const val SERVICE_ID = 10

    fun channels(c: Context) {
        c.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(
                listOf(
                    NotificationChannel(
                            "halo_control",
                            "HALO controls",
                            NotificationManager.IMPORTANCE_LOW,
                        )
                        .apply {
                            description = "Visible control for your floating island"
                            setShowBadge(false)
                        },
                    NotificationChannel(
                        "halo_timer",
                        "Timer alerts",
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                    NotificationChannel(
                            "halo_timer_silent",
                            "Silent timer alerts",
                            NotificationManager.IMPORTANCE_HIGH,
                        )
                        .apply {
                            setSound(null, null)
                            enableVibration(false)
                        },
                )
            )
    }

    fun open(c: Context) =
        PendingIntent.getActivity(
            c,
            0,
            Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun control(c: Context): Notification {
        val stop =
            PendingIntent.getService(
                c,
                1,
                Intent(c, OverlayService::class.java).setAction(OverlayService.STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Builder(c, "halo_control")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HALO is on")
            .setContentText("Your floating controls · tap to personalize")
            .setContentIntent(open(c))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stop HALO", stop)
            .build()
    }

    fun timerFinished(c: Context, audible: Boolean) {
        val n =
            NotificationCompat.Builder(c, if (audible) "halo_timer" else "halo_timer_silent")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Time is yours.")
                .setContentText("Your HALO timer has finished.")
                .setContentIntent(open(c))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        runCatching { c.getSystemService(NotificationManager::class.java).notify(72, n) }
    }
}

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        val pending = goAsync()
        c.halo.scope.launch {
            try {
                c.halo.timer.alarmFired()
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        if (i?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        c.halo.scope.launch {
            try {
                c.halo.settings.loaded.await()
                // No boot-time foreground service launch. An opt-in notification returns the user
                // to HALO.
                val stored = c.halo.settings
                if (stored.state.value.bootReminder) {
                    val n =
                        NotificationCompat.Builder(c, "halo_control")
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("Your island is a tap away")
                            .setContentText("Open HALO to enable floating controls after restart.")
                            .setContentIntent(Notices.open(c))
                            .setAutoCancel(true)
                            .build()
                    runCatching {
                        c.getSystemService(NotificationManager::class.java).notify(11, n)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
