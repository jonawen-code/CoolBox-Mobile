---
description: Build and Install CoolBox Mobile APK
---
// turbo-all

1. Run Gradle build and install:
```powershell
.\gradlew.bat clean assembleDebug
& "C:\Users\xiaoheishu\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\CoolBox-v1.0.12.apk"
```
Working directory: `o:\APP.Project\SynologyDrive\CoolBox-Mobile`

2. Verify successful installation on the device.
