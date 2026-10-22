package com.halo.island.core

import org.junit.Assert.*
import org.junit.Test

class CoreLogicTest {
    @Test
    fun activeSessionWinsOverNewerPausedSession() {
        assertEquals(
            "active",
            selectSession(
                listOf(MediaCandidate("paused", false, 20), MediaCandidate("active", true, 10)),
                null,
            ),
        )
    }

    @Test
    fun explicitPinCanRetainPausedSession() {
        assertEquals(
            "paused",
            selectSession(
                listOf(MediaCandidate("paused", false, 20), MediaCandidate("active", true, 10)),
                "paused",
                false,
            ),
        )
    }

    @Test
    fun destroyedPinFallsBack() {
        assertEquals("live", selectSession(listOf(MediaCandidate("live", true, 1)), "gone", false))
    }

    @Test
    fun noSessionsIsNull() {
        assertNull(selectSession(emptyList(), null))
    }

    @Test
    fun estimatesPlaybackUsingElapsedTime() {
        assertEquals(5000, playbackPosition(1000, 100, 2100, 2f, true, 10000))
    }

    @Test
    fun pausedProgressDoesNotAdvance() {
        assertEquals(1000, playbackPosition(1000, 100, 2100, 2f, false, 10000))
    }

    @Test
    fun progressClampsToDuration() {
        assertEquals(2000, playbackPosition(1000, 100, 5000, 1f, true, 2000))
    }

    @Test
    fun unknownTimestampDoesNotInventProgress() {
        assertEquals(1000, playbackPosition(1000, 0, 5000, 1f, true, 0))
    }

    @Test
    fun durationFormatsHoursAndNegativeInputs() {
        assertEquals("1:01:01", durationLabel(3661000))
        assertEquals("0:00", durationLabel(-1))
        assertEquals("3:09", durationLabel(189000))
    }

    @Test
    fun batteryHandlesUnknownScale() {
        assertNull(batteryPercent(30, 0))
        assertNull(batteryPercent(-1, 100))
        assertEquals(50, batteryPercent(1, 2))
        assertEquals(100, batteryPercent(110, 100))
    }

    @Test
    fun timerUsesMonotonicClockDuringWallClockChange() {
        val t = TimerState.start(10000, 1000, 50000, 4)
        assertEquals(8000, t.remaining(3000, 990000, 4))
    }

    @Test
    fun timerPauseResumePreservesRemaining() {
        val t = TimerState.start(10000, 1000, 50000, 4).pause(4000, 53000, 4)
        assertEquals(7000, t.remaining(99999, 999999, 4))
        assertEquals(6000, t.resume(9000, 99000, 4).remaining(10000, 100000, 4))
    }

    @Test
    fun timerRebootUsesBoundedWallFallback() {
        val t = TimerState.start(10000, 1000, 50000, 4)
        assertEquals(3000, t.remaining(100, 57000, 5))
        assertEquals(10000, t.remaining(100, 1000, 5))
        assertEquals(0, t.remaining(100, 70000, 5))
    }

    @Test
    fun finishedTimerCannotRestartByTicking() {
        assertEquals(
            0,
            TimerState.start(1000, 1000, 1000, 1).copy(finished = true).remaining(0, 0, 1),
        )
    }

    @Test
    fun stopwatchDoesNotMixBootClocks() {
        val w = StopwatchState(1000, 2000, 3)
        assertEquals(4000, w.elapsed(5000, 3))
        assertEquals(1000, w.elapsed(5000, 4))
    }

    @Test
    fun positionRespectsStatusBarCutoutAndEdges() {
        val p = clampPosition(400, 800, 300, 200, 900, -80, SafeInsets(10, 44, 15, 20))
        assertEquals(85, p.x)
        assertEquals(44, p.y)
        assertEquals(300, p.width)
    }

    @Test
    fun oversizedWindowClampsOnSmallScreen() {
        val p = clampPosition(200, 400, 500, 100, -900, 800, SafeInsets(8, 24, 8, 16))
        assertEquals(184, p.width)
        assertEquals(8, p.x)
        assertEquals(284, p.y)
    }

    @Test
    fun permissionMappingRequiresOverlayButNotOptionalNotifications() {
        assertTrue(PermissionState(true, false, false).canStart)
        assertFalse(PermissionState(true, false, true).mediaReady)
        assertTrue(PermissionState(true, true, false).mediaReady)
        assertFalse(PermissionState(false, true, true).canStart)
    }
}
