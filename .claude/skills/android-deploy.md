---
name: android-deploy
description: Build, install on device, and verify the app runs without crashes.
---

# Android Deploy

Build the debug APK, install it on the connected Android device, and check for crashes.

## Steps

1. Build the APK:
   ```bash
   export JAVA_HOME="C:/java"
   cd "f:/移动os/app" && ./gradlew assembleDebug 2>&1
   ```
   Stop if the build fails and report errors.

2. Install on device:
   ```bash
   "F:/Android/Sdk/platform-tools/adb" install -r "f:/移动os/app/app/build/outputs/apk/debug/app-debug.apk" 2>&1
   ```
   If `INSTALL_FAILED` appears, try uninstalling first:
   ```bash
   "F:/Android/Sdk/platform-tools/adb" uninstall com.example.app && "F:/Android/Sdk/platform-tools/adb" install "f:/移动os/app/app/build/outputs/apk/debug/app-debug.apk"
   ```

3. Launch the app and wait 5 seconds for any crash:
   ```bash
   "F:/Android/Sdk/platform-tools/adb" shell am start -n com.example.app/.MainActivity && sleep 5 && "F:/Android/Sdk/platform-tools/adb" logcat -d -s AndroidRuntime:E | tail -20
   ```

4. If `FATAL EXCEPTION` appears in the logcat, report the full stack trace.
   If logcat is clean, report: "App launched successfully, no crashes."

## Key project context

- **Package name**: `com.example.app`
- **Main activity**: `com.example.app.MainActivity`
- **Workspace**: Files are stored in `filesDir/workspace/{conversation_id}/`
- **Log tag filter**: Use `logcat -s AndroidRuntime:E` for crash detection
