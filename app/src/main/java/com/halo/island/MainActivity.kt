package com.halo.island

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.halo.island.core.PermissionState
import com.halo.island.system.*
import com.halo.island.ui.*

class MainActivity : ComponentActivity() {
    private var permissions by mutableStateOf(PermissionState(false, false, false))
    private val notificationRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissions = permissionState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloTheme {
                HaloApp(
                    halo,
                    permissions,
                    onOverlayPermission = {
                        safeLaunch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            )
                        )
                    },
                    onListenerPermission = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val detail =
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                    .putExtra(
                                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                        ComponentName(this, HaloNotificationListener::class.java)
                                            .flattenToString(),
                                    )
                            if (!safeLaunch(detail))
                                safeLaunch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } else safeLaunch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onNotificationPermission = {
                        if (permissionState().notifications)
                            safeLaunch(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            )
                        else if (Build.VERSION.SDK_INT >= 33)
                            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onEnable = {
                        if (permissionState().overlay)
                            runCatching { OverlayService.start(this) }
                                .onFailure {
                                    halo.error.value =
                                        "Android blocked startup. Keep HALO visible and try again: ${it.javaClass.simpleName}"
                                }
                        else
                            halo.error.value =
                                "Allow display over other apps first, then tap Enable HALO."
                    },
                    onDisable = {
                        startService(
                            Intent(this, OverlayService::class.java).setAction(OverlayService.STOP)
                        )
                    },
                    onBatterySettings = {
                        if (
                            !safeLaunch(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            )
                        )
                            safeLaunch(Intent(Settings.ACTION_SETTINGS))
                    },
                    onAppSettings = {
                        safeLaunch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName"),
                            )
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissions = permissionState()
        if (permissions.listener) halo.media.connect() else halo.media.disconnect()
        if (!permissions.overlay && halo.running.value)
            stopService(Intent(this, OverlayService::class.java))
    }
}
