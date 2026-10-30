# Bucket List for Android

The app runs on the phone. Your list and your PIN are stored only on this device, in the app's private storage. No account, no server.

## Build the APK (about 5 minutes, once)

1. Install **Android Studio** (free, from developer.android.com/studio).
2. Open Android Studio, choose **Open**, and select this `android` folder.
3. Wait for the first Gradle sync to finish. If Android Studio offers to create a Gradle wrapper, accept.
4. Menu: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. When it finishes, click **locate**. The file is `app/build/outputs/apk/debug/app-debug.apk`.

## Install on your phone

1. Copy `app-debug.apk` to the phone (USB, Google Drive, or email to yourself).
2. Open it on the phone. Allow "Install unknown apps" for the app you opened it from.
3. Open **Bucket List**. The first time, create a PIN (4 to 12 digits).

## Things to know

- **Data stays on the phone.** Uninstalling the app or clearing its storage erases the list. Use the menu → **Export backup** to keep a copy in Downloads, and **Import backup** to restore it.
- **PIN:** change it under the menu → **Change PIN**. If you forget it, the lock screen has **Forgot your PIN? Erase this device**, which erases the list.
- **Lockout:** five wrong PINs block entry for 15 minutes.
- **Back button:** closes dialogs first, then returns to the Dreams tab, then leaves the app.
- **Minimum Android:** 10 (API 29). The app needs internet only to load its display font; everything else works offline.

## Releasing a signed APK (for installing or sharing outside Android Studio)

Build → Generate Signed Bundle / APK → APK → create a keystore once and keep it safe. A lost keystore cannot be replaced, so back it up.

## Updating the app later

Edit the web files in the `bucketlist` project, then run `python3 android/sync-web.py /path/to/bucketlist` and rebuild the APK.

## Project layout

- `app/src/main/java/com/bucketlist/app/MainActivity.kt`: the WebView host, file picker, back button
- `app/src/main/java/com/bucketlist/app/NativeBridge.kt`: saves exports to Downloads
- `app/src/main/assets/`: the web app (synced from the `bucketlist` project)
- `app/src/main/AndroidManifest.xml`: app name, icon, permissions
