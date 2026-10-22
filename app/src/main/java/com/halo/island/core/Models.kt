package com.halo.island.core

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import kotlin.math.roundToInt

enum class IslandMode {
    IDLE,
    COMPACT,
    MEDIA_COMPACT,
    MEDIA_EXPANDED,
    MEDIA_SEEKING,
    TIMER_COMPACT,
    TIMER_EXPANDED,
    CHARGING,
    BATTERY_STATUS,
    NOTIFICATION_PREVIEW,
    QUICK_ACTIONS,
    TRANSITIONING,
    DISMISSED,
}

enum class GlassQuality {
    AUTO,
    ULTRA,
    HIGH,
    BALANCED,
    LITE,
}

enum class ThemePreset(val tint: Long, val opacity: Float, val rim: Float, val light: Float) {
    OBSIDIAN(0xFF101318, .94f, .28f, .08f),
    FROST(0xFF56616A, .88f, .72f, .26f),
    GRAPHITE(0xFF292D33, .94f, .38f, .12f),
    CRYSTAL(0xFF33434A, .82f, .85f, .30f),
    AURORA(0xFF172B2C, .90f, .52f, .20f),
    MINIMAL(0xFF090A0C, .99f, .10f, .02f),
}

enum class MotionPreset(val damping: Float, val stiffness: Float) {
    SOFT(.86f, 240f),
    RESPONSIVE(.88f, 460f),
    ELASTIC(.70f, 320f),
    SNAPPY(1f, 700f),
    CALM(1f, 180f),
}

enum class PreviewPrivacy {
    OFF,
    ICON,
    APP_NAME,
    FULL,
}

data class HaloSettings(
    val theme: ThemePreset = ThemePreset.OBSIDIAN,
    val quality: GlassQuality = GlassQuality.AUTO,
    val motion: MotionPreset = MotionPreset.SOFT,
    val opacity: Float = .94f,
    val rim: Float = .28f,
    val refraction: Float = .5f,
    val dispersion: Float = .22f,
    val brightness: Float = .08f,
    val shadow: Float = .6f,
    val blur: Float = .4f,
    val contrast: Float = .75f,
    val customTint: Long = 0L,
    val x: Float = 0f,
    val y: Float = 6f,
    val width: Float = 164f,
    val height: Float = 44f,
    val expandedWidth: Float = 352f,
    val radius: Float = 30f,
    val artSize: Float = 70f,
    val reducedMotion: Boolean = false,
    val intensity: Float = 1f,
    val collapseSeconds: Int = 10,
    val waveform: Boolean = true,
    val showPaused: Boolean = true,
    val autoExpand: Boolean = false,
    val sourceIcon: Boolean = true,
    val followPlaying: Boolean = true,
    val charging: Boolean = true,
    val lowBattery: Boolean = true,
    val timerEnabled: Boolean = true,
    val mediaEvents: Boolean = true,
    val eventSeconds: Int = 5,
    val privacy: PreviewPrivacy = PreviewPrivacy.OFF,
    val exclusions: String = "",
    val haptics: Boolean = true,
    val audibleTimer: Boolean = true,
    val bootReminder: Boolean = false,
    val enabled: Boolean = false,
) {
    fun preset(p: ThemePreset) =
        copy(theme = p, opacity = p.opacity, rim = p.rim, brightness = p.light, customTint = 0L)

    fun validated() =
        copy(
            opacity = opacity.coerceIn(.65f, 1f),
            rim = rim.coerceIn(0f, 1f),
            refraction = refraction.coerceIn(0f, 1f),
            dispersion = dispersion.coerceIn(0f, 1f),
            brightness = brightness.coerceIn(0f, .6f),
            shadow = shadow.coerceIn(0f, 1f),
            blur = blur.coerceIn(0f, 1f),
            contrast = contrast.coerceIn(0f, 1f),
            x = x.coerceIn(-500f, 500f),
            y = y.coerceIn(0f, 500f),
            width = width.coerceIn(120f, 280f),
            height = height.coerceIn(40f, 72f),
            expandedWidth = expandedWidth.coerceIn(260f, 440f),
            radius = radius.coerceIn(16f, 60f),
            artSize = artSize.coerceIn(52f, 90f),
            intensity = intensity.coerceIn(0f, 1.5f),
            collapseSeconds = collapseSeconds.coerceIn(3, 60),
            eventSeconds = eventSeconds.coerceIn(2, 15),
        )
}

/** Stable named fields, tolerant of old/corrupt preferences. No credential or notification data. */
object SettingsCodec {
    fun encode(s: HaloSettings): String =
        Properties()
            .apply {
                mapOf(
                        "theme" to s.theme.toString(),
                        "quality" to s.quality.toString(),
                        "motion" to s.motion.toString(),
                        "opacity" to s.opacity.toString(),
                        "rim" to s.rim.toString(),
                        "refraction" to s.refraction.toString(),
                        "dispersion" to s.dispersion.toString(),
                        "brightness" to s.brightness.toString(),
                        "shadow" to s.shadow.toString(),
                        "blur" to s.blur.toString(),
                        "contrast" to s.contrast.toString(),
                        "customTint" to s.customTint.toString(),
                        "x" to s.x.toString(),
                        "y" to s.y.toString(),
                        "width" to s.width.toString(),
                        "height" to s.height.toString(),
                        "expandedWidth" to s.expandedWidth.toString(),
                        "radius" to s.radius.toString(),
                        "artSize" to s.artSize.toString(),
                        "reducedMotion" to s.reducedMotion.toString(),
                        "intensity" to s.intensity.toString(),
                        "collapseSeconds" to s.collapseSeconds.toString(),
                        "waveform" to s.waveform.toString(),
                        "showPaused" to s.showPaused.toString(),
                        "autoExpand" to s.autoExpand.toString(),
                        "sourceIcon" to s.sourceIcon.toString(),
                        "followPlaying" to s.followPlaying.toString(),
                        "charging" to s.charging.toString(),
                        "lowBattery" to s.lowBattery.toString(),
                        "timerEnabled" to s.timerEnabled.toString(),
                        "mediaEvents" to s.mediaEvents.toString(),
                        "eventSeconds" to s.eventSeconds.toString(),
                        "privacy" to s.privacy.toString(),
                        "exclusions" to s.exclusions.toString(),
                        "haptics" to s.haptics.toString(),
                        "audibleTimer" to s.audibleTimer.toString(),
                        "bootReminder" to s.bootReminder.toString(),
                        "enabled" to s.enabled.toString(),
                    )
                    .forEach { (key, value) -> setProperty(key, value) }
            }
            .let { p -> StringWriter().also { p.store(it, null) }.toString() }

    fun decode(text: String): HaloSettings {
        val p = Properties()
        runCatching { p.load(StringReader(text)) }
        val d = HaloSettings()
        fun f(k: String, v: Float) =
            p.getProperty(k)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: v
        fun b(k: String, v: Boolean) = p.getProperty(k)?.toBooleanStrictOrNull() ?: v
        fun i(k: String, v: Int) = p.getProperty(k)?.toIntOrNull() ?: v
        fun l(k: String, v: Long) = p.getProperty(k)?.toLongOrNull() ?: v
        fun <T : Enum<T>> e(k: String, v: T, values: Array<T>) =
            values.firstOrNull { it.name == p.getProperty(k) } ?: v
        return d.copy(
                theme = e("theme", d.theme, ThemePreset.entries.toTypedArray()),
                quality = e("quality", d.quality, GlassQuality.entries.toTypedArray()),
                motion = e("motion", d.motion, MotionPreset.entries.toTypedArray()),
                opacity = f("opacity", d.opacity),
                rim = f("rim", d.rim),
                refraction = f("refraction", d.refraction),
                dispersion = f("dispersion", d.dispersion),
                brightness = f("brightness", d.brightness),
                shadow = f("shadow", d.shadow),
                blur = f("blur", d.blur),
                contrast = f("contrast", d.contrast),
                customTint = l("customTint", d.customTint),
                x = f("x", d.x),
                y = f("y", d.y),
                width = f("width", d.width),
                height = f("height", d.height),
                expandedWidth = f("expandedWidth", d.expandedWidth),
                radius = f("radius", d.radius),
                artSize = f("artSize", d.artSize),
                reducedMotion = b("reducedMotion", d.reducedMotion),
                intensity = f("intensity", d.intensity),
                collapseSeconds = i("collapseSeconds", d.collapseSeconds),
                waveform = b("waveform", d.waveform),
                showPaused = b("showPaused", d.showPaused),
                autoExpand = b("autoExpand", d.autoExpand),
                sourceIcon = b("sourceIcon", d.sourceIcon),
                followPlaying = b("followPlaying", d.followPlaying),
                charging = b("charging", d.charging),
                lowBattery = b("lowBattery", d.lowBattery),
                timerEnabled = b("timerEnabled", d.timerEnabled),
                mediaEvents = b("mediaEvents", d.mediaEvents),
                eventSeconds = i("eventSeconds", d.eventSeconds),
                privacy = e("privacy", d.privacy, PreviewPrivacy.entries.toTypedArray()),
                exclusions = p.getProperty("exclusions", d.exclusions),
                haptics = b("haptics", d.haptics),
                audibleTimer = b("audibleTimer", d.audibleTimer),
                bootReminder = b("bootReminder", d.bootReminder),
                enabled = b("enabled", false),
            )
            .validated()
    }
}

data class MediaCandidate(val id: String, val playing: Boolean, val updatedAt: Long)

fun selectSession(
    candidates: List<MediaCandidate>,
    pinned: String?,
    followPlaying: Boolean = true,
): String? {
    candidates
        .firstOrNull { it.id == pinned && (!followPlaying || it.playing) }
        ?.let {
            return it.id
        }
    return candidates
        .sortedWith(
            compareByDescending<MediaCandidate> { it.playing }.thenByDescending { it.updatedAt }
        )
        .firstOrNull()
        ?.id
}

fun playbackPosition(
    position: Long,
    updated: Long,
    now: Long,
    speed: Float,
    playing: Boolean,
    duration: Long,
): Long {
    val p =
        position.coerceAtLeast(0) +
            (if (playing && updated > 0) ((now - updated).coerceAtLeast(0) * speed).toLong() else 0)
    return if (duration > 0) p.coerceIn(0, duration) else p.coerceAtLeast(0)
}

fun durationLabel(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

fun batteryPercent(level: Int, scale: Int): Int? =
    if (level < 0 || scale <= 0) null
    else ((level.toDouble() / scale) * 100).roundToInt().coerceIn(0, 100)

data class SafeInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

data class WindowPosition(val x: Int, val y: Int, val width: Int)

fun clampPosition(
    screenWidth: Int,
    screenHeight: Int,
    desiredWidth: Int,
    height: Int,
    x: Int,
    y: Int,
    insets: SafeInsets,
): WindowPosition {
    val available = (screenWidth - insets.left - insets.right).coerceAtLeast(1)
    val w = desiredWidth.coerceIn(1, available)
    val center = insets.left + available / 2 + x
    return WindowPosition(
        (center - w / 2).coerceIn(
            insets.left,
            (screenWidth - insets.right - w).coerceAtLeast(insets.left),
        ),
        (insets.top + y).coerceIn(
            insets.top,
            (screenHeight - insets.bottom - height).coerceAtLeast(insets.top),
        ),
        w,
    )
}

data class PermissionState(
    val overlay: Boolean,
    val listener: Boolean,
    val notifications: Boolean,
) {
    val mediaReady
        get() = overlay && listener

    val canStart
        get() = overlay
}
