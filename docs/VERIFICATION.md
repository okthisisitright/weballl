# HALO verification — 2026-10-22

## Local build evidence

Executed against the native Android sources in this branch with JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, SDK platform 36 and build tools 35.0.0:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

| Check | Actual result |
|---|---|
| Kotlin, Android resources, manifest and debug APK assembly | Passed |
| Unit tests | **43 executed, 43 passed, 0 failures/errors/skips** |
| Android lint | Passed, **0 errors, 9 warnings** |
| Android integration test APK compilation | Passed; **5 tests compiled; initial run 4 passed / 1 fixture-visibility failure; later run killed during startup** |
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
ae3cb554a6c715fa8e295f9710658c705608f3fbbe9f9c06a7f75e511c9bf31a
```

A `.sha256` file is beside the handover APK. The local debug signing key is not committed. CI creates its own debug-signed APK, so its checksum/signing identity will differ. Uninstall a differently signed build before switching between local and CI debug APKs; preserve any preferences you need first.

## Runtime validation and remaining device tests

This cloud host has **no `/dev/kvm`**. Android 15 Google APIs and AOSP x86_64 emulators were attempted in software mode. Early guest bootstrap had `StorageManagerService` / missing `PackageManagerInternal` failures, SystemUI ANRs and system-server restarts. The AOSP guest eventually completed boot, and both HALO's app and test APKs installed successfully.

The first complete instrumentation run executed five tests:

| Device check | Observed result |
|---|---|
| Overlay service remains running after leaving HALO and stops cleanly | Passed |
| Local timer counts down, pauses, resumes and completes | Passed |
| Real MediaSession discovery, play/pause/previous/next/seek callbacks, artwork/metadata changes and disconnect | Passed using a local test session |
| Launch and theme persistence through a fresh DataStore repository read | Passed |
| Injected outside touch reaches a separate test application | Failed while finding the probe activity, before touch injection |

The touch harness was corrected to launch from a visible activity, inspect interactive windows, set touchscreen input source, and explicitly assert a bounded HALO window. A later full-suite attempt was killed by Android during cold startup **before tests executed**. Logs show very slow bytecode verification and startup ANRs; no HALO Java exception was recorded for that attempt. Android's standard `cmd package compile -m verify -f` completed successfully for both packages. Remaining device execution was then deferred to the user at their request. **No five-test passing run is claimed.**

HALO drew its home UI, but cold launches in this software emulator also exceeded startup timeouts and displayed an ANR dialog. Shader compilation and preview bitmap generation were moved off the main thread; shaders are not constructed for the overlay or Lite fallback. These changes compile and pass the unit/lint checks. Cold-start responsiveness, motion quality and battery performance still require a real device or hardware-accelerated emulator. The captured early ANR screenshot is diagnostic evidence, not a finished product screenshot.

The five instrumentation tests are included in the repository and a KVM-backed Android 15 GitHub Actions job. Its actual result could not be read because this cloud environment blocks `api.github.com`; workflow configuration and successful Git pushes do not establish CI success. See [the device checklist](DEVICE_TESTING.md). Permission grants in the automated setup are test-only shell grants; real users must use Android's consent/settings flows.

The latest APK was assembled, signature/alignment checked, and installed in the emulator. Named third-party players, Vivo/Funtouch persistence, permission revocation/recovery, charging transitions, touch pass-through after the harness correction, cutout/font variants and visual motion quality remain for device testing. A standalone shader-check utility was prepared but not executed; it is not counted as validation. No measured GPU, frame-rate, battery or broad player-compatibility claim is made.

## Platform fallbacks and known constraints

- Overlay glass cannot sample arbitrary third-party pixels; only the activity's own backdrop is refracted. No capture workaround exists in this app.
- Foreground services start from explicit interaction in a visible activity. A boot reminder is available; silent overlay restart and OEM persistence are not promised.
- Timers have an inexact alarm fallback. Doze, process death and OEM restrictions can delay completion delivery. A running stopwatch's unsaved interval is not reconstructed after reboot.
- Notification previews are optional and filtered; redaction and source PendingIntent policies may limit content/opening. Media controls require source-advertised actions.
- No per-app foreground detection or lock-screen overlay is implemented. HALO always hides while locked and respects Android's protected surfaces.
- Named third-party music players, actual Vivo/Funtouch behavior, font/cutout variations and visual motion quality still require device testing. Performance goals are not measured results.
