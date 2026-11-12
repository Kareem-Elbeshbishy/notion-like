# Workspace (offline notes and databases for Android)

Native Kotlin, Jetpack Compose, Room. No INTERNET permission: the app cannot reach the network.

## Build the APK (GitHub Actions)
1. Create a GitHub repository and push this folder to `main`.
2. Open the Actions tab, run "Build APK" (it also runs on every push).
3. Download the `workspace-debug-apk` artifact, unzip, install the APK.

## Build in Android Studio
Open this folder. If it asks for a Gradle version, run `gradle wrapper --gradle-version 8.9` once in this folder, or pick Gradle 8.9 in the dialog.

## Status
Build order steps 1 and 2 are in progress (editor, trash, search, page tree, sub-pages, undo/redo). See `replica/build-log.md`.
