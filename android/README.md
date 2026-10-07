# AudioForge Android

The Android app is a native Kotlin client with the same AudioForge flow as the desktop app. It uses Android's document pickers for input and output, and FFmpegKit for audio extraction.

Build an installable debug APK from this folder with:

```bash
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow builds and uploads a versioned copy automatically.

The supplied AudioForge logo is used for both the launcher icon and the in-app header. Converted audio is written directly to the device's `Downloads` folder using Android's media storage APIs.
