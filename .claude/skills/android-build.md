---
name: android-build
description: Build the DeepSeek Codex Android app debug APK and report errors.
---

# Android Build

Build the debug APK for the DeepSeek Codex Android app (Kotlin + Jetpack Compose).

## Steps

1. Set JAVA_HOME to the Android Studio JDK:
   ```bash
   export JAVA_HOME="C:/java"
   ```

2. Run the Gradle build:
   ```bash
   cd "f:/移动os/app" && ./gradlew assembleDebug 2>&1
   ```

3. Parse the output. If `BUILD SUCCESSFUL` appears, report success and show the APK path (`app/build/outputs/apk/debug/app-debug.apk`).
   If errors appear, extract the error messages — especially Kotlin compilation errors pointing to specific files and line numbers — and report them clearly so the user can fix them.

## Key project context

- **Language**: Kotlin with Jetpack Compose
- **Min SDK**: 24, **Target SDK**: 34, **Compile SDK**: 34
- **Compose BOM**: 2024.12.01 (Compose UI 1.7.6)
- **Important**: Do NOT upgrade the Compose BOM — it breaks compatibility with compileSdk 34
- **Markdown renderer**: 0.28.0 (compatible with Compose UI 1.7.x)
