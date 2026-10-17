# SUP Place

**SUP Place** is an Android application for managing SUP and kayak rentals in day-to-day operation.

The project combines reservations, active rentals, pricing rules, reports, recovery mechanisms, cloud-assisted report workflows, application updates and automated release checks in one mobile app.

## Highlights

- reservation and active-rental management;
- configurable pricing and automatic cost calculation;
- five-minute rounding rules for rental timing;
- rental history and correction workflows;
- report export, import, preview and full recovery;
- backward-compatible report migrations;
- manual and background application update checks;
- APK SHA-256 verification before installation;
- regression tests for rental logic, report migration/recovery, updater behaviour and cloud sync;
- automated CI/release workflow with GitHub Actions.

## Tech stack

- **Android / Java**
- **Android SDK 35**, minSdk 23
- **HTML, CSS and JavaScript** for the WebView UI
- **Gradle / Kotlin DSL**
- **Node.js** regression tests
- **Git / GitHub**
- **GitHub Actions** for CI/CD

## Architecture

The Android shell is implemented in Java and hosts the application UI in a local WebView.

The app is designed around reliable local operation. Newer versions also support cloud-assisted report workflows. The update system compares the installed `versionCode` with the published `version.json`, downloads the matching APK, verifies its SHA-256 checksum and opens the Android package installer.

## Testing

Regression tests cover:

- rental pricing and timing rules;
- report migration and backward compatibility;
- report recovery;
- update UI and timeout behaviour;
- native updater source checks;
- background update checks;
- cloud synchronization;
- version-manifest consistency.

The complete regression suite is executed automatically by GitHub Actions before a release is built.

## Build

Requirements:

- JDK 17
- Android SDK 35

```bash
./gradlew :app:assembleDebug
```

On Windows:

```text
gradlew.bat :app:assembleDebug
```

## Releases

Signed APK versions are published through GitHub Releases.

The Android package name and signing identity are kept stable so existing installations can be updated without deleting local application data.

Release signing material is stored only in protected GitHub Actions secrets and is never stored in the current source tree.

## Project status

Current version: **0.4.22**

SUP Place is a practical software-engineering project built around real rental workflows rather than a tutorial/demo application.

## Author

**Anton Kuzmin**

GitHub: [@InfTv](https://github.com/InfTv)
