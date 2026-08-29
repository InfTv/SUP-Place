import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

const html = fs.readFileSync(
  new URL("../app/src/main/assets/index.html", import.meta.url),
  "utf8",
);
const script = html.match(/<script>([\s\S]*)<\/script>/)?.[1];
assert.ok(script, "index.html must contain the application script");

const migrationSource = script.slice(0, script.indexOf("function save(){"));
const context = vm.createContext({
  localStorage: {
    getItem: () => null,
    setItem: () => {},
  },
});
vm.runInContext(`${migrationSource}\nthis.migrateStateForTest = migrateState;`, context);
const migrate = (value) =>
  context.migrateStateForTest(JSON.parse(JSON.stringify(value)));

const legacyV1 = migrate({
  inventory: { sup1: 3 },
  tariffs: { sup1: { p30: 75 } },
  rentals: [{ id: "r1", items: { sup1: 1 } }],
  bookings: [{ id: "b1", items: { sup1: 1 } }],
  history: [{ id: "h1", kind: "finish", sum: 75 }],
});
assert.equal(legacyV1.schemaVersion, 5);
assert.equal(legacyV1.tariffs.sup1.p60, null, "v1 demo tariffs are intentionally reset");
assert.equal(legacyV1.inventory.kayak2, 0);
assert.equal(legacyV1.rentals[0].prepaid, 0);
assert.equal(legacyV1.rentals[0].note, "");
assert.equal(legacyV1.bookings[0].prepaid, 0);
assert.equal(legacyV1.history[0].prepaid, 0);

const legacyV2 = migrate({
  schemaVersion: 2,
  inventory: {},
  tariffs: { sup1: { p30: 100 } },
  rentals: [],
  bookings: [],
  history: [],
  seq: 4,
});
assert.equal(legacyV2.schemaVersion, 5);
assert.equal(legacyV2.tariffs.sup1.p60, 200);

const legacyV3 = migrate({
  schemaVersion: 3,
  inventory: {},
  tariffs: {},
  rentals: [],
  bookings: [],
  history: [
    { kind: "start", legacy: true },
    { kind: "booking_start", legacy: true },
    { kind: "finish", sum: 120 },
  ],
});
assert.deepEqual(
  Array.from(legacyV3.history, (entry) => entry.kind),
  ["finish"],
);

const futureFields = migrate({
  schemaVersion: 99,
  inventory: {},
  tariffs: {},
  rentals: [{ prepaid: 10, note: "client", futurePaymentMethod: "cash" }],
  bookings: [],
  history: [],
  futureTopLevelField: { preserved: true },
});
assert.equal(futureFields.schemaVersion, 99);
assert.equal(futureFields.rentals[0].futurePaymentMethod, "cash");
assert.equal(futureFields.futureTopLevelField.preserved, true);

console.log("Report migrations: all compatibility checks passed");

