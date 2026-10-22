package com.halo.island.system

import android.app.KeyguardManager
import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ServiceCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.savedstate.*
import com.halo.island.core.*
import com.halo.island.halo
import com.halo.island.ui.HaloTheme
import com.halo.island.ui.IslandSurface
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Suppress(
    "RtlHardcoded",
    "ClickableViewAccessibility",
) // Absolute screen coordinates; ACTION_OUTSIDE is not a click. Compose exposes all click
// semantics.
class OverlayService : LifecycleService(), SavedStateRegistryOwner {
    companion object {
        const val STOP = "com.halo.island.STOP"

        fun start(c: Context) {
            // Called only by explicit user interaction while HALO's activity is visible.
            androidx.core.content.ContextCompat.startForegroundService(
                c,
                Intent(c, OverlayService::class.java),
            )
        }
    }

    private val registryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry
        get() = registryController.savedStateRegistry

    private lateinit var windowContext: Context
    private lateinit var windows: WindowManager
    private var island: ComposeView? = null
    private var attached = false
    private var availableWidthDp by mutableFloatStateOf(360f)
    private var measuredWidth = 0
    private var measuredHeight = 0
    private var screenAwake = true
    private var insets = SafeInsets()
    private var params =
        WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            .apply {
                gravity = Gravity.TOP or Gravity.LEFT
                // Android 12+ untrusted-overlay touch safety cap; no full-screen transparent
                // window.
                alpha = .8f
                if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
                if (Build.VERSION.SDK_INT >= 28)
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenAwake = false
                        hide()
                        halo.collapse()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        screenAwake = true
                        show()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        screenAwake = true
                        if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) show()
                    }
                }
            }
        }
    private val permissionListener =
        android.app.AppOpsManager.OnOpChangedListener { op, pkg ->
            if (pkg == packageName && !Settings.canDrawOverlays(this))
                lifecycleScope.launch {
                    stopSafely(
                        "Overlay access was revoked. Enable it in Android settings to resume."
                    )
                }
        }

    override fun onCreate() {
        registryController.performAttach()
        registryController.performRestore(null)
        super.onCreate()
        windowContext =
            if (Build.VERSION.SDK_INT >= 30) {
                val display =
                    getSystemService(android.hardware.display.DisplayManager::class.java)
                        .getDisplay(Display.DEFAULT_DISPLAY)
                if (display != null)
                    createDisplayContext(display)
                        .createWindowContext(
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            null,
                        )
                else this
            } else this
        windows = windowContext.getSystemService(WindowManager::class.java)
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        try {
            ServiceCompat.startForeground(
                this,
                Notices.SERVICE_ID,
                Notices.control(this),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else 0,
            )
        } catch (e: RuntimeException) {
            halo.error.value =
                "Android could not start HALO: ${e.javaClass.simpleName}. Reopen HALO and try again."
            stopSelf()
            return
        }
        island =
            ComposeView(windowContext).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_OUTSIDE) {
                        halo.collapse()
                        true
                    } else false
                }
                setOnApplyWindowInsetsListener { _, wi ->
                    @Suppress("DEPRECATION")
                    insets =
                        if (Build.VERSION.SDK_INT >= 30) {
                            val status =
                                wi.getInsetsIgnoringVisibility(
                                    WindowInsets.Type.statusBars() or
                                        WindowInsets.Type.displayCutout()
                                )
                            SafeInsets(status.left, status.top, status.right, status.bottom)
                        } else
                            SafeInsets(
                                wi.systemWindowInsetLeft,
                                wi.systemWindowInsetTop,
                                wi.systemWindowInsetRight,
                                wi.systemWindowInsetBottom,
                            )
                    post { position() }
                    wi
                }
                setContent {
                    val settings by halo.settings.state.collectAsStateWithLifecycle()
                    val mode by halo.mode.collectAsStateWithLifecycle()
                    val media by halo.media.selected.collectAsStateWithLifecycle()
                    val timer by halo.timer.timer.collectAsStateWithLifecycle()
                    val remaining by halo.timer.remaining.collectAsStateWithLifecycle()
                    val battery by halo.battery.state.collectAsStateWithLifecycle()
                    val notification by halo.notification.collectAsStateWithLifecycle()
                    HaloTheme {
                        IslandSurface(
                            mode,
                            settings,
                            media,
                            timer,
                            remaining,
                            battery,
                            notification,
                            overlay = true,
                            maxWidthDp = availableWidthDp,
                            onTap = halo::tap,
                            onCollapse = halo::collapse,
                            onQuick = halo::quick,
                            onToggle = halo.media::toggle,
                            onPrevious = halo.media::previous,
                            onNext = halo.media::next,
                            onSeek = halo.media::seek,
                            onSeekingChange = halo::seeking,
                            onSource = halo.media::openSource,
                            onTimerPause = halo.timer::pauseResume,
                            onTimerCancel = {
                                halo.timer.cancel()
                                halo.collapse()
                            },
                            onStartTimer = {
                                if (settings.timerEnabled) halo.timer.start(5 * 60 * 1000)
                            },
                            onOpenSettings = {
                                safeLaunch(
                                    Intent(
                                        this@OverlayService,
                                        com.halo.island.MainActivity::class.java,
                                    )
                                )
                            },
                            onStop = { stopSafely(null) },
                            onNotification = halo::openNotification,
                            onInteraction = halo::interaction,
                            onSize = { w, h ->
                                if (
                                    w != this@OverlayService.measuredWidth ||
                                        h != this@OverlayService.measuredHeight
                                ) {
                                    this@OverlayService.measuredWidth = w
                                    this@OverlayService.measuredHeight = h
                                    position()
                                }
                            },
                        )
                    }
                }
            }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        getSystemService(android.app.AppOpsManager::class.java)
            .startWatchingMode(
                android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                packageName,
                permissionListener,
            )
        halo.running.value = true
        halo.error.value = null
        halo.scope.launch { halo.settings.update { it.copy(enabled = true) } }
        lifecycleScope.launch { halo.settings.state.collect { position() } }
        show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == STOP) stopSafely(null)
        return Service.START_NOT_STICKY
    }

    private fun show() {
        if (
            attached ||
                !screenAwake ||
                getSystemService(KeyguardManager::class.java).isKeyguardLocked
        )
            return
        if (!Settings.canDrawOverlays(this)) {
            stopSafely("Overlay permission is missing.")
            return
        }
        island?.let { view ->
            position()
            try {
                windows.addView(view, params)
                attached = true
                view.requestApplyInsets()
            } catch (e: RuntimeException) {
                stopSafely("Android rejected the overlay window: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun hide() {
        if (attached) {
            island?.let {
                runCatching { windows.removeViewImmediate(it) }
                it.disposeComposition()
            }
            attached = false
        }
    }

    private fun position() {
        val metrics = windowContext.resources.displayMetrics
        val bounds =
            if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.bounds
            else android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        val s = halo.settings.state.value
        val d = metrics.density
        if (Build.VERSION.SDK_INT >= 30) {
            val safe =
                windows.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
                )
            insets =
                SafeInsets(
                    maxOf(insets.left, safe.left),
                    maxOf(insets.top, safe.top),
                    maxOf(insets.right, safe.right),
                    maxOf(insets.bottom, safe.bottom),
                )
        } else if (insets.top == 0) insets = insets.copy(top = (24 * d).roundToInt())
        availableWidthDp =
            ((bounds.width() - insets.left - insets.right) / d - 16f).coerceAtLeast(100f)
        val p =
            clampPosition(
                bounds.width(),
                bounds.height(),
                if (measuredWidth > 0) measuredWidth else (s.width * d).toInt(),
                measuredHeight,
                (s.x * d).roundToInt(),
                (s.y * d).roundToInt(),
                insets,
            )
        params.x = p.x
        params.y = p.y
        if (attached)
            island?.let {
                runCatching { windows.updateViewLayout(it, params) }
                    .onFailure {
                        stopSafely("Overlay window was removed by Android. Reopen HALO to resume.")
                    }
            }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        insets = SafeInsets()
        island?.requestApplyInsets()
        position()
        halo.collapse()
    }

    private fun stopSafely(message: String?) {
        halo.error.value = message
        halo.scope.launch { halo.settings.update { it.copy(enabled = false) } }
        hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        hide()
        island?.disposeComposition()
        island = null
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching {
            getSystemService(android.app.AppOpsManager::class.java)
                .stopWatchingMode(permissionListener)
        }
        halo.running.value = false
        halo.collapse()
        super.onDestroy()
    }
}
