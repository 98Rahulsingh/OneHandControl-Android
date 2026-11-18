# One Hand Control — Android clone starter

An offline-first native Android starter inspired by the public One Hand Control guide and the user's Side Panel mockup.

## Current build
- Android floating edge handle using the overlay permission
- One panel surface with Apps / Quick / Groups / Media tabs
- Quick links to Android Wi-Fi, Bluetooth, Sound and Settings screens
- Foreground service keeps the panel available while running
- No INTERNET permission is declared

## Build APK on GitHub
Open **Actions → Build Android APK → Run workflow**. When the workflow completes, open its run and download the `OneHandControl-debug-apk` artifact.

## Install
Download and install `app-debug.apk` on Android. Allow “Display over other apps”, then start the edge panel.

## Important limitations of this starter
This is an initial native implementation, not a feature-complete reproduction. Android restricts direct control of protected Quick Settings toggles, app launching, global gestures, media sessions, cursor simulation and accessibility actions. Those need explicit user-granted permissions / accessibility-service implementation and device testing. “Add app”, custom gesture mapping, pie menu, bottom 3-zone gestures, remote cursor and full media-session controls are planned follow-up milestones. The app does not silently grant permissions or bypass Android security.

## Compatibility
minSdk 26, target/compile SDK 35, Java 17. GitHub Actions builds a debug APK.
