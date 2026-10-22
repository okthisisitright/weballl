package com.halo.island.core

import org.junit.Assert.*
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun allPreferencesSurviveRoundtrip() {
        val s =
            HaloSettings()
                .preset(ThemePreset.CRYSTAL)
                .copy(
                    quality = GlassQuality.ULTRA,
                    motion = MotionPreset.ELASTIC,
                    x = -22f,
                    y = 32f,
                    width = 192f,
                    height = 52f,
                    expandedWidth = 400f,
                    radius = 36f,
                    artSize = 82f,
                    refraction = .7f,
                    dispersion = .8f,
                    brightness = .4f,
                    shadow = .3f,
                    blur = .8f,
                    contrast = .9f,
                    customTint = 0xFF445A52,
                    reducedMotion = true,
                    intensity = .7f,
                    collapseSeconds = 23,
                    waveform = false,
                    showPaused = false,
                    autoExpand = true,
                    sourceIcon = false,
                    followPlaying = false,
                    charging = false,
                    lowBattery = false,
                    timerEnabled = false,
                    mediaEvents = false,
                    eventSeconds = 9,
                    privacy = PreviewPrivacy.FULL,
                    exclusions = "one.app, two.app\nthree.app",
                    haptics = false,
                    audibleTimer = false,
                    bootReminder = true,
                    enabled = true,
                )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun eachPresetPersistsDistinctOptics() {
        val styles = ThemePreset.entries.map { HaloSettings().preset(it) }
        assertEquals(6, styles.map { Triple(it.opacity, it.rim, it.brightness) }.toSet().size)
        styles.forEach { assertEquals(it, SettingsCodec.decode(SettingsCodec.encode(it))) }
    }

    @Test
    fun missingAndUnknownFieldsFallBack() {
        assertEquals(HaloSettings(), SettingsCodec.decode(""))
        assertEquals(
            ThemePreset.OBSIDIAN,
            SettingsCodec.decode("theme=NO_SUCH_THEME\nunknown=42").theme,
        )
    }

    @Test
    fun corruptNumericsDoNotCreateInvisibleWindow() {
        val s =
            SettingsCodec.decode(
                "width=-900\nheight=NaN\nopacity=0\nrim=Infinity\nx=garbage\ncollapseSeconds=900"
            )
        assertEquals(120f, s.width)
        assertEquals(44f, s.height)
        assertEquals(.65f, s.opacity)
        assertEquals(.28f, s.rim)
        assertEquals(60, s.collapseSeconds)
    }

    @Test
    fun unsafeEventsStayPrivateByDefault() {
        assertEquals(PreviewPrivacy.OFF, SettingsCodec.decode("privacy=invalid").privacy)
    }
}
