import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = dirname(fileURLToPath(import.meta.url))
const secrets = Object.fromEntries(readFileSync(join(root, 'config/demo-secrets.properties'), 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/).filter(line => line.includes('=')).map(line => {
  const at = line.indexOf('='); return [line.slice(0, at).trim(), line.slice(at + 1).trim()]
}))
const base = process.env.DEMO_API_URL || 'http://127.0.0.1:18084'
if (!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base)) throw new Error('Demo API must be a local HTTP endpoint with an explicit port.')
const identity = await fetch(`${base}/api/demo/identity`).then(response => response.json())
if (identity.mode !== 'DEMO' || identity.database !== 'tripshield_demo') throw new Error('Backend is not connected to the isolated demo schema.')
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const dateAfter = days => { const date = new Date(`${today}T00:00:00Z`); date.setUTCDate(date.getUTCDate() + days); return date.toISOString().slice(0, 10) }
const local = (iso, zone) => new Intl.DateTimeFormat('sv-SE', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(iso)).replace(' ', 'T')
const after = (iso, minutes, zone) => local(new Date(new Date(iso).getTime() + minutes * 60000), zone)
async function request(path, token, method = 'GET', body, key) {
  const response = await fetch(`${base}/api${path}`, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}), ...(key ? { 'Idempotency-Key': key } : {}) }, body: body ? JSON.stringify(body) : undefined })
  const value = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status} ${value.detail || value.message || ''}`)
  return value
}
async function traveler(name, email) {
  try { return await request('/auth/register', null, 'POST', { name, email, password: secrets['demo.traveler.password'] }) }
  catch (error) { if (!String(error).includes('HTTP 409')) throw error; return request('/auth/login', null, 'POST', { email, password: secrets['demo.traveler.password'] }) }
}
const people = [
  ['Maya Rao', 'maya@demo.tripshield.test'],
  ['Arjun Mehta', 'arjun@demo.tripshield.test'],
  ['Nisha Patel', 'nisha@demo.tripshield.test'],
]
for (const [name, email] of people) await traveler(name, email)
const coordinator = await request('/auth/login', null, 'POST', { email: 'coordinator@demo.tripshield.test', password: secrets['demo.coordinator.password'] })
if (coordinator.role !== 'COORDINATOR') throw new Error('Demo coordinator login failed.')
const token = coordinator.token
const directory = await request('/travelers', token)
const owners = Object.fromEntries(directory.map(person => [person.email, person.id]))
const flights = async (origin, destination, date) => {
  const result = await request('/flights/search', token, 'POST', { origin, destination, departureDate: date })
  if (result.mode !== 'DEMO' || result.offers.length < 3) throw new Error(`Offline demo offers unavailable for ${origin}-${destination} on ${date}.`)
  return result.offers
}
const flightItem = offer => ({ kind: 'FLIGHT', title: offer.title, location: `${offer.origin} → ${offer.destination}`, startsAt: local(offer.departingAt, offer.originTimeZone), endsAt: local(offer.arrivingAt, offer.destinationTimeZone), flightSource: offer.source, flightOfferId: offer.id, flightAmount: offer.totalAmount, flightCurrency: offer.totalCurrency, flightExpiresAt: offer.expiresAt })
const stay = (title, city, arrival, zone, checkout, cost) => ({ kind: 'HOTEL', title, location: city, startsAt: after(arrival, 150, zone), endsAt: `${checkout}T11:00`, estimatedCost: cost, costCurrency: 'INR' })
const transfer = (title, airport, city, arrival, zone, cost) => ({ kind: 'TRANSPORT', title, location: `${airport} airport → ${city}`, startsAt: after(arrival, 45, zone), endsAt: after(arrival, 95, zone), estimatedCost: cost, costCurrency: 'INR' })
const scenarios = [
  { code: 'A', traveler: people[0], origin: 'Mumbai', destination: 'London', from: 'BOM', to: 'LHR', depart: dateAfter(3), end: dateAfter(6), checkout: dateAfter(5), returnDate: dateAfter(5), hotel: 'South Bank business stay · simulated', transfer: 'Heathrow express car · simulated', event: 'FLIGHT_CANCELLATION', affectedKind: 'FLIGHT', recover: false, budget: 145000 },
  { code: 'B', traveler: people[1], origin: 'Pune', destination: 'New Delhi', from: 'PNQ', to: 'DEL', depart: dateAfter(5), end: dateAfter(7), checkout: dateAfter(7), hotel: 'Connaught Place business stay · simulated', transfer: 'Delhi airport pickup · simulated', event: 'SEVERE_WEATHER', affectedKind: 'FLIGHT', recover: true, budget: 30000 },
  { code: 'C', traveler: people[2], origin: 'New Delhi', destination: 'Dubai', from: 'DEL', to: 'DXB', depart: dateAfter(9), end: dateAfter(11), checkout: dateAfter(11), hotel: 'Downtown Dubai business stay · simulated', transfer: 'DXB airport private car · simulated', event: 'HOTEL_UNAVAILABLE', affectedKind: 'HOTEL', recover: true, budget: 68000 },
  { code: 'D', traveler: people[2], origin: 'Bengaluru', destination: 'Hyderabad', from: 'BLR', to: 'HYD', depart: dateAfter(12), end: dateAfter(14), checkout: dateAfter(14), hotel: 'HITEC City business stay · simulated', transfer: 'Hyderabad airport car · simulated', event: 'TRANSPORT_DISRUPTION', affectedKind: 'TRANSPORT', recover: false, budget: 26000 },
]
const existing = await request('/trips', token)
const summary = []
for (const plan of scenarios) {
  const [name, email] = plan.traveler
  let trip = existing.find(value => value.travelerEmail === email && value.startDate === plan.depart && value.originAirportCode === plan.from && value.destinationAirportCode === plan.to)
  if (!trip) {
    const outboundOffers = await flights(plan.from, plan.to, plan.depart)
    const outbound = plan.code === 'A' ? outboundOffers.find(offer => offer.stops === 1) : outboundOffers[0]
    if (!outbound) throw new Error(`Scenario ${plan.code} has no valid outbound schedule.`)
    const destinationZone = outbound.destinationTimeZone
    const items = [flightItem(outbound), transfer(plan.transfer, plan.to, plan.destination, outbound.arrivingAt, destinationZone, 1800), stay(plan.hotel, plan.destination, outbound.arrivingAt, destinationZone, plan.checkout, plan.code === 'A' ? 39000 : 14500)]
    if (plan.returnDate) items.push(flightItem((await flights(plan.to, plan.from, plan.returnDate))[4]))
    trip = await request('/trips', token, 'POST', { traveler: name, travelerEmail: email, travelerUserId: owners[email], origin: plan.origin, destination: plan.destination, originAirportCode: plan.from, destinationAirportCode: plan.to, startDate: plan.depart, endDate: plan.end, estimatedBudget: plan.budget, budgetCurrency: 'INR', items }, `tripshield-demo-${plan.code}-${today}`)
  }
  const original = trip.items.find(item => item.kind === plan.affectedKind && !item.changeNote?.startsWith('Replaced '))
  if (!original) throw new Error(`Scenario ${plan.code} is missing its original ${plan.affectedKind}.`)
  let impact
  if (original.status === 'CONFIRMED') {
    impact = await request(`/trips/${trip.id}/disruptions`, token, 'POST', { type: plan.event, itemId: original.id })
  } else if (original.status === 'AFFECTED') {
    impact = await request(`/trips/${trip.id}/items/${original.id}/alternatives`, token)
  } else if (original.status !== 'REPLACED') throw new Error(`Scenario ${plan.code} has unexpected original status ${original.status}.`)
  if (impact && impact.alternatives.length < 2) throw new Error(`Scenario ${plan.code} has fewer than two recovery options.`)
  if (plan.recover && original.status !== 'REPLACED') {
    await request(`/trips/${trip.id}/items/${original.id}/alternatives/${impact.alternatives[0].id}/apply`, token, 'POST')
  }
  if (plan.code === 'B') {
    for (const kind of ['TRANSPORT', 'HOTEL']) {
      const current = await request(`/trips/${trip.id}`, token)
      const dependent = current.items.find(item => item.kind === kind && item.status === 'AT_RISK')
      if (!dependent) continue
      const choices = await request(`/trips/${trip.id}/items/${dependent.id}/alternatives`, token)
      if (!choices.alternatives.length) throw new Error(`Weather scenario has no valid ${kind} connection recovery.`)
      await request(`/trips/${trip.id}/items/${dependent.id}/alternatives/${choices.alternatives[0].id}/apply`, token, 'POST')
    }
  }
  const saved = await request(`/trips/${trip.id}`, token)
  summary.push({ scenario: plan.code, tripId: trip.id, traveler: name, route: `${plan.from}-${plan.to}`, date: plan.depart, originalStatus: saved.items.find(item => item.id === original.id)?.status, alternatives: impact?.alternatives.length ?? 'previously generated' })
}
console.log(JSON.stringify({ database: identity.database, mode: identity.mode, scenarios: summary }, null, 2))
