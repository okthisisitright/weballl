# HALO

**Your phone. Alive.** A native Android floating island for real media controls, local timers, and quiet system moments. Application ID: `com.halo.island`. Android 8.0+ (API 26); targets Android 16 (API 36), with explicit Android 15 foreground-service handling.

## What is implemented

- A real `TYPE_APPLICATION_OVERLAY` window, hosted by a user-started `specialUse` foreground service. It works outside the activity, wraps its visible capsule/panel, receives outside-touch collapse events, and leaves the rest of the display interactive. The service notification and expanded quick actions include Stop.
- Active cross-app `MediaSession` discovery through an explicitly authorized `NotificationListenerService`. Playing-session selection, manual source selection, metadata/artwork updates, local artwork color extraction, source opening, and capability-gated play/pause, previous/next, and seek. Progress uses playback timestamps and speed. Missing art, live/unknown durations, unseekable tracks, disconnects, and listener revocation have fallbacks. Playback bars are synthetic activity indicators, not captured audio.
- Spring-driven capsule/panel dimensions, continuous corner changes, press compression, context crossfades, artwork crossfades, and interpolated artwork lighting. Reduced motion and battery-aware graphics fallbacks.
- Real charging/disconnection and low/full battery events, with a percentage ring and optional energy orbit. No invented wattage or time-to-full values.
- Durable local countdown timers (start, pause/resume, cancel, completion notification) and a monotonic stopwatch. Timers use elapsed time within a boot, bounded wall-clock fallback after reboot, and an **inexact** `AlarmManager` fallback. Exact alarm access is not requested; OS/OEM restrictions can delay fallback alerts.
- Optional notification previews: **off by default**, icon only, app name, or public preview; package exclusions; no locked-device previews or stored notification history. Private/message/email/call/code-like text is filtered. Notification taps use the supplied `PendingIntent`.
- Six distinct material presets, five graphics tiers, optical controls, size/position calibration, five motion presets, media behavior settings, and a saved custom configuration. Settings are stored with AndroidX DataStore.
- Home, Studio, Events, and Settings, with a shared live rendering component. Explicitly labeled samples remain in the in-app preview and never feed fake data into the real overlay.
- Optional reboot reminder, permission recovery, Vivo/Funtouch guidance, launcher/adaptive/themed icons, and GitHub Actions APK builds plus Android 15 emulator integration tests.

## Build and install

Requirements: **JDK 17**, Android SDK platform 36, and accepted Android SDK licenses. Gradle/AGP install missing build tools when allowed. The Gradle 8.13 wrapper is included and its distribution SHA-256 is pinned. AGP 8.13.2, Kotlin/Compose compiler 2.2.21, Compose BOM 2025.11.01.

```sh
# Set ANDROID_HOME to your installed SDK, or set sdk.dir in local.properties.
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed automatically with the local Android debug key. **Never use that key for a production release.** For a release, create a private signing key outside the repository, provide signing configuration via secure local/CI settings, and build `assembleRelease`. No signing keys are committed.

### APK from GitHub Actions

Open this repository's **Actions → Build HALO Android**, choose a successful run for your branch/commit, and download **HALO-Android-debug**. Extract `HALO-Android-debug.apk` from the artifact ZIP. A `.sha256` file is included. Artifacts require a successful workflow run; configuration alone does not prove CI passed.

See [verification](docs/VERIFICATION.md) for actual local build/test results and device checks, and [device testing](docs/DEVICE_TESTING.md) for the acceptance checklist.

## Enable on Vivo Android 15 / Funtouch OS

1. Install and open HALO. Review **Display over other apps**, open Android settings, and allow HALO. Return to HALO; permission status is checked again.
2. Review **Connect your music** and explicitly enable HALO's notification-listener access. This is sensitive access; all metadata and notification data remain on device. Notification previews remain **off** until enabled separately in Events.
3. Optionally allow app notifications for the persistent Stop control and timer alerts. The foreground service can run if notification permission is denied, but Android's service status UI then differs.
4. Tap **Enable HALO while the activity is visible**. Play music in an app that exposes an active Android media session. Tap the capsule to expand; hold for quick actions. Source selection is available on Home when multiple sessions are active.
5. Adjust Studio's offsets and dimensions for your device. Offsets respect status-bar/cutout insets; HALO cannot replace your status bar or change the hardware cutout.
6. If Funtouch stops HALO, inspect **background activity**, **battery optimization**, and **auto-start** controls in your model's settings. Settings → Vivo help links to public Android battery/app settings. Labels vary; persistence is not guaranteed. The optional reboot reminder asks you to reopen HALO rather than silently starting a background foreground service.
7. Disable through Home, the persistent service notification, or the island's Stop action. Revoke special access through Android settings at any time.

## Rendering: two honest environments

HALO uses an app-owned `GlassRenderer`, rather than bundling a third-party glass engine. Research compared [Kyant0/Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) and [LiquidGlass](https://github.com/Abdullajon1881/LiquidGlass), their documented recording/sampling pipelines and Apache 2.0 licenses, alongside the [liquid glass implementation guide](https://github.com/sitong-zhang/apple-liquid-glass-guide) and [Impeccable](https://github.com/pbakaus/impeccable) design guidance. No source/assets from those projects are vendored. The guide's API-level statements are not treated as authoritative: Android `RuntimeShader` requires **API 33**.

**Inside HALO:** on API 33+, High/Ultra use an AGSL rounded-rectangle lens to refract a real **app-owned studio backdrop bitmap**, with global-coordinate sampling, limited spatial frost samples, RGB dispersion, tint, and optical rims. This does not promise arbitrary composable-content capture. Other tiers/API levels use an intentional layered material.

**Over other apps:** Android does not provide their backdrop pixels to an ordinary overlay. HALO uses smoke tint, directional highlights, multiple rim layers, chromatic edge cues, artwork-colored light, and shadows. It does **not** blur or refract the underlying app. Ultra does not change that. Opacity is capped at the window level for Android's untrusted-touch safety. No screen recording, accessibility screenshots, or hidden APIs.

Auto quality uses API capability, low-memory classification, and battery-saver state; this is a heuristic, **not a measured GPU benchmark**. Lite omits the backdrop shader and secondary optical layers. No continuous idle animation is scheduled. Playback activity and transient charging animation are conditional; screen-off detaches/disposes the overlay composition.

## Architecture

A single app module with separated packages keeps the first release inspectable without unnecessary build modules:

| Package | Responsibility |
|---|---|
| `core` | Pure Kotlin event reducer, priorities, timer/stopwatch models, session selection, progress/formatting, settings codec and window clamping |
| `data` | DataStore settings; persistent timers + Android alarm scheduling; media callbacks, bounded artwork cache and background color extraction |
| `system` | Overlay window lifecycle, permission mapping, notification listener/privacy bridge, battery broadcasts, notification channels and receivers |
| `ui` | Shared Compose island, AGSL/material renderer, motion tokens, dashboard/studio/events/onboarding/help |
| `HaloGraph` | Main-thread event coordinator and shared StateFlows; transient contexts expire back to the surviving media/timer context |

Listener callbacks drive session updates; there is no active-session polling loop. The expanded progress component ticks locally at 500 ms. Timer/stopwatch tickers exist only while running. All media commands are routed to the selected controller and gated by advertised capabilities. Callbacks are unregistered on disconnect/removal. No activity is retained by long-lived repositories.

## Permissions and platform limits

- `SYSTEM_ALERT_WINDOW`: separately granted overlay access. Android can hide overlays on banking/auth/permission/security screens. HALO never tries to defeat those protections. It stays below protected status-bar controls and hides while locked.
- Notification listener service binding: controlled by Android's special-access settings, used to discover MediaSessions and optional notification events. No privileged `MEDIA_CONTENT_CONTROL` request.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`: ongoing user-enabled interactive island. The manifest declares the subtype purpose. A store submission requires the applicable foreground-service declaration/review; a type declaration is not a guarantee of store approval.
- `POST_NOTIFICATIONS` (Android 13+): optional service controls and timer completion notifications.
- `RECEIVE_BOOT_COMPLETED`: reschedule persisted inexact timer fallback and optionally post a reopening reminder. Does not start the overlay from boot.
- **No INTERNET, microphone, accessibility, usage-access, media projection, exact-alarm, storage, or privileged media permissions.** Android 15's visible-overlay foreground-service exemption is not used to bypass startup rules; starting is an explicit action in the visible activity. Service restart is `START_NOT_STICKY`.

Spotify, YouTube Music, VLC, Poweramp and other players are supported **through their public sessions**, without player-specific APIs. Availability and advertised actions depend on the source, its playback mode, ads and OS restrictions. Compatibility with each named player and Vivo hardware needs real-device verification. Text redaction and OEM notification policies may limit preview contents.

Per-app automatic hiding and lock-screen overlays are intentionally not exposed: HALO does not inspect foreground applications and always hides on lock. Timer fallback is not exact and can be delayed after process death/doze. A running stopwatch does not reconstruct the unsaved running interval across a device reboot. Settings/persisted timer data remain local; backup and device transfer are excluded.

See [third-party notices](THIRD_PARTY_NOTICES.md). HALO uses Android's licensed system sans-serif rather than redistributing Apple's fonts or external album artwork.
