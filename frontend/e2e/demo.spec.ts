import { expect, test, type Page, type TestInfo } from '@playwright/test'

async function captureWidths(page: Page, info: TestInfo, name: string) {
  for (const width of [390, 768, 1366, 1920]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 900 })
    await expect(page.locator('.shell')).toBeVisible()
    await page.screenshot({ path: info.outputPath(`${name}-${width}.png`), fullPage: true })
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow, `${name} at ${width}px has horizontal overflow`).toBeLessThanOrEqual(1)
  }
}

const dateAfter = (days: number) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
}

test('offline coordinator and traveler journeys cover every disruption type', async ({ browser }, testInfo) => {
  const email = `demo-${Date.now()}@example.test`
  const password = 'demo-pass-2026'
  const traveler = await browser.newPage()
  await traveler.goto('/')
  await traveler.getByRole('button', { name: 'Create an account' }).click()
  await traveler.getByLabel('Full name').fill('Demo Traveler')
  await traveler.getByLabel('Email address').fill(email)
  await traveler.getByLabel('Password', { exact: true }).fill(password)
  await traveler.getByLabel('Confirm password').fill(password)
  await traveler.getByRole('button', { name: 'Create traveler account' }).click()
  await expect(traveler.getByText('Traveler workspace')).toBeVisible()
  await traveler.getByRole('button', { name: /Sign out/ }).click()

  const coordinator = await browser.newPage()
  await coordinator.goto('/')
  await coordinator.getByLabel('Email address').fill(process.env.E2E_COORDINATOR_EMAIL || 'coordinator@tripshield.test')
  await coordinator.getByLabel('Password').fill(process.env.E2E_COORDINATOR_PASSWORD || 'integration-test-coordinator-password')
  await coordinator.getByRole('button', { name: 'Sign in' }).click()
  await expect(coordinator.getByText('Coordinator workspace')).toBeVisible()
  await captureWidths(coordinator, testInfo, 'coordinator-overview')
  await coordinator.getByRole('button', { name: /New trip/ }).click()

  await coordinator.getByRole('combobox', { name: 'Departure airport' }).fill('Pune')
  await coordinator.getByRole('option', { name: /Pune PNQ/ }).click()
  await coordinator.getByRole('combobox', { name: 'Destination airport' }).fill('Delhi')
  await coordinator.getByRole('option', { name: /Delhi DEL/ }).click()
  await coordinator.getByLabel('Departure date').fill(dateAfter(3))
  await coordinator.getByLabel('Trip end date', { exact: true }).fill(dateAfter(5))
  await coordinator.getByLabel('Registered traveler').selectOption({ label: `Demo Traveler · ${email}` })
  await coordinator.getByRole('button', { name: /Continue/ }).click()
  await coordinator.getByRole('button', { name: 'Search flights' }).click()
  await expect(coordinator.getByText(/Offline demo schedules and estimated prices/)).toBeVisible()
  await expect(coordinator.getByRole('button', { name: 'Select flight' }).first()).toBeVisible()
  await coordinator.getByRole('button', { name: 'Select flight' }).first().click()
  await coordinator.getByRole('button', { name: /Continue/ }).click()
  await coordinator.getByRole('button', { name: /City centre business stay/ }).click()
  await coordinator.getByRole('button', { name: /Airport pickup · sedan/ }).click()
  await coordinator.getByRole('button', { name: /Continue/ }).click()
  await expect(coordinator.getByText('Offline demo schedule').first()).toBeVisible()
  await coordinator.getByRole('button', { name: 'Save trip' }).click()
  await expect(coordinator.getByText('Trip created.')).toBeVisible()
  await expect(coordinator.getByRole('heading', { name: 'Itinerary timeline' })).toBeVisible()
  await expect(coordinator.getByRole('img', { name: /Route through PNQ, DEL/ })).toBeVisible()
  await captureWidths(coordinator, testInfo, 'coordinator-trip')

  async function disrupt(button: string, expected: string) {
    await coordinator.getByRole('button', { name: button }).first().click()
    await expect(coordinator.getByRole('heading', { name: 'Review recovery options' })).toBeVisible()
    if (button === 'Simulate hotel issue') {
      await expect(coordinator.getByRole('button', { name: 'Close dialog' })).toBeFocused()
      await coordinator.screenshot({ path: testInfo.outputPath('recovery-comparison-desktop.png'), fullPage: true })
      await coordinator.setViewportSize({ width: 390, height: 844 })
      await coordinator.screenshot({ path: testInfo.outputPath('recovery-comparison-mobile.png'), fullPage: true })
      await coordinator.setViewportSize({ width: 1366, height: 900 })
      await coordinator.keyboard.press('Escape')
      await expect(coordinator.getByRole('heading', { name: 'Review recovery options' })).toBeHidden()
      await coordinator.getByRole('button', { name: /Review alternatives/ }).first().click()
    }
    await expect(coordinator.getByText(expected).first()).toBeVisible()
    await coordinator.getByRole('button', { name: /Apply option|Use in itinerary/ }).first().click()
    await expect(coordinator.getByRole('heading', { name: 'Update this itinerary?' })).toBeVisible()
    if (button === 'Simulate hotel issue') {
      await coordinator.getByRole('button', { name: 'Back to options' }).click()
      await expect(coordinator.getByRole('heading', { name: 'Update this itinerary?' })).toBeHidden()
      await coordinator.getByRole('button', { name: /Apply option|Use in itinerary/ }).first().click()
    }
    await coordinator.getByRole('button', { name: 'Confirm replacement' }).click()
    await expect(coordinator.getByRole('heading', { name: 'Review recovery options' })).toBeHidden()
    await expect(coordinator.getByText(/Recovery option applied|Flight schedule added/)).toBeVisible()
  }

  await disrupt('Simulate hotel issue', 'SIMULATED OPTION')
  await disrupt('Simulate transport issue', 'SIMULATED OPTION')
  await coordinator.getByRole('button', { name: 'Simulate weather' }).first().click()
  await expect(coordinator.getByRole('heading', { name: 'Review recovery options' })).toBeVisible()
  await expect(coordinator.getByRole('heading', { name: 'Connected itinerary at risk' })).toBeVisible()
  await expect(coordinator.getByText('OFFLINE DEMO SCHEDULE').first()).toBeVisible()
  await coordinator.getByRole('button', { name: 'Refresh flight options' }).click()
  await coordinator.getByRole('button', { name: 'Use in itinerary' }).first().click()
  await coordinator.getByRole('button', { name: 'Confirm replacement' }).click()
  await expect(coordinator.getByRole('heading', { name: 'Review recovery options' })).toBeHidden()
  await disrupt('Simulate cancellation', 'OFFLINE DEMO SCHEDULE')

  await traveler.goto('/')
  await traveler.getByLabel('Email address').fill(email)
  await traveler.getByLabel('Password').fill(password)
  await traveler.getByRole('button', { name: 'Sign in' }).click()
  await expect(traveler.getByText('Traveler workspace')).toBeVisible()
  await traveler.getByRole('button', { name: /My trips/ }).first().click()
  await traveler.locator('.trip-list__item').first().click()
  await expect(traveler.getByRole('heading', { name: 'Itinerary timeline' })).toBeVisible()
  await expect(traveler.getByText('Offline demo schedule').first()).toBeVisible()
  await traveler.getByRole('button', { name: 'View updates' }).click()
  await expect(traveler.getByText(/A recovery option was added to your itinerary/).first()).toBeVisible()
  await traveler.getByRole('button', { name: 'Hide updates' }).click()
  await captureWidths(traveler, testInfo, 'traveler-trip')
  await expect(traveler.getByRole('button', { name: /Simulate/ })).toHaveCount(0)
  await expect(traveler.getByRole('button', { name: 'Use in itinerary' })).toHaveCount(0)
  const forbidden = await traveler.evaluate(async () => {
    const token = sessionStorage.getItem('tripshield-token')
    const tripId = document.querySelector('.trip-detail__head .eyebrow')?.textContent?.match(/\d+/)?.[0]
    return fetch(`/api/trips/${tripId}/disruptions`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: 'SEVERE_WEATHER', itemId: 1 }),
    }).then(response => response.status)
  })
  expect(forbidden).toBe(403)
  await traveler.getByRole('button', { name: 'Request help' }).click()
  await expect(traveler.getByText('Help request recorded for your coordinator.')).toBeVisible()
  await traveler.screenshot({ path: testInfo.outputPath('traveler-desktop.png'), fullPage: true })
  await traveler.setViewportSize({ width: 390, height: 844 })
  await expect(traveler.getByRole('heading', { name: 'Itinerary timeline' })).toBeVisible()
  await traveler.screenshot({ path: testInfo.outputPath('traveler-mobile.png'), fullPage: true })
  const overflow = await traveler.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow).toBeLessThanOrEqual(1)

  await coordinator.close()
  await traveler.close()
})

test('flight search shows loading, failure, and offline retry', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Email address').fill(process.env.E2E_COORDINATOR_EMAIL || 'coordinator@tripshield.test')
  await page.getByLabel('Password').fill(process.env.E2E_COORDINATOR_PASSWORD || 'integration-test-coordinator-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.getByRole('button', { name: /New trip/ }).click()
  await page.getByRole('combobox', { name: 'Departure airport' }).fill('Pune')
  await page.getByRole('option', { name: /Pune PNQ/ }).click()
  await page.getByRole('combobox', { name: 'Destination airport' }).fill('Delhi')
  await page.getByRole('option', { name: /Delhi DEL/ }).click()
  await page.getByLabel('Departure date').fill(dateAfter(3))
  await page.getByLabel('Trip end date', { exact: true }).fill(dateAfter(5))
  await page.getByLabel('Traveler name').fill('Error Demo')
  await page.getByLabel('Traveler email').fill(`error-demo-${Date.now()}@example.test`)
  await page.getByRole('button', { name: /Continue/ }).click()
  await page.route('**/api/flights/search', async route => {
    await new Promise(resolve => setTimeout(resolve, 400))
    await route.fulfill({ status: 503, contentType: 'application/json', body: '{"detail":"Flight search temporarily unavailable"}' })
  })
  await page.getByRole('button', { name: 'Search flights' }).click()
  await expect(page.getByText('Checking airline offers. This can take a few moments…')).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('Flight search temporarily unavailable')
  await page.unrouteAll()
  await page.getByRole('button', { name: 'Try again' }).click()
  await expect(page.getByText(/Offline demo schedules and estimated prices/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Select flight' }).first()).toBeVisible()
})
