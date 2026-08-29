import assert from "node:assert/strict";
import fs from "node:fs";

const manifest = JSON.parse(
  fs.readFileSync(new URL("../version.json", import.meta.url), "utf8"),
);
const buildFile = fs.readFileSync(
  new URL("../app/build.gradle.kts", import.meta.url),
  "utf8",
);

assert.ok(Number.isInteger(manifest.versionCode) && manifest.versionCode > 0);
assert.equal(manifest.version, manifest.versionName);
assert.match(manifest.versionName, /^\d+\.\d+\.\d+$/);
assert.match(manifest.sha256, /^[A-F0-9]{64}$/);
assert.equal(
  manifest.apkUrl,
  `https://github.com/InfTv/SUP-Place/releases/download/v${manifest.versionName}/SUP_Place_${manifest.versionName}.apk`,
);
assert.match(buildFile, new RegExp(`versionCode\\s*=\\s*${manifest.versionCode}`));
assert.match(
  buildFile,
  new RegExp(`versionName\\s*=\\s*"${manifest.versionName.replaceAll(".", "\\.")}"`),
);

console.log("Version manifest: release metadata is consistent");
