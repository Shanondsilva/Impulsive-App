# Impulsive Android Dev Setup

## Java

Set `JAVA_HOME` before running Gradle from PowerShell:

```powershell
$env:JAVA_HOME='D:\tmp\jdk17\jdk-17.0.19+10'
```

## Build

From the project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Android Studio

Open `D:\Impulsive\Impulsive-App` as the project root in Android Studio, then let Gradle sync.

## Run On Emulator Or Device

Start an emulator from Android Studio Device Manager or connect a physical Android device with USB debugging enabled. Then run the app from Android Studio, or install the debug APK from PowerShell:

```powershell
C:\Users\shano\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
C:\Users\shano\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am start -n com.impulsive.app/.MainActivity
```
