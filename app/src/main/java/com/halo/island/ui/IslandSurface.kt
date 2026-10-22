@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.halo.island.ui

import android.media.session.PlaybackState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.island.core.*
import com.halo.island.data.MediaTrack
import com.halo.island.halo
import com.halo.island.system.*
import kotlin.math.*
import kotlinx.coroutines.*

@Composable
fun IslandSurface(
    mode: IslandMode,
    settings: HaloSettings,
    media: MediaTrack?,
    timer: TimerState,
    remaining: Long,
    battery: BatteryState,
    notification: NotificationPreview?,
    overlay: Boolean = false,
    backdrop: AppBackdrop? = null,
    maxWidthDp: Float? = null,
    timerInteractive: Boolean = true,
    onTap: () -> Unit = {},
    onCollapse: () -> Unit = {},
    onQuick: () -> Unit = {},
    onToggle: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onSeekingChange: (Boolean) -> Unit = {},
    onSource: () -> Unit = {},
    onTimerPause: () -> Unit = {},
    onTimerCancel: () -> Unit = {},
    onStartTimer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onStop: () -> Unit = {},
    onNotification: () -> Unit = {},
    onInteraction: () -> Unit = {},
    onSize: (Int, Int) -> Unit = { _, _ -> },
) {
    if (mode == IslandMode.DISMISSED) return
    val expanded =
        mode in
            setOf(
                IslandMode.MEDIA_EXPANDED,
                IslandMode.MEDIA_SEEKING,
                IslandMode.TIMER_EXPANDED,
                IslandMode.QUICK_ACTIONS,
            )
    val panel =
        expanded ||
            mode == IslandMode.NOTIFICATION_PREVIEW ||
            mode == IslandMode.CHARGING ||
            mode == IslandMode.BATTERY_STATUS
    val configuration = LocalConfiguration.current
    val maxWidth = maxWidthDp ?: (configuration.screenWidthDp - 24).coerceAtLeast(200).toFloat()
    val targetWidth = (if (panel) settings.expandedWidth else settings.width).coerceAtMost(maxWidth)
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val targetHeight =
        (when (mode) {
                IslandMode.MEDIA_EXPANDED,
                IslandMode.MEDIA_SEEKING -> 232f * fontScale
                IslandMode.TIMER_EXPANDED -> 200f * fontScale
                IslandMode.QUICK_ACTIONS -> 224f * fontScale
                IslandMode.NOTIFICATION_PREVIEW -> 126f * fontScale
                IslandMode.CHARGING,
                IslandMode.BATTERY_STATUS -> 106f * fontScale
                else -> settings.height
            })
            .coerceAtMost((configuration.screenHeightDp - 80).coerceAtLeast(100).toFloat())
    val reduced = settings.reducedMotion || settings.intensity == 0f
    val motion = settings.motion
    val spec: FiniteAnimationSpec<Float> =
        if (reduced) snap()
        else spring(motion.damping, (motion.stiffness / (settings.intensity.coerceAtLeast(.3f))))
    val width by animateFloatAsState(targetWidth, spec, label = "Island width")
    val height by animateFloatAsState(targetHeight, spec, label = "Island height")
    val radius by
        animateFloatAsState(
            if (panel) settings.radius else minOf(settings.radius, settings.height / 2),
            spec,
            label = "Continuous curvature",
        )
    val accent by
        animateColorAsState(
            media?.let { Color(it.accent) } ?: Mint,
            if (reduced) snap() else tween(800),
            label = "Artwork light",
        )
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val compression by
        animateFloatAsState(if (pressed && !reduced) .975f else 1f, spec, label = "Gel response")
    val haptic = LocalHapticFeedback.current
    fun feedback(action: () -> Unit) {
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onInteraction()
        action()
    }
    Box(
        Modifier.width(width.dp)
            .height(height.dp)
            .onSizeChanged { onSize(it.width, it.height) }
            .graphicsLayer {
                scaleX = compression
                scaleY = compression
            }
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                onClick = { feedback(onTap) },
                onLongClick = { feedback(onQuick) },
            )
            .semantics { contentDescription = "HALO ${mode.name.lowercase().replace('_',' ')}" }
    ) {
        GlassRenderer(settings, radius, accent, overlay, backdrop, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(radius.dp))) {
            AnimatedContent(
                targetState =
                    if (mode == IslandMode.MEDIA_SEEKING) IslandMode.MEDIA_EXPANDED else mode,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else
                        (fadeIn(tween(180, 70)) +
                            slideInVertically(tween(250)) { it / 12 }) togetherWith
                            fadeOut(tween(100))
                },
                label = "Context continuity",
            ) { visible ->
                when (visible) {
                    IslandMode.MEDIA_EXPANDED,
                    IslandMode.MEDIA_SEEKING ->
                        if (media != null)
                            MediaExpanded(
                                media,
                                settings,
                                accent,
                                { feedback(onToggle) },
                                { feedback(onPrevious) },
                                { feedback(onNext) },
                                onSeek,
                                onSeekingChange,
                                { feedback(onSource) },
                                onCollapse,
                                onInteraction,
                            )
                    IslandMode.TIMER_EXPANDED ->
                        TimerExpanded(
                            timer,
                            remaining,
                            accent,
                            { feedback(onTimerPause) },
                            { feedback(onTimerCancel) },
                            onCollapse,
                            timerInteractive,
                        )
                    IslandMode.CHARGING,
                    IslandMode.BATTERY_STATUS -> BatteryPanel(battery, accent, settings)
                    IslandMode.NOTIFICATION_PREVIEW ->
                        NotificationPanel(notification, onNotification, onCollapse)
                    IslandMode.QUICK_ACTIONS ->
                        QuickActions(
                            battery,
                            timer,
                            remaining,
                            settings,
                            { feedback(onStartTimer) },
                            { feedback(onOpenSettings) },
                            { feedback(onStop) },
                            onCollapse,
                        )
                    else -> CompactIsland(media, timer, remaining, settings, accent)
                }
            }
        }
    }
}

@Composable
private fun CompactIsland(
    media: MediaTrack?,
    timer: TimerState,
    remaining: Long,
    s: HaloSettings,
    accent: Color,
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (media != null && (media.playing || s.showPaused)) {
            Artwork(media, 28.dp)
            if (timer.active)
                Text(
                    durationLabel(remaining),
                    color = Silver,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            else Spacer(Modifier.weight(1f))
            if (s.waveform) ActivityBars(media.playing && !s.reducedMotion, s, accent)
            else Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(18.dp))
        } else if (timer.active) {
            Icon(Icons.Rounded.Timer, null, tint = accent, modifier = Modifier.size(20.dp))
            Text(
                durationLabel(remaining),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Silver,
            )
            Box(Modifier.size(5.dp).background(if (timer.running) accent else Muted, CircleShape))
        } else {
            Box(Modifier.size(6.dp).background(accent.copy(alpha = .65f), CircleShape))
            Text(
                "H A L O",
                fontSize = 10.sp,
                color = Silver.copy(alpha = .8f),
                fontWeight = FontWeight.Medium,
            )
            Icon(Icons.Rounded.Add, null, tint = Muted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ActivityBars(playing: Boolean, s: HaloSettings, accent: Color) {
    val powerSaver by LocalContext.current.halo.battery.powerSaver.collectAsStateWithLifecycle()
    // Playback-reactive synthetic activity, deliberately not an audio spectrum.
    if (playing && s.quality != GlassQuality.LITE && !powerSaver) {
        val transition = rememberInfiniteTransition(label = "Playback activity")
        val phase by
            transition.animateFloat(
                0f,
                (2 * PI).toFloat(),
                infiniteRepeatable(tween(1600, easing = LinearEasing)),
                label = "Activity phase",
            )
        Canvas(
            Modifier.size(24.dp, 22.dp).semantics {
                contentDescription = "Playing; animated activity indicator"
            }
        ) {
            repeat(4) { i ->
                val h = size.height * (.25f + .65f * abs(sin(phase + i * .8f)))
                drawLine(
                    accent,
                    Offset(size.width * i / 4 + 2, (size.height - h) / 2),
                    Offset(size.width * i / 4 + 2, (size.height + h) / 2),
                    3.dp.toPx(),
                    StrokeCap.Round,
                )
            }
        }
    } else
        Icon(
            if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.Pause,
            "${if(playing)"Playing" else "Paused"}",
            tint = accent,
            modifier = Modifier.size(24.dp),
        )
}

@Composable
fun Artwork(track: MediaTrack?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = track?.artwork
    Crossfade(
        targetState = bitmap,
        animationSpec = tween(350),
        label = "Album artwork",
        modifier = modifier.size(size).clip(RoundedCornerShape((size.value * .21f).dp)),
    ) { art ->
        if (art != null)
            Image(
                art.asImageBitmap(),
                "Album artwork",
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        else
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF314943), Color(0xFF15221F)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    tint = Mint.copy(alpha = .8f),
                    modifier = Modifier.size(size * .4f),
                )
            }
    }
}

@Composable
fun AppBadge(packageName: String, size: Dp = 18.dp) {
    val c = LocalContext.current
    val icon by
        produceState<android.graphics.Bitmap?>(null, packageName) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            c.packageManager.getApplicationIcon(packageName).toBitmap(48, 48)
                        }
                        .getOrNull()
                }
        }
    icon?.let {
        Image(
            it.asImageBitmap(),
            "Source application",
            Modifier.size(size).clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun MediaExpanded(
    track: MediaTrack,
    s: HaloSettings,
    accent: Color,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onSource: () -> Unit,
    onCollapse: () -> Unit,
    onInteraction: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(enabled = track.canOpen, onClick = onSource),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (s.sourceIcon) AppBadge(track.packageName, 14.dp)
                Text(
                    track.source.uppercase(),
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp,
                    color = Muted,
                    maxLines = 1,
                )
            }
            SmallButton(Icons.Rounded.KeyboardArrowUp, "Collapse island", onCollapse, 28.dp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 5.dp),
        ) {
            Artwork(
                track,
                s.artSize.dp,
                Modifier.clickable(enabled = track.canOpen, onClick = onSource),
            )
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                AnimatedContent(
                    track.title,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "Track title",
                ) { title ->
                    Text(
                        title,
                        color = Silver,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp,
                    )
                }
                Text(
                    track.artist.ifBlank { "Unknown artist" },
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        MediaProgress(track, accent, onSeek, onSeekingChange, onInteraction)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            SmallButton(
                Icons.Rounded.SkipPrevious,
                "Previous track",
                onPrevious,
                44.dp,
                track.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
            )
            Spacer(Modifier.width(20.dp))
            val toggle =
                track.supports(PlaybackState.ACTION_PLAY_PAUSE) ||
                    track.supports(
                        if (track.playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
                    )
            IconButton(
                onClick = onToggle,
                enabled = toggle,
                modifier = Modifier.size(50.dp).background(Silver.copy(alpha = .12f), CircleShape),
            ) {
                Icon(
                    if (track.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (track.playing) "Pause" else "Play",
                    tint = Silver.copy(alpha = if (toggle) 1f else .25f),
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(20.dp))
            SmallButton(
                Icons.Rounded.SkipNext,
                "Next track",
                onNext,
                44.dp,
                track.supports(PlaybackState.ACTION_SKIP_TO_NEXT),
            )
        }
    }
}

@Composable
private fun MediaProgress(
    track: MediaTrack,
    accent: Color,
    onSeek: (Long) -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onInteraction: () -> Unit,
) {
    var position by remember(track.id) { mutableLongStateOf(track.progress()) }
    var dragging by remember(track.id) { mutableStateOf(false) }
    var dragged by remember(track.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(track.id, track.position, track.updated, track.playing) {
        position = track.progress()
        while (track.playing) {
            delay(500)
            position = track.progress()
        }
    }
    DisposableEffect(track.id) { onDispose { if (dragging) onSeekingChange(false) } }
    val duration = track.duration
    val fraction =
        if (dragging) dragged else if (duration > 0) position.toFloat() / duration else 0f
    Slider(
        value = fraction.coerceIn(0f, 1f),
        onValueChange = {
            if (!dragging) onSeekingChange(true)
            dragging = true
            dragged = it
            onInteraction()
        },
        onValueChangeFinished = {
            onSeek((dragged * duration).toLong())
            position = (dragged * duration).toLong()
            dragging = false
            onSeekingChange(false)
        },
        enabled = duration > 0 && track.supports(PlaybackState.ACTION_SEEK_TO),
        modifier =
            Modifier.fillMaxWidth().height(28.dp).semantics {
                contentDescription = "Playback progress"
            },
        colors =
            SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Silver.copy(alpha = .12f),
            ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            durationLabel(if (dragging) (dragged * duration).toLong() else position),
            fontSize = 9.sp,
            color = Muted,
        )
        Text(
            if (duration > 0) durationLabel(duration) else "LIVE / UNKNOWN",
            fontSize = 9.sp,
            color = Muted,
        )
    }
}

@Composable
private fun TimerExpanded(
    timer: TimerState,
    remaining: Long,
    accent: Color,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onCollapse: () -> Unit,
    interactive: Boolean,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (timer.finished) "TIME IS YOURS" else "FOCUS TIMER",
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = Muted,
            )
            SmallButton(Icons.Rounded.KeyboardArrowUp, "Collapse", onCollapse, 24.dp)
        }
        Text(
            durationLabel(remaining),
            fontSize = 44.sp,
            fontWeight = FontWeight.Light,
            color = Silver,
            letterSpacing = (-2).sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LinearProgressIndicator(
            progress = { if (timer.total > 0) remaining.toFloat() / timer.total else 0f },
            color = accent,
            trackColor = Silver.copy(alpha = .08f),
            modifier = Modifier.fillMaxWidth().height(3.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            if (timer.active)
                OutlinedButton(onClick = onPause, enabled = interactive) {
                    Text(if (!interactive) "Sample" else if (timer.running) "Pause" else "Resume")
                }
            TextButton(onClick = onCancel) {
                Text(if (timer.finished) "Done" else "Cancel", color = Muted)
            }
        }
    }
}

@Composable
private fun BatteryPanel(b: BatteryState, accent: Color, s: HaloSettings) {
    val powerSaver by LocalContext.current.halo.battery.powerSaver.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxSize().padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            val level by
                animateFloatAsState(
                    (b.percent ?: 0) / 100f,
                    if (s.reducedMotion) snap() else tween(900),
                    label = "Battery fill",
                )
            CircularProgressIndicator(
                progress = { level },
                modifier = Modifier.fillMaxSize(),
                color = Mint,
                trackColor = Silver.copy(alpha = .1f),
                strokeWidth = 3.dp,
            )
            if (b.charging && !s.reducedMotion && s.quality != GlassQuality.LITE && !powerSaver) {
                val flow = rememberInfiniteTransition(label = "Charging energy")
                val angle by
                    flow.animateFloat(
                        0f,
                        360f,
                        infiniteRepeatable(tween(2200, easing = LinearEasing)),
                        label = "Energy orbit",
                    )
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(
                        Brush.sweepGradient(
                            listOf(Color.Transparent, Mint.copy(alpha = .1f), Silver)
                        ),
                        angle,
                        60f,
                        false,
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size =
                            androidx.compose.ui.geometry.Size(
                                size.width - 2.dp.toPx(),
                                size.height - 2.dp.toPx(),
                            ),
                        style =
                            androidx.compose.ui.graphics.drawscope.Stroke(
                                3.dp.toPx(),
                                cap = StrokeCap.Round,
                            ),
                    )
                }
            }
            Icon(
                if (b.connected) Icons.Rounded.Bolt else Icons.Rounded.BatteryStd,
                null,
                tint = Mint,
                modifier = Modifier.size(25.dp),
            )
        }
        Column {
            Text(
                "${b.percent?.toString()?:"—"}%",
                color = Silver,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                if (b.percent == 100) "Fully charged"
                else if (b.charging) "A little more energy."
                else if (b.connected) "Connected · not charging" else "Battery status",
                fontSize = 11.sp,
                color = Muted,
            )
        }
    }
}

@Composable
private fun NotificationPanel(n: NotificationPreview?, onOpen: () -> Unit, onCollapse: () -> Unit) {
    Row(
        Modifier.fillMaxSize()
            .clickable(enabled = n?.intent != null, onClick = onOpen)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        n?.let { AppBadge(it.packageName, 28.dp) }
        Column(Modifier.weight(1f)) {
            Text(n?.app ?: "Notification", fontSize = 11.sp, color = Muted, maxLines = 1)
            n?.title?.let {
                Text(
                    it,
                    color = Silver,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            n?.text?.let {
                Text(
                    it,
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (n?.title == null)
                Text(
                    if (n?.intent != null) "Tap to open" else "Notification preview",
                    fontSize = 13.sp,
                    color = Silver,
                )
        }
        SmallButton(Icons.Rounded.Close, "Dismiss preview", onCollapse, 28.dp)
    }
}

@Composable
private fun QuickActions(
    b: BatteryState,
    t: TimerState,
    remaining: Long,
    s: HaloSettings,
    onTimer: () -> Unit,
    onSettings: () -> Unit,
    onStop: () -> Unit,
    onCollapse: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("YOUR PHONE. ALIVE.", fontSize = 10.sp, color = Muted, letterSpacing = 1.sp)
            SmallButton(Icons.Rounded.KeyboardArrowUp, "Collapse", onCollapse, 24.dp)
        }
        Text(
            "${b.percent?.toString()?:"—"}% battery${if(t.active)"  ·  ${durationLabel(remaining)} left" else ""}",
            fontSize = 13.sp,
            color = Silver,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.timerEnabled)
                ActionTile(Icons.Rounded.Timer, "5 min", onTimer, Modifier.weight(1f))
            ActionTile(Icons.Rounded.Tune, "HALO", onSettings, Modifier.weight(1f))
            ActionTile(Icons.Rounded.PowerSettingsNew, "Stop", onStop, Modifier.weight(1f))
        }
        Text(
            "Long press for actions. Tap outside to collapse.",
            fontSize = 9.sp,
            color = Muted,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun ActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Silver.copy(alpha = .06f))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Silver, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(7.dp))
        Text(label, fontSize = 11.sp, color = Silver)
    }
}

@Composable
fun SmallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(
            icon,
            label,
            tint = Silver.copy(alpha = if (enabled) .9f else .22f),
            modifier = Modifier.size((size.value * .53f).dp),
        )
    }
}
