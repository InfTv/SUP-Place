import assert from "node:assert/strict";
import fs from "node:fs";

const manifest = fs.readFileSync(new URL("../app/src/main/AndroidManifest.xml", import.meta.url), "utf8");
const activity = fs.readFileSync(new URL("../app/src/main/java/com/supplace/app/SupPlaceActivity.java", import.meta.url), "utf8");
const service = fs.readFileSync(new URL("../app/src/main/java/com/supplace/app/UpdateCheckJobService.java", import.meta.url), "utf8");

assert.ok(manifest.includes("android.permission.POST_NOTIFICATIONS"), "Android 13+ notification permission must be declared");
assert.ok(manifest.includes("android.permission.RECEIVE_BOOT_COMPLETED"), "persisted JobScheduler work needs RECEIVE_BOOT_COMPLETED");
assert.ok(manifest.includes('android:name=".UpdateCheckJobService"'), "background update JobService must be registered");
assert.ok(manifest.includes('android:permission="android.permission.BIND_JOB_SERVICE"'), "JobService must require BIND_JOB_SERVICE");
assert.ok(activity.includes("setPeriodic(24L * 60L * 60L * 1000L)"), "background update check must run roughly once per day");
assert.ok(activity.includes("getPendingJob(UpdateCheckJobService.JOB_ID)"), "opening the app must not keep resetting the daily job on modern Android");
assert.ok(activity.includes("requestPermissions("), "Android 13+ must ask for notification permission in-app");
assert.ok(service.includes("installedVersionCode()"), "background checker must compare against the actually installed package version");
assert.ok(service.includes("remoteCode <= currentCode"), "the installed version must never notify about itself");
assert.ok(service.includes("LAST_NOTIFIED_CODE"), "the same remote version must notify only once");
assert.ok(service.includes("createNotificationChannel"), "Android 8+ update notification channel must exist");
assert.ok(service.includes("PendingIntent.getActivity"), "tapping the update notification must open SUP Place");

console.log("Background updates: daily schedule, real version comparison, permission and dedupe passed");
