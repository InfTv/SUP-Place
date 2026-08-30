# Publishing a release

1. Keep the package name `com.supplace.app` unchanged.
2. Increase both `versionCode` and `versionName` in `app/build.gradle.kts` and
   keep the web UI `APP_VERSION` in sync.
3. Build the release APK with the permanent key via the signing environment
   variables documented in the README.
4. Verify that the APK certificate SHA-256 is the permanent fingerprint from
   the README and that APK Signature Scheme v2 (or newer) is present. Do not
   publish a v1-only APK for a modern Android release build.
5. Run the migration/rental/version regression tests.
6. Create a GitHub Release tagged `v<versionName>` and attach the APK as
   `SUP_Place_<versionName>.apk`.
7. Only after the release asset is live, update `version.json` with the new
   code, name, release asset URL, changelog and exact APK SHA-256, then push it
   to `main`.

During release preparation `app/build.gradle.kts` may therefore be newer than
`version.json`. This is intentional: `version.json` always describes the most
recent APK which is already downloadable by installed clients.
