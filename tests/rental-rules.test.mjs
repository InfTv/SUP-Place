import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

const html = fs.readFileSync(
  new URL("../app/src/main/assets/index.html", import.meta.url),
  "utf8",
);
const script = html.match(/<script>([\s\S]*)<\/script>/)?.[1];
assert.ok(script, "index.html must contain the application script");

const coreSource = script.slice(0, script.indexOf("function toast("));
const context = vm.createContext({
  localStorage: {
    getItem: () => null,
    setItem: () => {},
  },
});
vm.runInContext(
  `${coreSource}\nthis.rules = { round5, billedMinutes, billFor, paymentBreakdown, setState: value => { S = value; } };`,
  context,
);

const rules = context.rules;
const minute = 60_000;
assert.equal(rules.round5(22 * minute), 20 * minute);
assert.equal(rules.round5(23 * minute), 25 * minute);
assert.equal(rules.round5(38 * minute), 40 * minute);
assert.equal(rules.round5(41 * minute), 40 * minute);

assert.equal(rules.billedMinutes(9), 0);
assert.equal(rules.billedMinutes(10), 15);
assert.equal(rules.billedMinutes(29), 15);
assert.equal(rules.billedMinutes(30), 30);
assert.equal(rules.billedMinutes(44), 30);
assert.equal(rules.billedMinutes(45), 60);
assert.equal(rules.billedMinutes(74), 60);
assert.equal(rules.billedMinutes(75), 90);

rules.setState({
  tariffs: {
    sup1: { p60: 400 },
    sup2: { p60: 600 },
  },
});
assert.equal(rules.billFor({ sup1: 1 }, 15), 100);
assert.equal(rules.billFor({ sup1: 1 }, 30), 200);
assert.equal(rules.billFor({ sup1: 1 }, 60), 400);
assert.equal(rules.billFor({ sup1: 2, sup2: 1 }, 30), 700);

assert.deepEqual(
  { ...rules.paymentBreakdown(500, 200) },
  { total: 500, prepaid: 200, left: 300, over: 0 },
);
assert.deepEqual(
  { ...rules.paymentBreakdown(500, 500) },
  { total: 500, prepaid: 500, left: 0, over: 0 },
);
assert.deepEqual(
  { ...rules.paymentBreakdown(500, 700) },
  { total: 500, prepaid: 700, left: 0, over: 200 },
);

for (const label of ["Полная стоимость", "Предоплата", "Осталось доплатить"]) {
  assert.ok(html.includes(label), `finish UI must include ${label}`);
}
assert.ok(html.includes("x.note"), "active rentals in imported reports must show notes");
assert.ok(html.includes("b.note"), "bookings in imported reports must show notes");

console.log("Rental rules: rounding, billing, prepayment, and report UI passed");
