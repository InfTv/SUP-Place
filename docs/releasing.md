# Publishing a release

SUP Place keeps a stable Android package name and signing identity so updates can
be installed over existing versions without deleting local application data.

## Release checklist

1. Keep the package name `com.supplace.app` unchanged.
2. Increase both `versionCode` and `versionName` in `app/build.gradle.kts`.
3. Keep the web UI application version in sync.
4. Update `release-notes.txt`.
5. Run all regression tests.
6. Build the release APK.
7. Sign it with the permanent release identity.
8. Verify the signing certificate and APK metadata.
9. Publish the APK to GitHub Releases.
10. Only after the APK is downloadable, update `version.json` with the release URL,
    changelog and exact SHA-256 of the published APK.

## Signing material

Signing material must never be committed to source control.

GitHub Actions uses these repository secrets:

- `SUP_PLACE_RELEASE_KEY_B64`
- `SUP_PLACE_RELEASE_PASSWORD_B64`

The workflow reconstructs the keystore only inside the temporary GitHub Actions runner.

For local release builds, `app/build.gradle.kts` also supports:

- `SUP_PLACE_KEYSTORE`
- `SUP_PLACE_STORE_PASSWORD`
- `SUP_PLACE_KEY_ALIAS`
- `SUP_PLACE_KEY_PASSWORD`

## Compatibility

Every new release must continue to read older report formats. New fields should be
optional and unknown fields should be preserved where supported.
