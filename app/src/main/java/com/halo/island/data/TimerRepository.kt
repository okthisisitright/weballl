package com.halo.island.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.halo.island.core.*
import com.halo.island.system.TimerAlarmReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimerRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val complete: () -> Unit,
) {
    private val key = stringPreferencesKey("timer_v1")
    private val watchKey = stringPreferencesKey("stopwatch_v1")
    private val mutex = Mutex()
    private val _timer = MutableStateFlow(TimerState())
    val timer = _timer.asStateFlow()
    private val _watch = MutableStateFlow(StopwatchState())
    val watch = _watch.asStateFlow()
    private val _remaining = MutableStateFlow(0L)
    val remaining = _remaining.asStateFlow()
    private val _watchTime = MutableStateFlow(0L)
    val watchTime = _watchTime.asStateFlow()
    val boot
        get() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private val alarm = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent() =
        PendingIntent.getBroadcast(
            context,
            71,
            Intent(context, TimerAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private var ticker: Job? = null
    private val initialized =
        scope.async {
            val p = context.haloStore.data.first()
            val n = p[key]?.split(',')?.mapNotNull { it.toLongOrNull() }
            if (n?.size == 7)
                _timer.value = TimerState(n[0], n[1], n[2], n[3].toInt(), n[4], n[5] == 1L)
            val w = p[watchKey]?.split(',')?.mapNotNull { it.toLongOrNull() }
            if (w?.size == 3)
                _watch.value =
                    StopwatchState(w[0], if (w[2].toInt() == boot) w[1] else 0, w[2].toInt())
        }

    init {
        scope.launch {
            initialized.await()
            reschedule()
            tick()
            startTicker()
        }
    }

    private suspend fun persist() {
        context.haloStore.edit { p ->
            val t = timer.value
            p[key] =
                listOf(
                        t.total,
                        t.deadlineElapsed,
                        t.deadlineWall,
                        t.boot,
                        t.paused,
                        if (t.finished) 1 else 0,
                        1,
                    )
                    .joinToString(",")
            val w = watch.value
            p[watchKey] = listOf(w.accumulated, w.started, w.boot).joinToString(",")
        }
    }

    private fun reschedule() {
        alarm.cancel(alarmIntent())
        if (timer.value.running) {
            val left =
                timer.value.remaining(
                    SystemClock.elapsedRealtime(),
                    System.currentTimeMillis(),
                    boot,
                )
            alarm.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + left,
                alarmIntent(),
            )
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch {
                while (isActive && (timer.value.running || watch.value.running)) {
                    tick()
                    delay(1000)
                }
                tick()
            }
    }

    suspend fun tick() =
        mutex.withLock {
            val t = timer.value
            _remaining.value =
                t.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot)
            _watchTime.value = watch.value.elapsed(SystemClock.elapsedRealtime(), boot)
            if (t.running && remaining.value == 0L) {
                _timer.value = t.copy(finished = true)
                persist()
                alarm.cancel(alarmIntent())
                complete()
            }
        }

    fun start(ms: Long) {
        scope.launch {
            initialized.await()
            mutex.withLock {
                val safe = ms.coerceIn(1000, 24 * 60 * 60 * 1000L)
                _timer.value =
                    TimerState.start(
                        safe,
                        SystemClock.elapsedRealtime(),
                        System.currentTimeMillis(),
                        boot,
                    )
                persist()
                reschedule()
            }
            tick()
            startTicker()
        }
    }

    fun pauseResume() {
        scope.launch {
            initialized.await()
            tick()
            mutex.withLock {
                _timer.value =
                    timer.value.let {
                        if (it.running)
                            it.pause(
                                SystemClock.elapsedRealtime(),
                                System.currentTimeMillis(),
                                boot,
                            )
                        else if (it.active)
                            it.resume(
                                SystemClock.elapsedRealtime(),
                                System.currentTimeMillis(),
                                boot,
                            )
                        else it
                    }
                persist()
                reschedule()
            }
            tick()
            startTicker()
        }
    }

    fun cancel() {
        scope.launch {
            initialized.await()
            mutex.withLock {
                _timer.value = TimerState()
                persist()
                reschedule()
            }
            tick()
        }
    }

    fun toggleWatch() {
        scope.launch {
            initialized.await()
            mutex.withLock {
                val w = watch.value
                _watch.value =
                    if (w.running)
                        StopwatchState(w.elapsed(SystemClock.elapsedRealtime(), boot), 0, boot)
                    else w.copy(started = SystemClock.elapsedRealtime(), boot = boot)
                persist()
            }
            tick()
            startTicker()
        }
    }

    fun resetWatch() {
        scope.launch {
            initialized.await()
            mutex.withLock {
                _watch.value = StopwatchState()
                persist()
            }
            tick()
        }
    }

    suspend fun alarmFired() {
        initialized.await()
        tick()
        if (timer.value.running) reschedule()
    }
}
