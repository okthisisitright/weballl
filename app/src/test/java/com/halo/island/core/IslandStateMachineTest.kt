package com.halo.island.core

import org.junit.Assert.*
import org.junit.Test

class IslandStateMachineTest {
    private fun mediaMachine() =
        IslandStateMachine().apply {
            dispatch(IslandEvent.Context(IslandContext(media = true, playing = true)), 0)
        }

    @Test
    fun idleIsMinimal() {
        assertEquals(IslandMode.IDLE, IslandStateMachine().mode(0))
    }

    @Test
    fun playingMediaBecomesCompact() {
        assertEquals(IslandMode.MEDIA_COMPACT, mediaMachine().mode(0))
    }

    @Test
    fun tappingExpandsAndReverses() {
        val m = mediaMachine()
        assertEquals(IslandMode.MEDIA_EXPANDED, m.dispatch(IslandEvent.Tap, 1))
        assertEquals(IslandMode.MEDIA_COMPACT, m.dispatch(IslandEvent.Tap, 2))
    }

    @Test
    fun mediaChargingMedia() {
        val m = mediaMachine()
        m.dispatch(IslandEvent.Temporary(IslandMode.CHARGING, 50, 500), 1)
        assertEquals(IslandMode.CHARGING, m.mode(100))
        assertEquals(IslandMode.MEDIA_COMPACT, m.dispatch(IslandEvent.Tick, 501))
    }

    @Test
    fun expandedContextSurvivesNotification() {
        val m = mediaMachine()
        m.dispatch(IslandEvent.Tap, 1)
        m.dispatch(IslandEvent.Temporary(IslandMode.NOTIFICATION_PREVIEW, 60, 500), 2)
        assertEquals(IslandMode.NOTIFICATION_PREVIEW, m.mode(100))
        assertEquals(IslandMode.MEDIA_EXPANDED, m.dispatch(IslandEvent.Tick, 501))
    }

    @Test
    fun timerMusicTimerCoexist() {
        val m = IslandStateMachine()
        m.dispatch(IslandEvent.Context(IslandContext(timer = true)), 0)
        assertEquals(IslandMode.TIMER_COMPACT, m.mode(0))
        m.dispatch(
            IslandEvent.Context(IslandContext(media = true, playing = true, timer = true)),
            1,
        )
        assertEquals(IslandMode.MEDIA_COMPACT, m.mode(1))
        assertEquals(
            IslandMode.TIMER_COMPACT,
            m.dispatch(IslandEvent.Context(IslandContext(timer = true)), 2),
        )
    }

    @Test
    fun expandedSessionLostReturnsIdle() {
        val m = mediaMachine()
        m.dispatch(IslandEvent.Tap, 1)
        assertEquals(IslandMode.IDLE, m.dispatch(IslandEvent.Context(IslandContext()), 2))
    }

    @Test
    fun lowerPriorityDoesNotStealTimerCompletion() {
        val m = mediaMachine()
        m.dispatch(IslandEvent.Temporary(IslandMode.TIMER_EXPANDED, 100, 500), 1)
        m.dispatch(IslandEvent.Temporary(IslandMode.CHARGING, 50, 200), 2)
        assertEquals(500L, m.temporaryExpiry)
        assertEquals(IslandMode.TIMER_EXPANDED, m.mode(300))
        assertEquals(IslandMode.MEDIA_COMPACT, m.dispatch(IslandEvent.Tick, 501))
    }

    @Test
    fun userCanDismissAndRestoreWithoutLosingContext() {
        val m = mediaMachine()
        assertEquals(IslandMode.DISMISSED, m.dispatch(IslandEvent.Dismiss, 0))
        assertEquals(IslandMode.MEDIA_COMPACT, m.dispatch(IslandEvent.Restore, 1))
    }

    @Test
    fun pausedVisibilityUsesTimerFallback() {
        val m = IslandStateMachine()
        assertEquals(
            IslandMode.TIMER_COMPACT,
            m.dispatch(IslandEvent.Context(IslandContext(true, false, true, false)), 0),
        )
    }

    @Test
    fun expiredEventIsIgnored() {
        val m = mediaMachine()
        assertEquals(
            IslandMode.MEDIA_COMPACT,
            m.dispatch(IslandEvent.Temporary(IslandMode.CHARGING, 50, 1), 10),
        )
    }

    @Test
    fun timerTapHasRealExpandedState() {
        val m = IslandStateMachine()
        m.dispatch(IslandEvent.Context(IslandContext(timer = true)), 0)
        assertEquals(IslandMode.TIMER_EXPANDED, m.dispatch(IslandEvent.Tap, 1))
    }

    @Test
    fun seekingHasExplicitRecoverableState() {
        val m = mediaMachine()
        m.dispatch(IslandEvent.Tap, 1)
        assertEquals(IslandMode.MEDIA_SEEKING, m.dispatch(IslandEvent.Seeking(true), 2))
        assertEquals(IslandMode.MEDIA_EXPANDED, m.dispatch(IslandEvent.Seeking(false), 3))
        m.dispatch(IslandEvent.Seeking(true), 4)
        assertEquals(IslandMode.IDLE, m.dispatch(IslandEvent.Context(IslandContext()), 5))
    }

    @Test
    fun hidePausedAlsoExitsExpandedPanel() {
        val m = mediaMachine()
        m.dispatch(IslandEvent.Tap, 1)
        assertEquals(
            IslandMode.TIMER_COMPACT,
            m.dispatch(IslandEvent.Context(IslandContext(true, false, true, false)), 2),
        )
    }
}
