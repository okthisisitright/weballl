@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.halo.island.ui

import android.media.session.PlaybackState
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.island.HaloGraph
import com.halo.island.core.*
import com.halo.island.data.MediaTrack
import com.halo.island.system.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    HOME("Home", Icons.Rounded.Home),
    STUDIO("Studio", Icons.Rounded.Tune),
    EVENTS("Events", Icons.Rounded.BlurOn),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun HaloApp(
    g: HaloGraph,
    permissions: PermissionState,
    onOverlayPermission: () -> Unit,
    onListenerPermission: () -> Unit,
    onNotificationPermission: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onBatterySettings: () -> Unit,
    onAppSettings: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    val settings by g.settings.state.collectAsStateWithLifecycle()
    val running by g.running.collectAsStateWithLifecycle()
    val error by g.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val update: ((HaloSettings) -> HaloSettings) -> Unit = { change ->
        scope.launch { g.settings.update(change) }
    }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HaloMark(28.dp)
                    Text(
                        "HALO",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 4.sp,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(5.dp).background(if (running) Mint else Muted, CircleShape))
                    Text(
                        if (running) "ISLAND ACTIVE" else "YOUR PHONE. ALIVE.",
                        fontSize = 8.sp,
                        letterSpacing = 1.sp,
                        color = Muted,
                    )
                }
            }
            AnimatedContent(
                destination,
                Modifier.weight(1f),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "Destination",
            ) { page ->
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    when (page) {
                        Destination.HOME ->
                            HomeScreen(
                                g,
                                settings,
                                running,
                                permissions,
                                onEnable,
                                onDisable,
                                onOverlayPermission,
                                onListenerPermission,
                                onNotificationPermission,
                                { destination = Destination.STUDIO },
                                update,
                            )
                        Destination.STUDIO -> StudioScreen(g, settings, update)
                        Destination.EVENTS -> EventsScreen(g, settings, update)
                        Destination.SETTINGS ->
                            SettingsScreen(
                                settings,
                                permissions,
                                update,
                                onOverlayPermission,
                                onListenerPermission,
                                onNotificationPermission,
                                onBatterySettings,
                                onAppSettings,
                            )
                    }
                    error?.let {
                        Text(
                            it,
                            color = Color(0xFFE9BEA8),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(24.dp),
                        )
                        TextButton(
                            onClick = { g.error.value = null },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
            HorizontalDivider(color = Silver.copy(alpha = .07f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Destination.entries.forEach { d ->
                    Column(
                        Modifier.clip(RoundedCornerShape(18.dp))
                            .clickable { destination = d }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            d.icon,
                            d.label,
                            tint = if (destination == d) Silver else Muted.copy(alpha = .55f),
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            d.label,
                            fontSize = 10.sp,
                            color = if (destination == d) Silver else Muted,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HaloMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        drawRoundRect(
            Brush.linearGradient(listOf(Color.White, Mint.copy(alpha = .45f))),
            topLeft = androidx.compose.ui.geometry.Offset(1f, this.size.height * .29f),
            size = androidx.compose.ui.geometry.Size(this.size.width - 2, this.size.height * .43f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.height * .22f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
        )
    }
}

@Composable
private fun Heading(eyebrow: String, title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
        Text(eyebrow, fontSize = 9.sp, letterSpacing = 2.sp, color = Mint)
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 9.dp, bottom = 8.dp),
        )
        Text(subtitle, fontSize = 13.sp, color = Muted, lineHeight = 20.sp)
    }
}

@Composable
private fun HomeScreen(
    g: HaloGraph,
    s: HaloSettings,
    running: Boolean,
    p: PermissionState,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onOverlay: () -> Unit,
    onListener: () -> Unit,
    onNotifications: () -> Unit,
    onStudio: () -> Unit,
    update: ((HaloSettings) -> HaloSettings) -> Unit,
) {
    Heading(
        "A LITTLE MORE POSSIBILITY",
        "Meet your island.",
        "Music, moments, and the little things.\nAlways within reach.",
    )
    LivePreview(g, s, onStudio)
    Row(
        Modifier.padding(horizontal = 24.dp, vertical = 18.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (running) "Your island is alive" else "Enable HALO",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (running) "Floating above supported apps"
                else "Enable HALO to float above other apps",
                fontSize = 11.sp,
                color = Muted,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Switch(checked = running, onCheckedChange = { if (it) onEnable() else onDisable() })
    }
    SectionLabel("MAKE IT YOURS", "Open studio", onStudio)
    PresetStrip(s, update)
    SectionLabel("A SMALL INTRODUCTION")
    Column(Modifier.padding(horizontal = 24.dp)) {
        PermissionRow(
            "01",
            "Display over other apps",
            "HALO uses a small floating window. Android may hide it on protected screens.",
            p.overlay,
            "Allow overlay",
            onOverlay,
        )
        PermissionRow(
            "02",
            "Connect your music",
            "Notification access discovers active media sessions. This access is sensitive; all data stays on your phone.",
            p.listener,
            "Connect media",
            onListener,
        )
        PermissionRow(
            "03",
            "Stay in control",
            "Optional notifications provide a visible Stop action and timer alerts. Android may still show foreground-service status if denied.",
            p.notifications,
            "Allow notifications",
            onNotifications,
        )
    }
    val tracks by g.media.tracks.collectAsStateWithLifecycle()
    val selected by g.media.selected.collectAsStateWithLifecycle()
    if (tracks.isNotEmpty()) {
        SectionLabel("ACTIVE AUDIO SOURCES")
        tracks.forEach { track ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { g.media.select(track.id) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(track, 38.dp)
                Column(Modifier.weight(1f)) {
                    Text(track.source, fontSize = 13.sp)
                    Text(
                        track.title,
                        fontSize = 11.sp,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (track.id == selected?.id)
                    Icon(
                        Icons.Rounded.Check,
                        "Selected source",
                        tint = Mint,
                        modifier = Modifier.size(18.dp),
                    )
            }
        }
    } else
        Text(
            "Play audio in a compatible app after connecting media. HALO listens for real session updates.",
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.padding(24.dp),
        )
}

@Composable
private fun LivePreview(
    g: HaloGraph,
    s: HaloSettings,
    onOpenStudio: () -> Unit = { g.collapse() },
) {
    val media by g.media.selected.collectAsStateWithLifecycle()
    val timer by g.timer.timer.collectAsStateWithLifecycle()
    val remaining by g.timer.remaining.collectAsStateWithLifecycle()
    val battery by g.battery.state.collectAsStateWithLifecycle()
    val mode by g.mode.collectAsStateWithLifecycle()
    val notification by g.notification.collectAsStateWithLifecycle()
    var demo by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var demoPlaying by rememberSaveable { mutableStateOf(true) }
    var demoPosition by rememberSaveable { mutableLongStateOf(64000) }
    val context = LocalContext.current
    val bitmap by
        produceState<android.graphics.Bitmap?>(null) {
            value = withContext(Dispatchers.Default) { studioBackdrop() }
        }
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val sample =
        remember(demoPlaying, demoPosition) {
            MediaTrack(
                "sample",
                "com.halo.island",
                "A quieter kind of alive",
                "HALO · Sample audio",
                "Preview only",
                null,
                0xFFBEDDD1.toInt(),
                213000,
                demoPosition,
                0,
                1f,
                demoPlaying,
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO,
            )
        }
    val visibleMode =
        when (demo) {
            "Music" -> if (expanded) IslandMode.MEDIA_EXPANDED else IslandMode.MEDIA_COMPACT
            "Charging" -> IslandMode.CHARGING
            "Timer" -> if (expanded) IslandMode.TIMER_EXPANDED else IslandMode.TIMER_COMPACT
            "Alert" -> IslandMode.NOTIFICATION_PREVIEW
            else -> mode
        }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Box(
            Modifier.fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(30.dp))
                .onGloballyPositioned { bounds = it.boundsInRoot() }
        ) {
            bitmap?.let { scene ->
                Image(
                    scene.asImageBitmap(),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
            Column(Modifier.align(Alignment.TopStart).padding(18.dp)) {
                Text(
                    if (demo == null) "LIVE MATERIAL PREVIEW"
                    else "SAMPLE ${demo!!.uppercase()} · PREVIEW ONLY",
                    fontSize = 8.sp,
                    color = Silver.copy(alpha = .6f),
                    letterSpacing = 1.2.sp,
                )
                Text(
                    "In-app backdrop refraction on API 33+",
                    fontSize = 9.sp,
                    color = Silver.copy(alpha = .45f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box(Modifier.align(Alignment.Center)) {
                IslandSurface(
                    visibleMode,
                    s,
                    if (demo == "Music") sample else media,
                    if (demo == "Timer") TimerState(total = 300000, paused = 187000) else timer,
                    if (demo == "Timer") 187000 else remaining,
                    if (demo == "Charging") BatteryState(72, true, true) else battery,
                    if (demo == "Alert")
                        NotificationPreview(
                            "HALO",
                            "com.halo.island",
                            "A little moment",
                            "An explicitly labeled sample notification.",
                            null,
                            null,
                        )
                    else notification,
                    backdrop = bitmap?.let { AppBackdrop(it, bounds) },
                    timerInteractive = demo != "Timer",
                    onTap = { if (demo != null) expanded = !expanded else g.tap() },
                    onCollapse = { if (demo != null) expanded = false else g.collapse() },
                    onQuick = { if (demo == null) g.quick() },
                    onToggle = {
                        if (demo == "Music") demoPlaying = !demoPlaying
                        else if (demo == null) g.media.toggle()
                    },
                    onPrevious = { if (demo == null) g.media.previous() },
                    onNext = { if (demo == null) g.media.next() },
                    onSeek = {
                        if (demo == "Music") demoPosition = it
                        else if (demo == null) g.media.seek(it)
                    },
                    onSeekingChange = { if (demo == null) g.seeking(it) },
                    onSource = { if (demo == null) g.media.openSource() },
                    onTimerPause = { if (demo == null) g.timer.pauseResume() },
                    onTimerCancel = {
                        if (demo != null) demo = null
                        else {
                            g.timer.cancel()
                            g.collapse()
                        }
                    },
                    onStartTimer = { if (demo == null) g.timer.start(300000) },
                    onOpenSettings = {
                        demo = null
                        g.collapse()
                        onOpenStudio()
                    },
                    onStop = {
                        if (demo == null && g.running.value)
                            context.startService(
                                android.content
                                    .Intent(context, OverlayService::class.java)
                                    .setAction(OverlayService.STOP)
                            )
                        demo = null
                        g.collapse()
                    },
                    onNotification = { if (demo == null) g.openNotification() },
                    onInteraction = g::interaction,
                )
            }
            Text(
                if (demo == null) "Tap to expand  ·  Hold for more"
                else if (demo in listOf("Music", "Timer")) "Sample only · tap to expand"
                else "Sample event · no live data",
                fontSize = 10.sp,
                color = Silver.copy(alpha = .5f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("Live", "Music", "Charging", "Timer", "Alert").forEach { label ->
                val chosen = (demo == null && label == "Live") || demo == label
                FilterChip(
                    chosen,
                    onClick = {
                        demo = if (label == "Live") null else label
                        expanded = false
                    },
                    label = { Text(label, fontSize = 10.sp) },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    number: String,
    title: String,
    description: String,
    granted: Boolean,
    button: String,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 15.dp)) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(number, fontSize = 10.sp, color = Mint, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (granted)
                        Icon(
                            Icons.Rounded.CheckCircle,
                            "Granted",
                            tint = Mint,
                            modifier = Modifier.size(16.dp),
                        )
                }
                Text(
                    description,
                    fontSize = 11.sp,
                    color = Muted,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
                    Text(if (granted) "Manage access" else button, fontSize = 12.sp)
                }
            }
        }
        HorizontalDivider(color = Silver.copy(alpha = .07f))
    }
}

@Composable
private fun PresetStrip(s: HaloSettings, update: ((HaloSettings) -> HaloSettings) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ThemePreset.entries.forEach { preset ->
            Column(
                Modifier.clickable { update { it.preset(preset) } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(48.dp)
                        .border(
                            if (s.theme == preset) 1.5.dp else .5.dp,
                            if (s.theme == preset) Mint else Silver.copy(alpha = .15f),
                            CircleShape,
                        )
                        .padding(5.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(preset.tint).copy(alpha = 1f),
                                    Color.White.copy(alpha = preset.light + .1f),
                                )
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.width(24.dp)
                            .height(10.dp)
                            .border(.7.dp, Silver.copy(alpha = preset.rim), CircleShape)
                            .background(Color(preset.tint), CircleShape)
                    )
                }
                Text(
                    preset.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 9.sp,
                    color = if (s.theme == preset) Silver else Muted,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 9.sp, letterSpacing = 1.7.sp, color = Muted)
        action?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = Mint,
                modifier = Modifier.clickable(onClick = onClick),
            )
        }
    }
}

@Composable
private fun StudioScreen(
    g: HaloGraph,
    s: HaloSettings,
    update: ((HaloSettings) -> HaloSettings) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Heading(
        "THE MATERIAL STUDIO",
        "Find your form.",
        "Every edge. Every reflection. Entirely yours.",
    )
    LivePreview(g, s)
    SectionLabel("MATERIAL PALETTES")
    PresetStrip(s, update)
    ControlGroup("Optical quality") {
        ChoiceRow(GlassQuality.entries, s.quality, { it.name }) { q ->
            update { it.copy(quality = q) }
        }
        Text(
            "AUTO adapts to graphics APIs, low-memory devices and battery saver. Overlay glass never samples other apps. Refraction and frost apply to the app-owned preview backdrop on API 33+.",
            fontSize = 11.sp,
            color = Muted,
            lineHeight = 18.sp,
        )
        ControlSlider("Opacity", s.opacity, .65f..1f) { v -> update { it.copy(opacity = v) } }
        ControlSlider("Edge illumination", s.rim, 0f..1f) { v -> update { it.copy(rim = v) } }
        ControlSlider("Refraction · in app", s.refraction, 0f..1f) { v ->
            update { it.copy(refraction = v) }
        }
        ControlSlider("Frost · in app", s.blur, 0f..1f) { v -> update { it.copy(blur = v) } }
        ControlSlider("Chromatic edge", s.dispersion, 0f..1f) { v ->
            update { it.copy(dispersion = v) }
        }
        ControlSlider("Material brightness", s.brightness, 0f..0.6f) { v ->
            update { it.copy(brightness = v) }
        }
        ControlSlider("Shadow softness", s.shadow, 0f..1f) { v -> update { it.copy(shadow = v) } }
        ControlSlider("Background contrast", s.contrast, 0f..1f) { v ->
            update { it.copy(contrast = v) }
        }
        ChoiceRow(
            listOf("Preset", "Silver", "Smoke", "Sage", "Blue"),
            when (s.customTint) {
                0xFF445A52L -> "Sage"
                0xFF384553L -> "Blue"
                0xFF555D64L -> "Silver"
                0xFF292C32L -> "Smoke"
                else -> "Preset"
            },
            { it },
        ) { t ->
            update {
                it.copy(
                    customTint =
                        when (t) {
                            "Silver" -> 0xFF555D64
                            "Smoke" -> 0xFF292C32
                            "Sage" -> 0xFF445A52
                            "Blue" -> 0xFF384553
                            else -> 0
                        }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        g.settings.saveCustom()
                        message = "Custom material saved on this phone."
                    }
                }
            ) {
                Text("Save custom", fontSize = 11.sp)
            }
            TextButton(
                onClick = {
                    scope.launch {
                        message =
                            if (g.settings.restoreCustom()) "Custom material restored."
                            else "Save a custom material first."
                    }
                }
            ) {
                Text("Restore", fontSize = 11.sp)
            }
        }
        message?.let { Text(it, fontSize = 11.sp, color = Mint) }
    }
    ControlGroup("Position & silhouette") {
        Text(
            "Offsets start inside Android's status-bar and cutout safe area. HALO stays below protected system controls; this does not change the hardware cutout.",
            fontSize = 11.sp,
            color = Muted,
        )
        ControlSlider("Horizontal offset", s.x, -140f..140f, "dp") { v ->
            update { it.copy(x = v) }
        }
        ControlSlider("Vertical offset", s.y, 0f..180f, "dp") { v -> update { it.copy(y = v) } }
        ControlSlider("Compact width", s.width, 120f..280f, "dp") { v ->
            update { it.copy(width = v) }
        }
        ControlSlider("Compact height", s.height, 40f..72f, "dp") { v ->
            update { it.copy(height = v) }
        }
        ControlSlider("Expanded width", s.expandedWidth, 260f..440f, "dp") { v ->
            update { it.copy(expandedWidth = v) }
        }
        ControlSlider("Corner radius", s.radius, 16f..60f, "dp") { v ->
            update { it.copy(radius = v) }
        }
        CalibrationPreview(s)
        TextButton(
            onClick = {
                update {
                    it.copy(
                        x = 0f,
                        y = 6f,
                        width = 164f,
                        height = 44f,
                        expandedWidth = 352f,
                        radius = 30f,
                    )
                }
            }
        ) {
            Text("Reset calibration")
        }
    }
    ControlGroup("A softer kind of motion") {
        ChoiceRow(MotionPreset.entries, s.motion, { it.name }) { m ->
            update { it.copy(motion = m) }
        }
        ControlSlider("Animation intensity", s.intensity, 0f..1.5f) { v ->
            update { it.copy(intensity = v) }
        }
        ControlSlider("Auto-collapse", s.collapseSeconds.toFloat(), 3f..60f, "sec") { v ->
            update { it.copy(collapseSeconds = v.toInt()) }
        }
        ToggleRow(
            "Reduced motion",
            "Instant morphs and settled playback indicators.",
            s.reducedMotion,
        ) { v ->
            update { it.copy(reducedMotion = v) }
        }
        ToggleRow(
            "Haptic feedback",
            "A subtle response when you interact with the island.",
            s.haptics,
        ) { v ->
            update { it.copy(haptics = v) }
        }
    }
    ControlGroup("Your listening experience") {
        ControlSlider("Artwork size", s.artSize, 52f..90f, "dp") { v ->
            update { it.copy(artSize = v) }
        }
        ToggleRow(
            "Playback activity",
            "Synthetic playing indicator; not microphone or audio capture.",
            s.waveform,
        ) { v ->
            update { it.copy(waveform = v) }
        }
        ToggleRow(
            "Source app icon",
            "Identify the app receiving playback commands.",
            s.sourceIcon,
        ) { v ->
            update { it.copy(sourceIcon = v) }
        }
        ToggleRow(
            "Show paused sessions",
            "Keep controls available when music pauses.",
            s.showPaused,
        ) { v ->
            update { it.copy(showPaused = v) }
        }
        ToggleRow(
            "Expand when playback begins",
            "Then collapse after your chosen interval.",
            s.autoExpand,
        ) { v ->
            update { it.copy(autoExpand = v) }
        }
        ToggleRow(
            "Follow playing source",
            "When off, keep your manually selected active session.",
            s.followPlaying,
        ) { v ->
            update { it.copy(followPlaying = v) }
        }
    }
}

@Composable
private fun EventsScreen(
    g: HaloGraph,
    s: HaloSettings,
    update: ((HaloSettings) -> HaloSettings) -> Unit,
) {
    val timer by g.timer.timer.collectAsStateWithLifecycle()
    val remaining by g.timer.remaining.collectAsStateWithLifecycle()
    val watch by g.timer.watch.collectAsStateWithLifecycle()
    val elapsed by g.timer.watchTime.collectAsStateWithLifecycle()
    var minutes by rememberSaveable { mutableStateOf("5") }
    Heading(
        "SMALL MOMENTS, BEAUTIFULLY HELD",
        "In the moment.",
        "A little focus. A little energy. Nothing missed.",
    )
    ControlGroup("Focus timer") {
        Text(
            durationLabel(remaining),
            fontSize = 54.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-2).sp,
        )
        OutlinedTextField(
            minutes,
            { minutes = it.filter(Char::isDigit).take(4) },
            label = { Text("Minutes · 1 to 1440") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    g.timer.start((minutes.toLongOrNull() ?: 5).coerceIn(1, 1440) * 60000)
                },
                enabled = s.timerEnabled,
            ) {
                Text(if (timer.active) "Restart" else "Start")
            }
            if (timer.active)
                OutlinedButton(onClick = g.timer::pauseResume) {
                    Text(if (timer.running) "Pause" else "Resume")
                }
            if (timer.total > 0)
                TextButton(
                    onClick = {
                        g.timer.cancel()
                        g.collapse()
                    }
                ) {
                    Text("Clear")
                }
        }
        Text(
            "Runs while another app is open. Keep HALO enabled for foreground timer monitoring. An inexact Android alarm is the fallback after process death; battery restrictions may delay it.",
            fontSize = 11.sp,
            color = Muted,
            lineHeight = 18.sp,
        )
        ToggleRow(
            "Timer alerts",
            "Allow local countdowns and their island indicator.",
            s.timerEnabled,
        ) { v ->
            update { it.copy(timerEnabled = v) }
        }
        ToggleRow(
            "Audible completion",
            "Uses Android's timer notification channel; system channel settings take precedence.",
            s.audibleTimer,
        ) { v ->
            update { it.copy(audibleTimer = v) }
        }
    }
    ControlGroup("Stopwatch") {
        Text(durationLabel(elapsed), fontSize = 44.sp, fontWeight = FontWeight.Light)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = g.timer::toggleWatch) {
                Text(if (watch.running) "Pause" else if (elapsed > 0) "Resume" else "Start")
            }
            TextButton(onClick = g.timer::resetWatch) { Text("Reset") }
        }
        Text(
            "Uses elapsed real time, including when HALO is backgrounded. Stops at the last saved total after a device restart.",
            fontSize = 11.sp,
            color = Muted,
        )
    }
    ControlGroup("System moments") {
        ToggleRow(
            "Charging island",
            "Briefly display real battery state when connected or disconnected.",
            s.charging,
        ) { v ->
            update { it.copy(charging = v) }
        }
        ToggleRow("Low & full battery", "A quiet update at 15% and 100%.", s.lowBattery) { v ->
            update { it.copy(lowBattery = v) }
        }
        ToggleRow(
            "Playback start events",
            "Allow automatic media expansion if enabled in Studio.",
            s.mediaEvents,
        ) { v ->
            update { it.copy(mediaEvents = v) }
        }
        ControlSlider("Event duration", s.eventSeconds.toFloat(), 2f..15f, "sec") { v ->
            update { it.copy(eventSeconds = v.toInt()) }
        }
    }
    ControlGroup("Notification privacy") {
        Text(
            "Optional, off by default. Requires notification access. Locked-device previews are hidden. Private, message, email, call and code-like contents are never expanded.",
            fontSize = 11.sp,
            color = Muted,
            lineHeight = 18.sp,
        )
        ChoiceRow(
            PreviewPrivacy.entries,
            s.privacy,
            {
                when (it) {
                    PreviewPrivacy.OFF -> "Off"
                    PreviewPrivacy.ICON -> "Icon only"
                    PreviewPrivacy.APP_NAME -> "App name"
                    PreviewPrivacy.FULL -> "Public preview"
                }
            },
        ) { v ->
            update { it.copy(privacy = v) }
        }
        var excluded by remember(s.exclusions) { mutableStateOf(s.exclusions) }
        OutlinedTextField(
            excluded,
            { excluded = it },
            label = { Text("Excluded app package names") },
            placeholder = { Text("com.example.app, com.example.other") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        TextButton(
            onClick = {
                val value = excluded
                update { it.copy(exclusions = value) }
            }
        ) {
            Text("Save exclusions")
        }
        Text(
            "Find an app's package name in its store URL or app details. Notification text stays in memory only and is discarded after the preview.",
            fontSize = 11.sp,
            color = Muted,
        )
    }
}

@Composable
private fun SettingsScreen(
    s: HaloSettings,
    p: PermissionState,
    update: ((HaloSettings) -> HaloSettings) -> Unit,
    onOverlay: () -> Unit,
    onListener: () -> Unit,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
    onApp: () -> Unit,
) {
    Heading("BUILT TO STAY PERSONAL", "Quietly capable.", "On your phone. Under your control.")
    ControlGroup("Access & control") {
        PermissionRow(
            "01",
            "Display over other apps",
            "Revoke this any time in Android settings.",
            p.overlay,
            "Manage overlay",
            onOverlay,
        )
        PermissionRow(
            "02",
            "Media notification access",
            "Used for MediaSession discovery and optional notification previews. No notification history is stored.",
            p.listener,
            "Manage access",
            onListener,
        )
        PermissionRow(
            "03",
            "App notifications",
            "Service controls and timer completion.",
            p.notifications,
            "Manage permission",
            onNotifications,
        )
        ToggleRow(
            "Reminder after restart",
            "Post a notification to reopen HALO. Android 15+ startup rules mean the overlay is not silently relaunched from boot.",
            s.bootReminder,
        ) { v ->
            update { it.copy(bootReminder = v) }
        }
    }
    ControlGroup("Vivo / Funtouch OS") {
        Text(
            "Start HALO while this app is visible. If Vivo stops it, check your model's background activity, battery optimization and auto-start controls. Labels and behavior vary by device. Allow HALO to run in the background where available; lock it in Recents if your OS supports that option.",
            fontSize = 13.sp,
            color = Muted,
            lineHeight = 21.sp,
        )
        OutlinedButton(onClick = onBattery) {
            Text("Battery optimization settings", fontSize = 12.sp)
        }
        TextButton(onClick = onApp) { Text("HALO app details") }
        Text(
            "Background persistence cannot be guaranteed on every OEM. If Android hides the island on a secure screen, HALO respects that choice. The overlay always hides while the phone is locked.",
            fontSize = 11.sp,
            color = Muted,
            lineHeight = 18.sp,
        )
    }
    ControlGroup("Private by design") {
        Text(
            "No internet permission. No analytics, ads, accounts, screen recording or accessibility service. No microphone access. Media, notifications and preferences stay on this device.",
            fontSize = 13.sp,
            color = Silver,
            lineHeight = 21.sp,
        )
        Text(
            "Glass in other apps is a carefully layered optical material. It does not read or refract their pixels. Android can hide application overlays on sensitive surfaces. Per-app automatic hiding is not offered because HALO does not request usage-access or inspect other apps.",
            fontSize = 11.sp,
            color = Muted,
            lineHeight = 18.sp,
        )
    }
    ControlGroup("HALO 1.0") {
        Text("Your phone. Alive.", style = MaterialTheme.typography.titleLarge)
        Text(
            "Native Kotlin & Jetpack Compose · Android 8+\nDesigned for Android 15 and newer.\nUses the device's licensed system typeface. No proprietary fonts or artwork are bundled.",
            fontSize = 11.sp,
            color = Muted,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun ControlGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = Silver.copy(alpha = .08f))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 10.dp, bottom = 3.dp),
        )
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(
                description,
                fontSize = 10.sp,
                color = Muted,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Switch(value, onCheckedChange = onChange)
    }
}

@Composable
private fun ControlSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String = "",
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 12.sp, color = Silver)
            Text(
                if (unit.isBlank()) "${(value*100).toInt()}%" else "${value.toInt()} $unit",
                fontSize = 11.sp,
                color = Muted,
            )
        }
        Slider(
            value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onChoose: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        values.forEach { v ->
            FilterChip(
                selected == v,
                onClick = { onChoose(v) },
                label = { Text(label(v), fontSize = 10.sp) },
            )
        }
    }
}

@Composable
private fun CalibrationPreview(s: HaloSettings) {
    Box(
        Modifier.fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF11171B)),
        contentAlignment = Alignment.TopCenter,
    ) {
        val density = LocalDensity.current.density
        Canvas(Modifier.fillMaxSize()) {
            val factor = size.width / 420f
            val cutout = androidx.compose.ui.geometry.Offset(size.width / 2, 16 * factor)
            drawCircle(Muted.copy(alpha = .35f), 4 * factor, cutout)
            drawLine(
                Muted.copy(alpha = .2f),
                androidx.compose.ui.geometry.Offset(0f, 32 * factor),
                androidx.compose.ui.geometry.Offset(size.width, 32 * factor),
                1f,
            )
            val p =
                clampPosition(
                    420,
                    210,
                    s.width.toInt(),
                    s.height.toInt(),
                    s.x.toInt(),
                    s.y.toInt(),
                    SafeInsets(top = 32),
                )
            drawRoundRect(
                Mint.copy(alpha = .12f),
                androidx.compose.ui.geometry.Offset(p.x * factor, p.y * factor),
                androidx.compose.ui.geometry.Size(p.width * factor, s.height * factor),
                androidx.compose.ui.geometry.CornerRadius(s.radius * factor),
            )
            drawRoundRect(
                Mint.copy(alpha = .8f),
                androidx.compose.ui.geometry.Offset(p.x * factor, p.y * factor),
                androidx.compose.ui.geometry.Size(p.width * factor, s.height * factor),
                androidx.compose.ui.geometry.CornerRadius(s.radius * factor),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f),
            )
        }
        Text(
            "SAFE-AREA CALIBRATION · ILLUSTRATION",
            fontSize = 8.sp,
            color = Muted,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }
}
