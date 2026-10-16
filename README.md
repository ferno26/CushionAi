# Cushion Lab Auto Guide (Android, no root)

**Status: compilable Android source prototype — not device-verified or a guaranteed accurate aim tool.**

A separate touch-through overlay intended for 8 Ball Pool landscape mode. This project does **not** patch or hook the Miniclip APK and does not require root, Accessibility Service, Internet permission, or a login.

## Features implemented

- Android 15-compatible screen sharing request using `MediaProjectionConfig.createConfigForDefaultDisplay()` (API 34+)
- Foreground `mediaProjection` service with notification and Stop action
- One full-screen `TYPE_APPLICATION_OVERLAY` window with `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE` and **window alpha 0.45 by default**. No touchable bubbles over the game.
- Processes RGBA screen frames locally about 3–4 times per second, without storing/uploading screenshots.
- Heuristic color-based table/felt, ball, cue-ball and cue-stick estimation.
- Multi-cushion reflections (up to 12), cue trajectory to first ball/cushion, rough object-ball path, and pocket guide rays.
- Control screen for opacity (10–70%), table width/height and X/Y alignment offsets, bounce count, automatic aiming and manual-angle fallback.
- Rotation/capture resize via `VirtualDisplay.resize()` and `setSurface()` (reuses the one projection token).

## Known limitations (important)

- Automatic detection uses pixel heuristics, not a trained detector: skins, visual effects, reflections, motion, UI graphics, and other overlays can produce false detections. Line positions and pocket markers may not be exact.
- A screenshot does not contain the full physical state of 8 Ball Pool. Spin, ball friction, collisions, opponent effects, and hidden game logic are not modeled exactly.
- Aim direction inference requires a visibly distinguishable cue stick. The `ANGLE FALLBACK` indicator means direction detection is weak; use the manual angle slider.
- Android may restrict capture of protected content. Some games may hide screen content during capture or restrict third-party overlays.
- Android 15 requires user permission for each new MediaProjection session; app cannot automatically restart a screen capture after Android revokes it.
- With touch-through ON, drag handles in the game cannot receive touches by design. Edit geometry using the main activity.
- **No SDK or emulator was available in the authoring environment. Android build/installation and automatic detection on an S26+ are not verified.**

## Build from source

Requirements: Android SDK Platform 35; Android Gradle Plugin 8.7.3; Gradle 8.9; JDK 17.

```
gradle :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions (phone-only remote build)

Upload this extracted project **with its directories preserved** to a GitHub repository. The included `.github/workflows/android-apk.yml` automatically assembles a debug APK whenever you push to `main` or `master`, or when run manually from Actions. Open the latest workflow run, then download the `CushionLab-AutoGuide-APK` artifact. Inside is `app-debug.apk`, installable with Samsung My Files. This needs no root or computer, but you need a GitHub account and a way to upload the project folder intact.

### Direct AndroidIDE build

Alternatively, import the extracted project using a compatible AndroidIDE/Android Studio installation with Android SDK 35 and JDK 17, then run `:app:assembleDebug`. On the phone, this is not possible with **only** a file manager, because an APK must be compiled and signed.

## Using on Galaxy S26+ (Android 15)

1. Install the APK built from this project. Open **Cushion Lab Auto Guide**.
2. Enable **Appear on top** for this app.
3. Press **START SCREEN GUIDANCE** and approve the Android full-display screen-capture request.
4. Switch to 8 Ball Pool landscape mode. A bright outline appears only when the algorithm detects a likely pool table.
5. Yellow trajectory: estimated cue-ball route and cushion rebounds. Cyan: rough target-ball route. Six pocket markers represent the table ends.
6. If it fails to identify the cue stick, change the fallback angle from the app settings; control screen stays outside the overlay so normal game interaction is preserved.
7. Return to the app/notification and press **Stop** to end capture.

For troubleshooting, check `adb logcat -s CushionAutoGuide` if Android debugging is available. If the game cannot be tapped, disable other overlays and ensure the only displayed window from this app uses `FLAG_NOT_TOUCHABLE` and `LayoutParams.alpha <= 0.70f`. OEM security layers may still block touches.

## Verification

Pure Java screenshot/trajectory module tested locally via:

```
sh tests/run.sh
```

Covers 52 launch directions × 7 cushion reflections, object-ball collision, and synthetic felt/table recognition. This **does not** simulate Android permissions, window routing, a full Gradle build, or real 8 Ball Pool screenshots on Galaxy S26+.

## Reference sources

The project was designed with two uploaded sources as references: `8BallPool-main.zip` (Jonathan Silva, MIT, geometry/overlay concepts) and earlier `CushionLabOverlay` sources (table, bounce/opacity design). This is a new independent Java Android project, not a replacement 8 Ball Pool game APK. Thanks to Jonathan Silva's MIT-licensed project for the reference geometry; original license is included in `REFERENCE-LICENSE.txt`.
