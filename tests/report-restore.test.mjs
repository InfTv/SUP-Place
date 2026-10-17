import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

const html = fs.readFileSync(new URL("../app/src/main/assets/index.html", import.meta.url), "utf8");
const script = html.match(/<script>([\s\S]*)<\/script>/)?.[1];
assert.ok(script, "index.html must contain application script");
const source = script.slice(0, script.indexOf("function openReceivedReport"));

function makeStorage(initial = {}, failKey = null) {
  const data = new Map(Object.entries(initial));
  return {
    getItem: (key) => data.has(key) ? data.get(key) : null,
    setItem: (key, value) => {
      if (key === failKey) throw new Error("quota");
      data.set(key, String(value));
    },
    dump: () => Object.fromEntries(data),
  };
}

function boot(storage) {
  const messages = [];
  const context = vm.createContext({
    localStorage: storage,
    navigator: { userAgent: "" },
    document: {
      documentElement: { style: { setProperty() {} } },
      querySelectorAll: () => [],
      getElementById: () => ({ addEventListener() {}, classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } }, style: {}, dataset: {} }),
    },
    window: { addEventListener() {} },
    history: { replaceState() {}, pushState() {}, back() {}, state: null },
    console,
    messages,
  });
  vm.runInContext(`${source}\nthis.testApi={
    setState:v=>{S=v}, getState:()=>S,
    setReports:v=>{R=v}, getReports:()=>R,
    restore:commitRestoreReport, deleteReport:commitDeleteReceivedReport,
    patchUi:()=>{closeModal=()=>{};render=()=>{};setPage=()=>{};toast=m=>messages.push(String(m))},
    messages
  };`, context);
  context.testApi.patchUi();
  return context.testApi;
}

const state = {schemaVersion:5,inventory:{sup1:4,sup2:1,kayak1:2,kayak2:0},tariffs:{},rentals:[],bookings:[],history:[],seq:7};
const restored = {schemaVersion:5,inventory:{sup1:8,sup2:2,kayak1:3,kayak2:1},tariffs:{},rentals:[],bookings:[],history:[],seq:11};
const report = {id:"rep1",data:restored,importedAt:1,exportedAt:2};
const otherReport = {id:"rep2",data:state,importedAt:3,exportedAt:4};

// Successful restore persists new state and removes only the restored received report.
{
  const storage = makeStorage({supplace_state_v1:JSON.stringify(state),supplace_received_reports_v1:JSON.stringify([report,otherReport])});
  const api = boot(storage);
  api.setState(structuredClone(state)); api.setReports([structuredClone(report),structuredClone(otherReport)]);
  api.restore("rep1");
  assert.equal(api.getState().inventory.sup1, 8);
  assert.equal(api.getReports().length, 1);
  assert.equal(api.getReports()[0].id, "rep2");
  assert.equal(JSON.parse(storage.dump().supplace_state_v1).inventory.sup1, 8);
  assert.deepEqual(JSON.parse(storage.dump().supplace_received_reports_v1).map(x=>x.id), ["rep2"]);
  assert.ok(api.messages.some(x=>x.includes("отчёт удалён")));
}

// If restored working state cannot be persisted, keep both previous state and received report.
{
  const storage = makeStorage({supplace_state_v1:JSON.stringify(state),supplace_received_reports_v1:JSON.stringify([report])}, "supplace_state_v1");
  const api = boot(storage);
  api.setState(structuredClone(state)); api.setReports([structuredClone(report)]);
  api.restore("rep1");
  assert.equal(api.getState().inventory.sup1, 4);
  assert.equal(api.getReports().length, 1);
  assert.ok(api.messages.some(x=>x.includes("Не удалось сохранить")));
}

// Manual received-report deletion must not lie if report storage cannot be updated.
{
  const storage = makeStorage({supplace_received_reports_v1:JSON.stringify([report])}, "supplace_received_reports_v1");
  const api = boot(storage);
  api.setReports([structuredClone(report)]);
  api.deleteReport("rep1");
  assert.equal(api.getReports().length, 1);
  assert.ok(api.messages.some(x=>x.includes("Не удалось удалить отчёт")));
}

console.log("Report restore: auto-delete and persistence failure handling passed");
