package com.halo.island.core

data class IslandContext(
    val media: Boolean = false,
    val playing: Boolean = false,
    val timer: Boolean = false,
    val showPaused: Boolean = true,
)

data class TransientEvent(val mode: IslandMode, val priority: Int, val expires: Long)

sealed interface IslandEvent {
    data class Context(val context: IslandContext) : IslandEvent

    data class Temporary(val mode: IslandMode, val priority: Int, val until: Long) : IslandEvent

    data class Seeking(val active: Boolean) : IslandEvent

    data object Tap : IslandEvent

    data object Collapse : IslandEvent

    data object QuickActions : IslandEvent

    data object Dismiss : IslandEvent

    data object Restore : IslandEvent

    data object Tick : IslandEvent
}

/** Main-thread reducer. Transient focus never destroys media/timer context. */
class IslandStateMachine {
    var context = IslandContext()
        private set

    private var manual: IslandMode? = null
    private var temporary: TransientEvent? = null
    private var dismissed = false
    val temporaryExpiry
        get() = temporary?.expires

    fun dispatch(event: IslandEvent, now: Long): IslandMode {
        if ((temporary?.expires ?: Long.MAX_VALUE) <= now) temporary = null
        when (event) {
            is IslandEvent.Context -> {
                context = event.context
                if (
                    (!context.media || (!context.playing && !context.showPaused)) &&
                        manual in setOf(IslandMode.MEDIA_EXPANDED, IslandMode.MEDIA_SEEKING)
                )
                    manual = null
                if (!context.timer && manual == IslandMode.TIMER_EXPANDED) manual = null
            }
            is IslandEvent.Temporary ->
                if (
                    event.until > now &&
                        (temporary == null || event.priority >= temporary!!.priority)
                )
                    temporary = TransientEvent(event.mode, event.priority, event.until)
            is IslandEvent.Seeking -> {
                if (context.media)
                    manual =
                        if (event.active) IslandMode.MEDIA_SEEKING else IslandMode.MEDIA_EXPANDED
            }
            IslandEvent.Tap -> {
                temporary = null
                manual =
                    when (mode(now)) {
                        IslandMode.MEDIA_EXPANDED,
                        IslandMode.MEDIA_SEEKING,
                        IslandMode.TIMER_EXPANDED,
                        IslandMode.QUICK_ACTIONS -> null
                        else ->
                            when {
                                context.media && (context.playing || context.showPaused) ->
                                    IslandMode.MEDIA_EXPANDED
                                context.timer -> IslandMode.TIMER_EXPANDED
                                else -> IslandMode.QUICK_ACTIONS
                            }
                    }
            }
            IslandEvent.Collapse -> {
                manual = null
                temporary = null
            }
            IslandEvent.QuickActions -> {
                manual = IslandMode.QUICK_ACTIONS
                temporary = null
            }
            IslandEvent.Dismiss -> {
                dismissed = true
                temporary = null
                manual = null
            }
            IslandEvent.Restore -> dismissed = false
            IslandEvent.Tick -> Unit
        }
        return mode(now)
    }

    fun mode(now: Long): IslandMode {
        if (dismissed) return IslandMode.DISMISSED
        temporary
            ?.takeIf { it.expires > now }
            ?.let {
                return it.mode
            }
        manual?.let {
            return it
        }
        return when {
            context.media && (context.playing || context.showPaused) -> IslandMode.MEDIA_COMPACT
            context.timer -> IslandMode.TIMER_COMPACT
            else -> IslandMode.IDLE
        }
    }
}

data class TimerState(
    val total: Long = 0,
    val deadlineElapsed: Long = 0,
    val deadlineWall: Long = 0,
    val boot: Int = 0,
    val paused: Long = 0,
    val finished: Boolean = false,
) {
    val active
        get() = total > 0 && !finished

    val running
        get() = active && paused == 0L

    fun remaining(elapsed: Long, wall: Long, currentBoot: Int): Long =
        when {
            finished || total <= 0 -> 0
            paused > 0 -> paused
            boot == currentBoot -> (deadlineElapsed - elapsed).coerceAtLeast(0)
            else -> (deadlineWall - wall).coerceIn(0, total)
        }

    fun pause(elapsed: Long, wall: Long, currentBoot: Int) =
        copy(paused = remaining(elapsed, wall, currentBoot).coerceAtLeast(1))

    fun resume(elapsed: Long, wall: Long, currentBoot: Int) =
        copy(
            deadlineElapsed = elapsed + paused,
            deadlineWall = wall + paused,
            boot = currentBoot,
            paused = 0,
        )

    companion object {
        fun start(ms: Long, elapsed: Long, wall: Long, boot: Int) =
            TimerState(ms, elapsed + ms, wall + ms, boot)
    }
}

data class StopwatchState(val accumulated: Long = 0, val started: Long = 0, val boot: Int = 0) {
    val running
        get() = started > 0

    fun elapsed(now: Long, currentBoot: Int) =
        accumulated + if (running && boot == currentBoot) (now - started).coerceAtLeast(0) else 0
}
