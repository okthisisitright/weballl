# Device verification

Do not treat a successful APK build as proof of overlay behavior on Vivo hardware. Use a separate test phone/emulator, and grant access deliberately. Nothing below requires root or accessibility access for HALO.

## Automated Android integration tests

With an emulator/device already connected and HALO's main/debug-test APKs built:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell appops set com.halo.island SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener com.halo.island/.system.HaloNotificationListener
adb shell pm grant com.halo.island android.permission.POST_NOTIFICATIONS
adb shell am instrument -w com.halo.island.test/androidx.test.runner.AndroidJUnitRunner
```

These shell grants are **test-device-only** substitutes for clicking Android's real settings flows. Production users must grant access themselves. Tests create an actual local MediaSession, verify discovery and pause/seek callbacks, release it and verify cleanup. They also check launch, a fresh settings repository read, overlay service survival after Home, countdown/pause/resume/completion, and an injected physical touch reaching a button in a separate test application beneath the overlay. The touch-probe activity is only in the test APK. These tests do not prove Spotify compatibility or Vivo persistence.

GitHub Actions includes an Android 15 integration job on a KVM-capable runner, with the same explicit test-only permission grants followed by Gradle `connectedDebugAndroidTest`. Its reports are uploaded separately. A configured job is not a claim that a workflow run passed.

## Acceptance checklist

- Launch HALO and return from overlay/listener settings with grant **and denial**. Check actual status updates. Enable while visible; then open Launcher, browser, messaging and a music player. The capsule must remain visible only on supported surfaces.
- Tap controls **under and outside** the overlay bounds. Confirm only the capsule/panel receives island touches. Drag down the notification shade, use gesture navigation and verify system controls remain accessible. Tap outside an expanded island and confirm collapse/touch bounds shrink. Long press → Stop, then verify no invisible overlay remains.
- Start Spotify, YouTube Music, VLC and Poweramp where available. Record app/version, session title, artwork updates, pause/resume, previous, next and seek. Live/unseekable/ad content must disable unsupported controls. Switch between two live sessions; stop/destroy each session and verify no stale island remains.
- Start a timer, leave HALO, pause/resume, cancel and let a second timer finish. Test after process recreation, screen-off and doze. Record fallback delay; do not require exact completion after OS process termination. Reboot with a timer running and verify fallback/remainder behavior.
- Connect/disconnect power; verify **real** percentage, charging/full/low events, temporary focus, and return to media/timer.
- Enable each notification privacy mode. Test a public notification, private notification, message/code content, an excluded app, and screen lock. Confirm previews are filtered, do not appear locked, and do not dismiss original notifications. Tap a valid preview to open its supplied PendingIntent.
- Change each theme/optical parameter. Confirm the real overlay changes. In-app High/Ultra on API 33+ should lens only HALO's studio backdrop; no third-party pixels should be sampled. Lite must omit lens and secondary effects. Turn battery saver on and recompose.
- Rotate and test a small/split window, cutout variations and 200% font. Check position clamping, panel scrolling, text contrast and control reachability. Test animator duration scale zero and HALO reduced motion.
- Revoke overlay access while running and notification listener access while media is active. Confirm graceful removal/clearing. Reopen the app and explicitly enable again.
- On Vivo, repeat after 15 minutes backgrounded, screen lock/unlock, battery saver, OEM task cleanup and reboot. Record specific model, Funtouch build and battery/background settings. Never mark background persistence as guaranteed.

## Performance review

Record on an actual device using Android Studio CPU/memory/system traces: idle after settling, playback compact, expanded seek, screen-off, theme changes, and charging event. Compare Lite/High. Check retained activities, allocation spikes and frame pacing. Build times and emulator CPU use do not measure phone battery consumption or GPU performance. No benchmark results are claimed without a captured run.
