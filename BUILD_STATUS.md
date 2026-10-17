# Build and verification status

Source complete for an experimental Android 15 screen capture + touch-through overlay.

Verified locally:
- Pure-Java trajectory, three-line and pocket tests.
- Syntactic XML parsing and valid project/ZIP structure.

NOT verified:
- Android Gradle compilation (no Android SDK or Gradle available in this execution environment).
- Installation on Galaxy S26+.
- Accuracy over live 8 Ball Pool frames; automatic cue-direction inference is experimental.
- Touch pass-through and protection against self-capture on the phone.

To produce an installable APK, run .github/workflows/android-apk.yml in a GitHub repository with the contents of this root directory. It creates an app-debug.apk artifact.

Implementation uses Android 15 MediaProjection user approval with a foreground mediaProjection service. Only one TYPE_APPLICATION_OVERLAY window is created and its flags explicitly include FLAG_NOT_TOUCHABLE and FLAG_NOT_FOCUSABLE. Overlay opacity defaults to .45 (< Android's .80 obscuring-opacity threshold).
