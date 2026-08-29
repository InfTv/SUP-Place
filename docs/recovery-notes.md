# Recovery notes

The source project was reconstructed from the last preserved MVP sources and
the installed release artifact `SUP_Place_0.4.8.apk`.

Reference release facts:

- package: `com.supplace.app`
- version: `0.4.8` (`versionCode` 17)
- APK SHA-256: `D038148D18636305BFBFDE0FCA47DDE233C9B9D59FB022247CD3ED1BCCD30395`
- signing certificate SHA-256: `FC:9C:49:84:61:21:3F:9E:FD:9B:0C:EE:A9:D2:48:98:F3:57:D2:22:45:18:3F:8E:10:B0:3F:02:94:E9:83:CA`
- WebView storage key: `supplace_state_v1`
- received reports key: `supplace_received_reports_v1`

The private signing key is deliberately not part of this repository. Release
builds read it from the `SUP_PLACE_KEYSTORE`, `SUP_PLACE_STORE_PASSWORD`,
`SUP_PLACE_KEY_ALIAS`, and `SUP_PLACE_KEY_PASSWORD` environment variables.

The recovered app deliberately keeps the same package and the same
`file:///android_asset/index.html` origin so an update installed over 0.4.8
retains its WebView local storage.

