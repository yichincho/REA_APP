# REA APK Backup

Android APK backup utility for personal archiving, written in Java (no root required).

## Features
- Browse and search installed user apps (including updated system apps).
- Select multiple packages and choose a destination using the system Storage Access Framework.
- Export all installed APK paths (base APK and split APKs) into a ZIP-based **.apks** archive per package.
- Include a text manifest with package name, app label, version, APK count and timestamp.
- View backup progress and per-app errors.

## Build an APK
1. Visit **Actions → Build Android APK** in this GitHub repository.
2. Trigger **Run workflow** (or check the build triggered by a push).
3. After a successful build, open the run and download the **REA-APK-Backup-debug** artifact ZIP.
4. Extract the ZIP to obtain **app-debug.apk** and sideload it on your Android device.

Alternatively open this project in Android Studio with JDK 17, Android SDK 35, and Gradle 8.9, and build the debug APK locally.

## Usage
1. Launch **REA APK Backup**.
2. Search/select installed apps.
3. Tap **匯出所選 App (.apks)** and choose a folder.
4. Keep the resulting *.apks archives in a safe backup location.

Each archive contains all APK files currently installed for that package, plus a `backup-info.txt` file. Restore the APK files with a compatible split-APK installer or use `adb install-multiple` after extracting the archive. Do not try to install a split APK archive as a plain APK.

## Important limitations
- It backs up **installation files only**, not private app data, app settings, or paid licenses.
- Google Play licensing, Play Integrity, device-specific splits, app-specific encryption or Android version changes may prevent reinstalling or using backed-up applications.
- The app uses `QUERY_ALL_PACKAGES` to show installed packages. **Google Play restricts use of this permission**; this source project is intended for personal sideloading and may need redesign for Play Store publication.
- APKs are read from installed packages using the normal application sandbox permissions; no root access and no license bypass.
- Backup files can be large. Make sure storage space is available; verify your backups before uninstalling applications.
- A debug build is for personal testing, not a production signed release.

## Project structure
- `app/src/main/java/tw/rea/apkbackup/MainActivity.java` — UI, selection and backup ZIP streaming
- `app/src/main/AndroidManifest.xml` — package visibility, Activity
- `.github/workflows/build.yml` — builds a downloadable debug APK in GitHub Actions

## Privacy
No network permission is declared. All backup operations run locally on the phone and write only to the user-chosen document tree.
