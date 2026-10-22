package com.halo.island

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.island.core.ThemePreset
import com.halo.island.system.OverlayService
import com.halo.island.system.permissionState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** Run on an emulator with overlay and listener access explicitly granted using adb (see docs). */
class HaloDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context
        get() = instrumentation.targetContext

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    @Test
    fun launchesAndPersistsThemeAcrossNewRepositoryRead() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val graph = context.halo
            graph.settings.loaded.await()
            val old = graph.settings.state.value
            try {
                graph.settings.update { it.preset(ThemePreset.FROST) }
                val fresh = com.halo.island.data.SettingsRepository(context, graph.scope)
                fresh.loaded.await()
                assertEquals(ThemePreset.FROST, fresh.state.value.theme)
            } finally {
                graph.settings.update { old }
            }
        }
    }

    @Test
    fun realSessionDiscoveryTransportSeekAndDisconnect() {
        assertTrue(
            "Grant listener access before running this test",
            context.permissionState().listener,
        )
        val graph = context.halo
        val paused = CountDownLatch(1)
        val sought = CountDownLatch(1)
        val played = CountDownLatch(1)
        val next = CountDownLatch(1)
        val previous = CountDownLatch(1)
        val session = MediaSession(context, "HALO instrumentation source")
        try {
            onMain {
                session.setCallback(
                    object : MediaSession.Callback() {
                        override fun onPause() {
                            paused.countDown()
                            session.setPlaybackState(state(false))
                        }

                        override fun onPlay() {
                            session.setPlaybackState(state(true))
                            played.countDown()
                        }

                        override fun onSkipToNext() {
                            val art =
                                android.graphics.Bitmap.createBitmap(
                                        12,
                                        12,
                                        android.graphics.Bitmap.Config.ARGB_8888,
                                    )
                                    .apply { eraseColor(android.graphics.Color.GREEN) }
                            session.setMetadata(
                                MediaMetadata.Builder()
                                    .putString(
                                        MediaMetadata.METADATA_KEY_TITLE,
                                        "Updated integration track",
                                    )
                                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 120000)
                                    .build()
                            )
                            next.countDown()
                        }

                        override fun onSkipToPrevious() {
                            previous.countDown()
                        }

                        override fun onSeekTo(pos: Long) {
                            if (pos == 45000L) sought.countDown()
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
                session.setMetadata(
                    MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, "Integration test track")
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, "Local test source")
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, 120000)
                        .build()
                )
                session.setPlaybackState(state(true))
                session.isActive = true
                graph.media.connect()
                graph.media.refresh()
            }
            runBlocking {
                withTimeout(10000) {
                    graph.media.tracks.first { tracks ->
                        tracks.any { it.title == "Integration test track" }
                    }
                }
            }
            onMain {
                graph.media.tracks.value
                    .firstOrNull { it.title == "Integration test track" }
                    ?.let { graph.media.select(it.id) }
                graph.media.toggle()
            }
            assertTrue(
                "Transport pause delivered to real MediaSession",
                paused.await(5, TimeUnit.SECONDS),
            )
            runBlocking {
                withTimeout(10000) { graph.media.selected.first { it?.playing == false } }
            }
            onMain { graph.media.toggle() }
            assertTrue("Transport play delivered", played.await(5, TimeUnit.SECONDS))
            onMain { graph.media.next() }
            assertTrue("Transport next delivered", next.await(5, TimeUnit.SECONDS))
            runBlocking {
                withTimeout(10000) {
                    graph.media.selected.first {
                        it?.title == "Updated integration track" && it.artwork != null
                    }
                }
            }
            onMain { graph.media.previous() }
            assertTrue("Transport previous delivered", previous.await(5, TimeUnit.SECONDS))
            onMain { graph.media.seek(45000) }
            assertTrue("Transport seek delivered", sought.await(5, TimeUnit.SECONDS))
        } finally {
            onMain {
                session.release()
                graph.media.refresh()
            }
        }
        runBlocking {
            withTimeout(10000) {
                graph.media.tracks.first { tracks ->
                    tracks.none {
                        it.title == "Integration test track" ||
                            it.title == "Updated integration track"
                    }
                }
            }
        }
    }

    @Test
    fun overlayStartsOutsideActivityAndStopsCleanly() {
        assertTrue("Grant overlay permission first", context.permissionState().overlay)
        val graph = context.halo
        ActivityScenario.launch(MainActivity::class.java).use {
            onMain { OverlayService.start(context) }
            runBlocking { withTimeout(10000) { graph.running.first { it } } }
            instrumentation.uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            )
            Thread.sleep(800)
            assertTrue("Service remains running above launcher", graph.running.value)
            onMain {
                context.stopService(android.content.Intent(context, OverlayService::class.java))
            }
            runBlocking { withTimeout(10000) { graph.running.first { !it } } }
        }
    }

    @Test
    fun touchesOutsideOverlayReachAnotherApplication() {
        assertTrue(context.permissionState().overlay)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                onMain { OverlayService.start(context) }
                runBlocking { withTimeout(10000) { context.halo.running.first { it } } }
                val intent =
                    android.content
                        .Intent()
                        .setComponent(
                            android.content.ComponentName(
                                "com.halo.island.test",
                                "com.halo.island.TestProbeActivity",
                            )
                        )
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                val ui = instrumentation.uiAutomation
                ui.serviceInfo =
                    ui.serviceInfo.apply {
                        flags =
                            flags or
                                android.accessibilityservice.AccessibilityServiceInfo
                                    .FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    }
                scenario.onActivity { activity -> activity.startActivity(intent) }
                fun findProbe(label: String): android.view.accessibility.AccessibilityNodeInfo? =
                    ui.windows
                        .mapNotNull { it.root }
                        .flatMap { it.findAccessibilityNodeInfosByText(label) }
                        .firstOrNull {
                            it.isVisibleToUser && it.packageName == "com.halo.island.test"
                        }
                val deadline = System.currentTimeMillis() + 15000
                var target: android.view.accessibility.AccessibilityNodeInfo? = null
                while (target == null && System.currentTimeMillis() < deadline) {
                    target = findProbe("Tap target")
                    if (target == null) Thread.sleep(200)
                }
                assertNotNull("Separate test application is visible", target)
                val rect = android.graphics.Rect()
                target!!.getBoundsInScreen(rect)
                assertTrue("Real overlay service is still running", context.halo.running.value)
                val overlayBounds =
                    ui.windows
                        .firstOrNull { window ->
                            val frame = android.graphics.Rect()
                            window.getBoundsInScreen(frame)
                            window.root?.packageName == "com.halo.island" &&
                                frame.height() in
                                    1 until (context.resources.displayMetrics.heightPixels / 2)
                        }
                        ?.let { window ->
                            android.graphics.Rect().also { window.getBoundsInScreen(it) }
                        }
                assertNotNull(
                    "HALO exposes a compact visible window rather than a full-screen input surface",
                    overlayBounds,
                )
                assertFalse(
                    "Probe is outside island input bounds",
                    overlayBounds!!.contains(rect.centerX(), rect.centerY()),
                )
                val now = android.os.SystemClock.uptimeMillis()
                val down =
                    android.view.MotionEvent.obtain(
                        now,
                        now,
                        android.view.MotionEvent.ACTION_DOWN,
                        rect.exactCenterX(),
                        rect.exactCenterY(),
                        0,
                    )
                val up =
                    android.view.MotionEvent.obtain(
                        now,
                        now + 100,
                        android.view.MotionEvent.ACTION_UP,
                        rect.exactCenterX(),
                        rect.exactCenterY(),
                        0,
                    )
                down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(instrumentation.uiAutomation.injectInputEvent(down, true))
                assertTrue(instrumentation.uiAutomation.injectInputEvent(up, true))
                down.recycle()
                up.recycle()
                val finish = System.currentTimeMillis() + 10000
                var received = false
                while (!received && System.currentTimeMillis() < finish) {
                    received = findProbe("Touch passed through") != null
                    if (!received) Thread.sleep(200)
                }
                assertTrue(
                    "Outside touch reached the other app through real input dispatch",
                    received,
                )
            } finally {
                onMain {
                    context.stopService(android.content.Intent(context, OverlayService::class.java))
                }
                runBlocking { withTimeout(10000) { context.halo.running.first { !it } } }
            }
        }
    }

    @Test
    fun localTimerCountsPausesResumesAndFinishes() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val timers = context.halo.timer
            try {
                timers.start(4000)
                withTimeout(15000) { timers.timer.first { it.running } }
                val initial = withTimeout(15000) { timers.remaining.first { it in 1..4000 } }
                withTimeout(15000) { timers.remaining.first { it in 1 until initial } }
                timers.pauseResume()
                val paused = withTimeout(15000) { timers.timer.first { it.active && !it.running } }
                withTimeout(15000) { timers.remaining.first { it == paused.paused } }
                delay(1200)
                assertEquals("Pause remains stable", paused.paused, timers.remaining.value)
                timers.pauseResume()
                withTimeout(20000) { timers.timer.first { it.finished } }
                assertEquals(0L, timers.remaining.value)
            } finally {
                timers.cancel()
            }
        }
    }

    private fun state(playing: Boolean) =
        PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                10000,
                if (playing) 1f else 0f,
            )
            .build()
}
