const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
function load(source) {
  const code = fs.readFileSync(path.join(__dirname, '..', 'src', 'services', source), 'utf8')
  const js = ts.transpileModule(code, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const module = { exports: {} }
  vm.runInNewContext(js, { exports: module.exports, module }, { filename: source })
  return module.exports
}
const { validateTripDraft } = load('tripValidation.ts')
const { filterTrips, emptyCriteria } = load('tripFilters.ts')
const valid = { traveler: 'Ana', travelerEmail: 'ana@example.test', origin: 'Pune', destination: 'Delhi', startDate: '2026-11-10', endDate: '2026-11-12', items: [{ kind: 'FLIGHT', title: 'Flight', location: 'Pune', startsAt: '2026-11-10T09:00', endsAt: '2026-11-10T11:00' }] }
test('trip form accepts a valid itinerary and rejects items outside its dates', () => {
  assert.equal(validateTripDraft(valid), null)
  assert.match(validateTripDraft({ ...valid, items: [{ ...valid.items[0], endsAt: '2026-11-13T11:00' }] }), /within the trip dates/)
  assert.match(validateTripDraft({ ...valid, items: [{ ...valid.items[0], endsAt: '2026-11-10T08:00' }] }), /end after/)
})
test('trip filters combine traveler, risk, and overlapping date range', () => {
  const trips = [
    { id: 1, traveler: 'Ana', travelerEmail: 'ana@example.test', origin: 'Pune', destination: 'Delhi', startDate: '2026-11-10', endDate: '2026-11-12', status: 'ON_TRACK', riskLevel: 'LOW' },
    { id: 2, traveler: 'Bo', travelerEmail: 'bo@example.test', origin: 'Delhi', destination: 'Mumbai', startDate: '2026-11-11', endDate: '2026-11-15', status: 'NEEDS_ATTENTION', riskLevel: 'HIGH' },
  ]
  assert.deepEqual(Array.from(filterTrips(trips, { ...emptyCriteria(), from: '2026-11-12', to: '2026-11-12' }), t => t.id), [1, 2])
  assert.deepEqual(Array.from(filterTrips(trips, { ...emptyCriteria(), query: 'delhi', risk: 'HIGH' }), t => t.id), [2])
  assert.deepEqual(Array.from(filterTrips(trips, { ...emptyCriteria(), sort: 'RISK' }), t => t.id), [2, 1])
})
