import assert from "node:assert/strict";
import fs from "node:fs";

const manifest = JSON.parse(
  fs.readFileSync(new URL("../version.json", import.meta.url), "utf8"),
);
const buildFile = fs.readFileSync(
  new URL("../app/build.gradle.kts", import.meta.url),
  "utf8",
);
const appHtml = fs.readFileSync(
  new URL("../app/src/main/assets/index.html", import.meta.url),
  "utf8",
);

const buildCodeMatch = buildFile.match(/versionCode\s*=\s*(\d+)/);
const buildNameMatch = buildFile.match(/versionName\s*=\s*"([^"]+)"/);
const appVersionMatch = appHtml.match(/const APP_VERSION='([^']+)'/);
assert.ok(buildCodeMatch, "source versionCode is missing");
assert.ok(buildNameMatch, "source versionName is missing");
assert.ok(appVersionMatch, "APP_VERSION is missing");

const sourceCode = Number(buildCodeMatch[1]);
const sourceName = buildNameMatch[1];
assert.equal(appVersionMatch[1], sourceName, "web UI and Android source versions must match");

assert.ok(Number.isInteger(manifest.versionCode) && manifest.versionCode > 0);
assert.equal(manifest.version, manifest.versionName);
assert.match(manifest.versionName, /^\d+\.\d+\.\d+$/);
assert.match(manifest.sha256, /^[A-F0-9]{64}$/);
assert.equal(
  manifest.apkUrl,
  `https://github.com/InfTv/SUP-Place/releases/download/v${manifest.versionName}/SUP_Place_${manifest.versionName}.apk`,
);

// version.json describes the latest APK which is already published. During release
// preparation source may legitimately be newer, but the manifest must never advertise
// a version newer than the source tree (or an APK which does not exist yet).
assert.ok(
  manifest.versionCode <= sourceCode,
  `published versionCode ${manifest.versionCode} cannot exceed source versionCode ${sourceCode}`,
);
if (manifest.versionCode === sourceCode) {
  assert.equal(manifest.versionName, sourceName);
}

console.log("Version manifest: published metadata is valid and not ahead of source");
