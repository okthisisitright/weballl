# HALO verification — 2026-10-22

## Local build evidence

Executed against the native Android sources in this branch with JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK platform 36 and build tools 35.0.0:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

| Check | Actual result |
|---|---|
| Kotlin, Android resources, manifest and debug APK assembly | Passed |
| Unit tests | **41 executed, 41 passed, 0 failures/errors/skips** |
| Android lint | Passed, **0 errors, 9 warnings** |
| Android integration test APK compilation | Passed; **5 tests compiled, execution not yet established locally** |
| APK signature (`apksigner verify --verbose`) | Passed, APK Signature Scheme v2, one signer |
| ZIP alignment (`zipalign -c -P 16 4`) | Passed |
| Manifest/package inspection (`aapt dump badging`) | `com.halo.island`, version 1.0.0, min SDK 26, target/compile SDK 36 |
| Permissions inspection | No INTERNET, microphone, accessibility, media projection, exact alarm, storage or privileged media permission |
| Packaged native libraries | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Repository whitespace check (`git diff --check`) | Passed |

Warnings are five KTX-style suggestions, two configuration sizing recommendations (the service supplies a safe width from actual WindowManager bounds), a newer-coroutines-version notice, and a base adaptive-icon monochrome recommendation (API 33's icon resource includes monochrome). None is suppressed by a global lint baseline or error disabling.

Unit coverage includes event priorities and interruption recovery, explicit seeking state, hiding paused expanded media, manual/playing session selection, progress estimation and bounds, duration formatting, invalid battery information, monotonic timers, paused/reboot timer restoration, stopwatch boot boundaries, settings round trips and corrupted/old values, all six presets, permission mapping, safe insets/position clamping, quality/API fallback, low-memory Auto, and battery-saver tiers.

## APK

Local handover file: `/workspace/artifacts/HALO-Android-debug.apk`.

Build output: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256 of the local handover APK:

```text
f90ba788e7ade5ac3aabcfeac09d95efea5642317e8b15da8757073e627a7be1
```

A `.sha256` file is beside the handover APK. The local debug signing key is not committed. CI creates its own debug-signed APK, so its checksum/signing identity will differ. Uninstall a differently signed build before switching between local and CI debug APKs; preserve any preferences you need first.

## Runtime validation limits

This cloud host has **no `/dev/kvm`**. Android 15 Google APIs and AOSP x86_64 emulators were installed using the Android SDK tools and attempted in software mode. Both exposed ADB during incomplete bootstrap. Attempts to install HALO encountered Android's own `StorageManagerService` / missing `PackageManagerInternal` initialization failure. System logs also showed SystemUI startup ANRs and system-server restarts under software emulation. These occurred **before HALO was installed or launched**. Special-access grant attempts before installation could not succeed.

A writable emulator state location and a direct Python ADB client resolved separate read-only-home/ADB-tool startup issues, but that does not establish Android guest readiness. No application runtime pass, screenshot review, cross-app touch pass-through, player compatibility, GPU performance, battery measurement, or Vivo persistence result is claimed from these attempts. A PID, ADB connection, or early package service was not accepted as proof of readiness.

Five instrumentation tests are implemented and compiled: app launch/DataStore persistence, real MediaSession discovery plus play/pause/previous/next/seek and artwork updates/disconnect, overlay service survival outside the main activity, injected touch pass-through to a separate test APK, and real local countdown pause/resume/completion. The touch probe exists only in the test APK. GitHub Actions includes an Android 15 **KVM-backed** integration job with explicit test-only permission grants and Gradle's connected-test result reporting. A configured job is not a passing CI run; consult its actual run and reports.

Acceptance A–S therefore have source implementations and relevant pure-logic coverage, but **device acceptance remains unverified**. Acceptance T (a valid assembled Android APK) is verified as above. Use [the device checklist](DEVICE_TESTING.md) on a functioning Android 15 emulator and the actual Vivo phone.

## Platform fallbacks and known constraints

- Overlay glass cannot sample arbitrary third-party pixels; only the activity's own backdrop is refracted. No capture workaround exists in this app.
- Foreground services start from explicit interaction in a visible activity. A boot reminder is available; silent overlay restart and OEM persistence are not promised.
- Timers have an inexact alarm fallback. Doze, process death and OEM restrictions can delay completion delivery. A running stopwatch's unsaved interval is not reconstructed after reboot.
- Notification previews are optional and filtered; redaction and source PendingIntent policies may limit content/opening. Media controls require source-advertised actions.
- No per-app foreground detection or lock-screen overlay is implemented. HALO always hides while locked and respects Android's protected surfaces.
- Named third-party music players, actual Vivo/Funtouch behavior, font/cutout variations and visual motion quality still require device testing. Performance goals are not measured results.
