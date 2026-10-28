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
  vm.runInNewContext(js, { exports: module.exports, module, require: id => id === './airportCoordinates.json' ? require(path.join(__dirname, '..', 'src', 'services', 'airportCoordinates.json')) : require(id) }, { filename: source })
  return module.exports
}
const { validateTripDraft } = load('tripValidation.ts')
const { filterTrips, emptyCriteria } = load('tripFilters.ts')
const valid = { traveler: 'Ana', travelerEmail: 'ana@example.test', origin: 'Pune', destination: 'Delhi', startDate: '2026-11-10', endDate: '2026-11-12', items: [{ kind: 'HOTEL', title: 'Flight', location: 'Pune', startsAt: '2026-11-10T09:00', endsAt: '2026-11-10T11:00' }] }
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

const { searchAirports } = load('airports.ts')
const { offerExpired, money } = load('flightOffers.ts')
test('airport search matches city and IATA code while excluding selected airport', () => {
  assert.ok(searchAirports('pune').some(airport => airport.code === 'PNQ'))
  assert.ok(searchAirports('DEL').some(airport => airport.city === 'New Delhi'))
  assert.equal(searchAirports('Pune', 'PNQ').length, 0)
})
test('flight offer selection rejects expired offers and formats currency', () => {
  assert.equal(offerExpired({ expiresAt: '2000-01-01T00:00:00Z' }), true)
  assert.equal(offerExpired({ expiresAt: '2999-01-01T00:00:00Z' }), false)
  assert.match(money('1234.50', 'INR'), /1,234/)
})

const { actionForTrip, activeToday, attentionTrips, departuresSoon, upcomingTrips, nextSegment } = load('dashboard.ts')
test('overview counts trips by dates and prioritizes help requests', () => {
  const base = { traveler: 'Ana', travelerEmail: 'ana@example.test', origin: 'Pune', destination: 'Delhi', endDate: '2026-11-12', riskLevel: 'LOW', checkInStatus: 'PENDING', items: [] }
  const trips = [
    { ...base, id: 1, startDate: '2026-11-10', status: 'ON_TRACK' },
    { ...base, id: 2, startDate: '2026-11-11', status: 'NEEDS_ATTENTION', riskLevel: 'HIGH', checkInStatus: 'NEEDS_HELP' },
    { ...base, id: 3, startDate: '2026-11-10', status: 'CANCELLED' },
  ]
  assert.deepEqual(Array.from(activeToday(trips, '2026-11-10'), t => t.id), [1])
  assert.deepEqual(Array.from(departuresSoon(trips, '2026-11-10'), t => t.id), [1, 2])
  assert.deepEqual(Array.from(attentionTrips(trips), t => t.id), [2])
  assert.deepEqual(Array.from(upcomingTrips(trips, '2026-11-10'), t => t.id), [1, 2])
})
test('action center classifies saved trip facts and excludes closed trips', () => {
  const base = { id: 1, traveler: 'Ana', origin: 'Pune', destination: 'Delhi', startDate: '2026-11-10', endDate: '2026-11-12', status: 'ON_TRACK', riskLevel: 'LOW', checkInStatus: 'PENDING', items: [] }
  assert.equal(actionForTrip({ ...base, status: 'REQUESTED' }).label, 'Review request')
  assert.equal(actionForTrip({ ...base, checkInStatus: 'NEEDS_HELP', checkInNote: 'Stranded' }).label, 'Open assistance')
  assert.equal(actionForTrip({ ...base, items: [{ status: 'AFFECTED', title: 'Flight' }] }).label, 'Review recovery')
  assert.equal(actionForTrip({ ...base, status: 'NEEDS_ATTENTION', riskReason: 'One unresolved disruption' }).label, 'Review recovery')
  assert.equal(actionForTrip({ ...base, budgetExceeded: true, items: [{ status: 'AT_RISK' }] }).label, 'Review recovery')
  assert.equal(actionForTrip({ ...base, budgetExceeded: true }).label, 'Review budget')
  assert.equal(actionForTrip(base).label, 'View trip')
  assert.deepEqual(Array.from(attentionTrips([{ ...base, status: 'REQUESTED' }, { ...base, id: 2, budgetExceeded: true }, { ...base, id: 3, status: 'CANCELLED', budgetExceeded: true }]), trip => trip.id), [1, 2])
})
test('coordinator Action Center renders a reason and specific action', () => {
  const React = require('react')
  const { renderToStaticMarkup } = require('react-dom/server')
  const code = fs.readFileSync(path.join(__dirname, '..', 'src', 'pages', 'Dashboards.tsx'), 'utf8')
  const js = ts.transpileModule(code, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText
  const module = { exports: {} }
  const ui = { Button: ({ children, ...props }) => React.createElement('button', props, children), EmptyState: ({ children }) => React.createElement('div', null, children), StatusBadge: ({ status }) => React.createElement('span', null, status) }
  vm.runInNewContext(js, { exports: module.exports, module, require: id => id === 'react/jsx-runtime' ? require(id) : id === '../components/format' ? { formatDate: value => value } : id === '../components/ui' ? ui : id === '../services/dashboard' ? load('dashboard.ts') : require(id) })
  const trip = { id: 7, traveler: 'Ana', origin: 'Pune', destination: 'Delhi', startDate: '2026-11-10', endDate: '2026-11-12', status: 'NEEDS_ATTENTION', riskLevel: 'HIGH', checkInStatus: 'PENDING', items: [{ status: 'AFFECTED', changeNote: 'Flight cancelled' }] }
  const html = renderToStaticMarkup(React.createElement(module.exports.CoordinatorDashboard, { trips: [trip], onOpen: () => {}, onTrips: () => {}, onNewTrip: () => {} }))
  assert.match(html, /ACTION CENTER/)
  assert.match(html, /Review recovery/)
  assert.match(html, /1 itinerary item needs attention/)
  assert.match(html, /Review recovery: Ana, Pune to Delhi/)
})
test('traveler overview chooses next active itinerary segment', () => {
  const trip = { items: [{ id: 1, status: 'REPLACED', startsAt: '2026-11-10T08:00', endsAt: '2026-11-10T09:00' }, { id: 2, status: 'CONFIRMED', startsAt: '2026-11-10T10:00', endsAt: '2026-11-10T12:00' }] }
  assert.equal(nextSegment(trip, '2026-11-10T09:30').id, 2)
})

const { routePoints } = load('routeGeometry.ts')
test('flight map keeps actual connecting and technical airport coordinates', () => {
  const legs = [
    { origin: 'PNQ', originName: 'Pune', originLatitude: 18.58, originLongitude: 73.9, destination: 'BOM', destinationName: 'Mumbai', destinationLatitude: 19.09, destinationLongitude: 72.87, technicalStops: [] },
    { origin: 'BOM', destination: 'DEL', destinationName: 'Delhi', destinationLatitude: 28.55, destinationLongitude: 77.1, technicalStops: [{ code: 'JAI', name: 'Jaipur', latitude: 26.82, longitude: 75.8 }] },
  ]
  assert.deepEqual(Array.from(routePoints(JSON.stringify(legs)), point => point.code), ['PNQ', 'BOM', 'JAI', 'DEL'])
  assert.equal(routePoints('invalid').length, 0)
})

test('flight map pins PNQ and DEL to reference coordinates and ignores bad offer coordinates', () => {
  const legs = [{ origin: 'PNQ', originLatitude: 0, originLongitude: 0, destination: 'DEL', destinationLatitude: 64, destinationLongitude: -141 }]
  const points = routePoints(JSON.stringify(legs), 'PNQ → DEL')
  assert.deepEqual(Array.from(points, point => point.code), ['PNQ', 'DEL'])
  assert.ok(Math.abs(points[0].lat - 18.5821) < 0.001)
  assert.ok(Math.abs(points[0].lon - 73.919701) < 0.001)
  assert.ok(Math.abs(points[1].lat - 28.55563) < 0.001)
  assert.ok(Math.abs(points[1].lon - 77.09519) < 0.001)
})
test('flight map falls back to selected route when offer legs disagree', () => {
  const legs = [{ origin: 'BOM', destination: 'JAI' }]
  assert.deepEqual(Array.from(routePoints(JSON.stringify(legs), 'PNQ → DEL'), point => point.code), ['PNQ', 'DEL'])
  assert.deepEqual(Array.from(routePoints(null, 'PNQ → DEL'), point => point.code), ['PNQ', 'DEL'])
})

test('manual flight segment is rejected', () => {
  const issue = validateTripDraft({ ...valid, items: [{ ...valid.items[0], kind: 'FLIGHT' }] })
  assert.match(issue, /selected flight schedule/)
})
