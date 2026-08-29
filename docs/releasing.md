# Publishing a release

1. Keep the package name `com.supplace.app` unchanged.
2. Increase both `versionCode` and `versionName` in `app/build.gradle.kts`.
3. Build the release APK with the permanent key via the signing environment
   variables documented in the README.
4. Verify that the APK certificate SHA-256 is the permanent fingerprint from
   the README.
5. Create a GitHub Release tagged `v<versionName>` and attach the APK as
   `SUP_Place_<versionName>.apk`.
6. Update `version.json` with the new code, name, release asset URL, changelog,
   and APK SHA-256, then push it to `main`.

The release asset must be published before `version.json` advertises it. This
prevents installed apps from receiving a download URL that is not live yet.

