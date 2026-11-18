# One Hand Control — full-featured clone project

Offline-first native Android project inspired by the public One Hand Control guide. This is an independent implementation and does not include the original app's proprietary source/assets.

## Implemented in this build
- Accessibility-service based left/right/top/bottom edge zones
- Tap, double tap, long press, pull inward and directional swipe recognition
- Configurable action mappings for Back, Home, Recents, notifications, Quick Settings, screenshot, power dialog, lock screen, volume, mute, brightness settings, flashlight, and floating panels
- Quick Tools: brightness and media volume sliders, flashlight, Wi-Fi/Bluetooth settings, notification shade and Quick Settings
- Pie Control: configurable radial-style action grid
- App Launcher: searchable list of launchable installed apps
- Side Panel: Actions, clipboard view while app is foreground, and on-device notes
- Media transport buttons, remote cursor touch pad (uses AccessibilityService gesture dispatch), quick timer
- Edge size/length/sensitivity and gesture timing settings
- No INTERNET permission in manifest; no server or analytics dependency

## Setup
1. Install APK.
2. Open app and enable **One Hand Control Edge Gestures** in Android Accessibility settings.
3. Return to app and configure Edges and Actions.
4. Allow Modify System Settings if you want the brightness slider to change system brightness.
5. Use left/right edge trigger zones to open your selected panels.

## Build
GitHub → Actions → Build Android APK → Run workflow. Download artifact `OneHandControl-debug-apk`.

## Android limitations
- Android/OEM versions may restrict lock-screen, screenshot, power, torch and global navigation actions.
- Clipboard reads are restricted by Android to foreground/focused apps; the panel reads current clipboard only while open.
- Wi-Fi/mobile-data master switches are not directly toggled by normal third-party apps; settings screens are opened instead.
- Media buttons depend on the active media session and Android/OEM support.
- Accessibility gesture injection and edge overlays require explicit user enablement. Do not enable the service if you do not trust the app.
- Some advanced features from the reference app (notification carousel, macro recorder, drawn gestures, app-specific pause rules, full multi-ring pie editing, notch cutout calibration, per-zone sector editor) remain future work; no fake controls are presented as fully functional.

## Technical
Kotlin, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile/target SDK 35, min SDK 26, Java 17.
