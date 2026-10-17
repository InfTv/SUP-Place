import assert from "node:assert/strict";
import fs from "node:fs";

const html = fs.readFileSync(new URL("../app/src/main/assets/index.html", import.meta.url), "utf8");
const activity = fs.readFileSync(new URL("../app/src/main/java/com/supplace/app/SupPlaceActivity.java", import.meta.url), "utf8");

assert.ok(html.includes('id="brandTitle"'), "SUP Place title must be the hidden owner entry target");
assert.ok(html.includes("setTimeout(()=>{ownerHoldTimer=null;openOwnerEntry()},1700)"), "owner mode must require a long press");
assert.ok(html.includes("CLOUD_DEVICE_KEY='supplace_cloud_device_v1'"), "paired point must persist locally");
assert.ok(html.includes("CLOUD_ADMIN_KEY='supplace_cloud_admin_v1'"), "owner session must persist only on the owner device");
assert.ok(html.includes("CLOUD_QUEUE_KEY='supplace_cloud_queue_v1'"), "offline report queue must persist");
assert.ok(html.includes("supplace_pair_device"), "instructor device pairing RPC must exist");
assert.ok(html.includes("supplace_submit_daily_report"), "daily report upload RPC must exist");
assert.ok(html.includes("supplace_owner_reports"), "owner archive RPC must exist");
assert.ok(html.includes("supplace_owner_create_pair_code"), "owner must be able to create one-time pairing codes");
assert.ok(html.includes("Повторная отправка за эту же дату обновит сохранённый отчёт"), "same-day upload semantics must be explicit");
assert.ok(html.includes("setInterval(retryCloudQueue,CLOUD_RETRY_MS)"), "offline queue must retry automatically");
assert.ok(activity.includes('https://tqhesbyebqbqzepexgyo.supabase.co'), "native bridge must target the configured Supabase project");
assert.ok(activity.includes('sb_publishable_jHiQlleA5yZS23F_CXBZnA_Waf8LDOs'), "native bridge must use the publishable key");
assert.ok(activity.includes('setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)'), "Supabase publishable key must be sent in apikey header");
assert.ok(!activity.includes('Authorization", "Bearer " + SUPABASE_PUBLISHABLE_KEY'), "opaque publishable key must not be sent as a Bearer JWT");
assert.ok(activity.includes('isAllowedCloudFunction(functionName)'), "native bridge must whitelist RPC functions");

console.log("Cloud sync: pairing, daily archive, hidden owner mode, offline queue and native RPC bridge passed");
