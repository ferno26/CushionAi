# Cushion AI · Android 15 screen-guidance prototype

**Build status:** Android source only. The Android application has not been compiled or tested on a phone. The prediction algorithms and the separate screenshot tester have local tests, but the live screen capture, overlays and cue tracking are **unverified on a device**.

A standalone 8 Ball Pool-style shot guideline system for your no-root Galaxy S26+. It does **not** modify the Miniclip APK, automate touches, bypass security, or read hidden game memory.

## Implemented source features

- Android `MediaProjection` full-display permission request, required for each new session on Android 15.
- Native foreground screen capture service with an ongoing notification and Stop action.
- A **single** full-screen `TYPE_APPLICATION_OVERLAY` using `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE` and **0.45 window alpha**. No touchable floating WebView windows above other apps.
- Local heuristic detection of table boundaries, cue ball, other balls and cue-stick direction. Processing roughly 3–4 frames per second when hardware allows it.
- Center guideline in yellow, optional two offset side guides (three lines total), up to 12 ideal cushion reflections, approximate first-collision object-ball path, and clear-vs-obstructed pocket guide rays.
- Controls for opacity, table alignment, maximum bounces, guide corridor width, and manual angle fallback.
- A separate `Screenshot-Lab.html` for testing a saved screenshot on your phone. This is **not** a live overlay.
- No internet, analytics, or filesystem storage permissions.

## Critical accuracy limits

This does not use a trained YOLO or neural model. It uses an image-colour heuristic that can fail on some skins, animations, overlays, and graphic effects. The shot-angle estimation may be wrong even when cue-ball detection succeeds. 'ANGLE FALLBACK' indicates uncertain stick recognition; set the manual angle in the app. Shot physics assume ideal reflection and ignore friction, spin, hidden game physics and many collisions.

## Build APK

Requires JDK 17, Android SDK Platform 35, Android Gradle Plugin 8.7.3 and Gradle 8.9.

```
gradle --no-daemon :app:assembleDebug
```

The signed debug APK is then at `app/build/outputs/apk/debug/app-debug.apk`.

To build without a PC, upload the **contents of this Android project directory** to a GitHub repository, including `.github/workflows/android-apk.yml`. GitHub Actions will build an Android APK when main is updated. GitHub must allow repository writes. The last attempt from ChatGPT's GitHub integration got HTTP 403; this ZIP alone does not magically start a build.

## Use on phone after building

1. Stop/uninstall your older Cushion Lab overlays to avoid two overlapping windows blocking taps.
2. Install the newly compiled APK on the Galaxy S26+.
3. Grant **Appear on top**. Tap **Start Screen Guidance** and grant Android permission to share the **entire display**.
4. Open 8 Ball Pool in landscape. Yellow = center ray, white = side guides, cyan = rough object-ball ray, green dotted = visually unobstructed pocket line.
5. If cue-stick angle detection is uncertain, return to Cushion AI's settings, disable automatic angle selection and adjust the degree slider.
6. Stop screen capture using the app's Stop button or ongoing notification.

**Touch-through:** This native app creates a non-touchable drawing window rather than a WebView that handles drag events. Android 12+ may still block pass-through if there are multiple translucent overlays whose combined alpha exceeds 0.8, or if an OEM imposes stricter rules. This cannot be guaranteed without a phone installation test.

## Validation run

```
sh tests/run.sh
```

Tests cover 120 independent headings with 3 projected lines, six-segment reflection, 52 other headings with seven rebounds, synthetic felt, object collisions, and pocket-clearance checks. The screenshot browser app was exercised against an available game screenshot: detected cue (1052,657) vs visually observed cue (~1050,655). That does not establish reliable performance in gameplay.

## References

Based on previously uploaded Cushion Lab sources and the MIT-licensed 8BallPool-main example by Jonathan Silva (see `REFERENCE-LICENSE.txt`). The program is an independent tool, not affiliated with Miniclip or Cheto.


## 2026-10-17 recognition fixes (uncompiled source revision)

- Added radial-disk occupancy check to reject line-shaped aiming/cue artifacts.
- Preserved striped object-ball recognition with a white-disk exception.
- Corrected cue-ball identity scoring: don't treat a striped object's bright annulus as the white cue ball.
- Removed automatic manual-angle fallback: the app now hides trajectory predictions when detected cue direction confidence is below 0.55. Manual angle still works if explicitly selected.
- New exact-screenshot regression prevents detections at the fake red aiming icon and along the cue shaft, and checks the blue-striped object ball.
- GitHub CI runs host-side tests before attempting Android compilation.

**Validation status:** Tests pass on the one provided screenshot, and trajectory math passes synthetic tests. Real-time aiming accuracy, Android installation, and performance have not been validated on a device. The current environment lacks Android SDK and GitHub write scope, so this source revision is not yet compiled into an APK.
