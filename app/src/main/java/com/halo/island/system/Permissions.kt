package com.halo.island.system

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.halo.island.core.PermissionState

fun Context.permissionState() =
    PermissionState(
        Settings.canDrawOverlays(this),
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName),
        NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED),
    )

fun Context.safeLaunch(intent: Intent): Boolean =
    runCatching {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }
        .getOrDefault(false)

/** Only call from an explicit tap. This opts the sender into its existing public BAL privileges. */
fun Context.sendUserPendingIntent(pending: android.app.PendingIntent): Boolean =
    runCatching {
            val options =
                if (Build.VERSION.SDK_INT >= 34)
                    android.app.ActivityOptions.makeBasic()
                        .apply {
                            setPendingIntentBackgroundActivityStartMode(
                                if (Build.VERSION.SDK_INT >= 36)
                                    android.app.ActivityOptions
                                        .MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                                else
                                    android.app.ActivityOptions
                                        .MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                        }
                        .toBundle()
                else null
            pending.send(this, 0, null, null, null, null, options)
            true
        }
        .getOrDefault(false)
