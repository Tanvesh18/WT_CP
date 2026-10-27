import { expect, test, type Page, type TestInfo } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

test.skip(process.env.DEMO_E2E !== '1', 'Run against the dedicated demo profile with DEMO_E2E=1.')
const secrets = Object.fromEntries((process.env.DEMO_E2E === '1' ? readFileSync(new URL('../../backend/config/demo-secrets.properties', import.meta.url), 'utf8') : '').replace(/^\uFEFF/, '').split(/\r?\n/).filter(line => line.includes('=')).map(line => { const at = line.indexOf('='); return [line.slice(0, at), line.slice(at + 1)] }))
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const day = (offset: number) => { const value = new Date(`${today}T00:00:00Z`); value.setUTCDate(value.getUTCDate() + offset); return value.toISOString().slice(0, 10) }

async function screenshots(page: Page, info: TestInfo, label: string) {
  for (const width of [390, 768, 1366, 1920]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 900 })
    await page.screenshot({ path: info.outputPath(`${label}-${width}.png`), fullPage: true })
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth), `${label} overflows at ${width}px`).toBeLessThanOrEqual(1)
  }
  await page.setViewportSize({ width: 1366, height: 900 })
}
async function signIn(page: Page, email: string, password: string) {
  await page.goto('/')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.locator('.shell')).toBeVisible()
}
async function selectTrip(page: Page, route: string) {
  await page.getByRole('button', { name: /^Trips$|^My trips$/ }).first().click()
  await page.locator('.trip-list__item').filter({ hasText: route }).first().click()
  await expect(page.getByRole('heading', { name: 'Itinerary timeline' })).toBeVisible()
}
async function currentTrip(page: Page) {
  const id = Number((await page.locator('.trip-detail__head .eyebrow').textContent())?.match(/\d+/)?.[0])
  return page.evaluate(async tripId => {
    const token = sessionStorage.getItem('tripshield-token')
    const response = await fetch(`/api/trips/${tripId}`, { headers: { Authorization: `Bearer ${token}` } })
    return response.json()
  }, id) as Promise<{ id: number; status: string; items: Array<{ id: number; kind: string; status: string; flightSource?: string; title: string }> }>
}
function persistedStatus(tripId: number, itemId: number) {
  const query = `SELECT status FROM trip_item WHERE trip_id=${tripId} AND id=${itemId}`
  const result = spawnSync('C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe', ['--host=127.0.0.1', '--port=3306', '--user=tripshield_demo_app', '--database=tripshield_demo', '--batch', '--skip-column-names', '--execute', query], { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: secrets['demo.db.password'] } })
  if (result.status !== 0) throw new Error('Direct isolated demo database verification failed.')
  return result.stdout.trim()
}
async function createTrip(page: Page, origin: string, originChoice: RegExp, destination: string, destinationChoice: RegExp, departure: string, end: string, person: string) {
  await page.getByRole('button', { name: /New trip/ }).click()
  await page.getByRole('button', { name: /Continue/ }).click()
  await expect(page.getByRole('alert')).toContainText('Choose two different airports')
  await page.getByRole('combobox', { name: 'Departure airport' }).fill(origin)
  await page.getByRole('option', { name: originChoice }).click()
  await page.getByRole('combobox', { name: 'Destination airport' }).fill(destination)
  await page.getByRole('option', { name: destinationChoice }).click()
  await page.getByLabel('Departure date').fill(departure)
  await page.getByLabel('Trip end date', { exact: true }).fill(end)
  const personId = await page.getByLabel('Registered traveler').locator('option').filter({ hasText: person }).getAttribute('value')
  expect(personId).toBeTruthy()
  await page.getByLabel('Registered traveler').selectOption(personId!)
  await page.getByRole('button', { name: /Continue/ }).click()
  await page.getByRole('button', { name: 'Search flights' }).click()
  await expect(page.getByText(/Offline demo schedules and estimated prices/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Select flight' }).first()).toBeVisible()
  await page.getByRole('button', { name: 'Select flight' }).first().click()
  await page.getByRole('button', { name: /Continue/ }).click()
  await page.getByRole('button', { name: /City centre business stay/ }).click()
  await page.getByRole('button', { name: /Airport pickup/ }).first().click()
  await page.getByRole('button', { name: /Continue/ }).click()
  await expect(page.getByText('Offline demo schedule').first()).toBeVisible()
  await page.getByRole('button', { name: 'Save trip' }).click()
  await expect(page.getByText('Trip created.')).toBeVisible()
  await expect(page.getByRole('img', { name: /Route through/ })).toBeVisible()
  const trip = await currentTrip(page)
  expect(trip.items.map(item => item.kind)).toEqual(expect.arrayContaining(['FLIGHT', 'HOTEL', 'TRANSPORT']))
  return trip
}
async function recover(page: Page, info: TestInfo, label: string, expectedKind: string) {
  await expect(page.getByRole('heading', { name: 'Review recovery options' })).toBeVisible()
  expect(await page.locator('.alternative').count()).toBeGreaterThanOrEqual(2)
  await expect(page.locator('.alternative--recommended')).toHaveCount(1)
  await screenshots(page, info, label)
  const trip = await currentTrip(page)
  const affected = trip.items.find(item => item.kind === expectedKind && item.status === 'AFFECTED')
  expect(affected).toBeTruthy()
  const optionId = await page.evaluate(async ({ tripId, itemId }) => {
    const token = sessionStorage.getItem('tripshield-token')
    const response = await fetch(`/api/trips/${tripId}/items/${itemId}/alternatives`, { headers: { Authorization: `Bearer ${token}` } })
    const data = await response.json()
    return data.alternatives[0]?.id as number
  }, { tripId: trip.id, itemId: affected!.id })
  expect(optionId).toBeTruthy()
  const button = page.getByRole('button', { name: /Use in itinerary|Apply option/ }).first()
  await button.click()
  await expect(page.getByRole('heading', { name: 'Update this itinerary?' })).toBeVisible()
  await expect(page.getByText('Current affected item')).toBeVisible()
  await screenshots(page, info, `${label}-confirm`)
  await page.getByRole('button', { name: 'Confirm replacement' }).click()
  await expect(page.getByRole('heading', { name: 'Review recovery options' })).toBeHidden()
  const updated = await currentTrip(page)
  expect(updated.items.find(item => item.id === affected!.id)?.status).toBe('REPLACED')
  expect(persistedStatus(trip.id, affected!.id)).toBe('REPLACED')
  return { tripId: trip.id, itemId: affected!.id, optionId }
}

test('seeded coordinator and traveler presentation walkthrough', async ({ browser }, info) => {
  const coordinator = await browser.newPage({ viewport: { width: 1366, height: 900 } })
  const identity = await coordinator.request.get('/api/demo/identity')
  expect(await identity.json()).toEqual({ mode: 'DEMO', database: 'tripshield_demo' })
  await signIn(coordinator, 'coordinator@demo.tripshield.test', secrets['demo.coordinator.password'])
  await expect(coordinator.getByText('Coordinator workspace')).toBeVisible()
  await expect(coordinator.locator('.overview-metric').filter({ hasText: 'Needs action' })).toContainText('2')
  await screenshots(coordinator, info, 'seeded-coordinator-dashboard')
  await selectTrip(coordinator, 'Mumbai')
  await expect(coordinator.getByText('South Bank business stay').first()).toBeVisible()
  await expect(coordinator.getByRole('img', { name: /Route through BOM, DXB, LHR/ })).toBeVisible()
  await coordinator.getByRole('button', { name: /Review alternatives/ }).first().click()
  const cancellation = await recover(coordinator, info, 'seeded-flight-cancellation', 'FLIGHT')
  const duplicate = await coordinator.evaluate(async ({ tripId, itemId, optionId }) => {
    const token = sessionStorage.getItem('tripshield-token')
    const response = await fetch(`/api/trips/${tripId}/items/${itemId}/alternatives/${optionId}/apply`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } })
    return response.status
  }, cancellation)
  expect(duplicate).toBe(400)

  await selectTrip(coordinator, 'Bengaluru')
  await coordinator.getByRole('button', { name: /Review alternatives/ }).first().click()
  await recover(coordinator, info, 'seeded-transport-recovery', 'TRANSPORT')
  await selectTrip(coordinator, 'Dubai')
  await expect(coordinator.getByText('Downtown Dubai business stay').first()).toBeVisible()
  await expect(coordinator.getByText(/Replaced Downtown Dubai business stay/).first()).toBeVisible()
  await coordinator.locator('.segment--confirmed').filter({ hasText: 'DXB airport private car' }).getByRole('button', { name: 'Simulate weather' }).click()
  await recover(coordinator, info, 'weather-ground-impact', 'TRANSPORT')

  const created = await createTrip(coordinator, 'Pune', /Pune PNQ/, 'Delhi', /Delhi DEL/, day(25), day(27), 'Maya Rao')
  await screenshots(coordinator, info, 'flight-selection-result')
  await coordinator.getByRole('button', { name: 'Simulate cancellation' }).first().click()
  const newCancellation = await recover(coordinator, info, 'new-flight-cancellation', 'FLIGHT')
  const duplicateApply = await coordinator.evaluate(async ({ tripId, itemId, optionId }) => {
    const token = sessionStorage.getItem('tripshield-token')
    const response = await fetch(`/api/trips/${tripId}/items/${itemId}/alternatives/${optionId}/apply`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } })
    return response.status
  }, newCancellation)
  expect(duplicateApply).toBe(400)
  expect(persistedStatus(created.id, created.items.find(item => item.kind === 'FLIGHT')!.id)).toBe('REPLACED')

  await createTrip(coordinator, 'Bengaluru', /Bengaluru BLR/, 'Delhi', /Delhi DEL/, day(29), day(31), 'Arjun Mehta')
  await coordinator.getByRole('button', { name: 'Simulate weather' }).first().click()
  await expect(coordinator.getByRole('heading', { name: 'Connected itinerary at risk' })).toBeVisible()
  await recover(coordinator, info, 'severe-weather-flight', 'FLIGHT')

  await coordinator.getByRole('button', { name: /Sign out/ }).click()
  await expect(coordinator.getByRole('button', { name: 'Sign in' })).toBeVisible()
  const traveler = await browser.newPage({ viewport: { width: 1366, height: 900 } })
  await signIn(traveler, 'maya@demo.tripshield.test', secrets['demo.traveler.password'])
  await expect(traveler.getByText('Traveler workspace')).toBeVisible()
  expect(await traveler.locator('.overview-row').count()).toBeGreaterThanOrEqual(2)
  await screenshots(traveler, info, 'seeded-traveler-dashboard')
  await selectTrip(traveler, 'Mumbai')
  await expect(traveler.getByText('Offline demo schedule').first()).toBeVisible()
  await expect(traveler.getByText('South Bank business stay').first()).toBeVisible()
  await expect(traveler.getByRole('button', { name: /Simulate|Review alternatives/ })).toHaveCount(0)
  await traveler.getByRole('button', { name: 'View updates' }).click()
  await expect(traveler.getByText(/A recovery option was added to your itinerary/).first()).toBeVisible()
  await screenshots(traveler, info, 'seeded-traveler-notifications')
  const forbidden = await traveler.evaluate(async tripId => {
    const token = sessionStorage.getItem('tripshield-token')
    return (await fetch(`/api/trips/${tripId}/items/1/alternatives`, { headers: { Authorization: `Bearer ${token}` } })).status
  }, cancellation.tripId)
  expect(forbidden).toBe(403)
  await traveler.getByRole('button', { name: 'Hide updates' }).click()
  await screenshots(traveler, info, 'seeded-traveler-itinerary')
  await traveler.setViewportSize({ width: 390, height: 844 })
  await traveler.getByRole('button', { name: 'Sign out' }).click()
  await expect(traveler.getByRole('button', { name: 'Sign in' })).toBeVisible()
  await coordinator.close(); await traveler.close()
})
