package com.halo.island.system

import android.content.*
import android.os.BatteryManager
import android.os.PowerManager
import com.halo.island.core.batteryPercent
import kotlinx.coroutines.flow.*

data class BatteryState(
    val percent: Int? = null,
    val connected: Boolean = false,
    val charging: Boolean = false,
)

class BatteryStateRepository(context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val _powerSaver = MutableStateFlow(power.isPowerSaveMode)
    val powerSaver = _powerSaver.asStateFlow()
    private val _state = MutableStateFlow(BatteryState())
    val state = _state.asStateFlow()
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i == null) return
                if (i.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                    _powerSaver.value = power.isPowerSaveMode
                    return
                }
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                _state.value =
                    BatteryState(
                        batteryPercent(
                            i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                            i.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                        ),
                        i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                        status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL,
                    )
            }
        }

    init {
        context.applicationContext.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            },
        )
    }
}
