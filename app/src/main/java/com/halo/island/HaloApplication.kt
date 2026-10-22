package com.halo.island

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.halo.island.core.*
import com.halo.island.data.*
import com.halo.island.system.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class HaloApplication : Application() {
    val graph by lazy { HaloGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph
    }
}

val Context.halo: HaloGraph
    get() = (applicationContext as HaloApplication).graph

class HaloGraph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(context, scope)
    val media = MediaSessionRepository(context, scope, settings)
    val battery = BatteryStateRepository(context)
    val timer =
        TimerRepository(context, scope) {
            Notices.timerFinished(context, settings.state.value.audibleTimer)
            temporary(IslandMode.TIMER_EXPANDED, 100, 12000)
        }
    private val machine = IslandStateMachine()
    private val _mode = MutableStateFlow(IslandMode.IDLE)
    val mode = _mode.asStateFlow()
    private val _notification = MutableStateFlow<NotificationPreview?>(null)
    val notification = _notification.asStateFlow()
    val running = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var transientJob: Job? = null
    private var collapseJob: Job? = null

    init {
        Notices.channels(context)
        scope.launch {
            combine(media.selected, timer.timer, settings.state) { m, t, s -> Triple(m, t, s) }
                .collect { (m, t, s) ->
                    val before = machine.context
                    dispatch(
                        IslandEvent.Context(
                            IslandContext(m != null, m?.playing == true, t.active, s.showPaused)
                        )
                    )
                    if (s.autoExpand && s.mediaEvents && m?.playing == true && !before.playing) {
                        dispatch(IslandEvent.Tap)
                        scheduleCollapse()
                    }
                }
        }
        scope.launch {
            var previous: BatteryState? = null
            battery.state.collect { b ->
                val old = previous
                previous = b
                val s = settings.state.value
                if (old != null && b.connected != old.connected && s.charging)
                    temporary(IslandMode.CHARGING, 50, s.eventSeconds * 1000L)
                else if (
                    old != null &&
                        s.lowBattery &&
                        ((b.percent ?: 100) <= 15 && (old.percent ?: 100) > 15 ||
                            b.percent == 100 && old.percent != 100)
                )
                    temporary(IslandMode.BATTERY_STATUS, 40, s.eventSeconds * 1000L)
            }
        }
    }

    fun dispatch(event: IslandEvent) {
        _mode.value = machine.dispatch(event, SystemClock.elapsedRealtime())
    }

    fun tap() {
        dispatch(IslandEvent.Tap)
        scheduleCollapse()
    }

    fun collapse() {
        collapseJob?.cancel()
        dispatch(IslandEvent.Collapse)
    }

    fun quick() {
        dispatch(IslandEvent.QuickActions)
        scheduleCollapse()
    }

    fun interaction() = scheduleCollapse()

    fun seeking(active: Boolean) {
        dispatch(IslandEvent.Seeking(active))
        if (active) collapseJob?.cancel() else scheduleCollapse()
    }

    private fun scheduleCollapse() {
        if (mode.value == IslandMode.MEDIA_SEEKING) return
        collapseJob?.cancel()
        collapseJob =
            scope.launch {
                delay(settings.state.value.collapseSeconds * 1000L)
                dispatch(IslandEvent.Collapse)
            }
    }

    fun temporary(mode: IslandMode, priority: Int, duration: Long) {
        dispatch(IslandEvent.Temporary(mode, priority, SystemClock.elapsedRealtime() + duration))
        transientJob?.cancel()
        transientJob =
            scope.launch {
                delay(
                    ((machine.temporaryExpiry ?: SystemClock.elapsedRealtime()) -
                            SystemClock.elapsedRealtime())
                        .coerceAtLeast(0)
                )
                dispatch(IslandEvent.Tick)
                if (this@HaloGraph.mode.value != IslandMode.NOTIFICATION_PREVIEW)
                    _notification.value = null
            }
    }

    fun previewNotification(preview: NotificationPreview) {
        _notification.value = preview
        temporary(IslandMode.NOTIFICATION_PREVIEW, 60, settings.state.value.eventSeconds * 1000L)
    }

    fun openNotification() {
        _notification.value?.intent?.let { context.sendUserPendingIntent(it) }
        collapse()
    }
}
