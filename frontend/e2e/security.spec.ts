import { expect, request, test } from '@playwright/test'

const coordinatorEmail = process.env.E2E_COORDINATOR_EMAIL || 'coordinator@tripshield.test'
const coordinatorPassword = process.env.E2E_COORDINATOR_PASSWORD || 'integration-test-coordinator-password'

test('browser cookie session rotates, survives refresh, and enforces CSRF and roles', async ({ browser, baseURL }) => {
  const context = await browser.newContext()
  const page = await context.newPage()
  const travelerEmail = `security-${Date.now()}@example.test`
  const travelerPassword = 'security-test-2026'
  try {
    await page.goto('/')
    await page.getByLabel('Email address').fill(coordinatorEmail)
    await page.getByLabel('Password').fill('incorrect-password')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('alert')).toContainText('Invalid email or password')
    await page.getByLabel('Password').fill(coordinatorPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByText('Coordinator workspace')).toBeVisible()
    const first = (await context.cookies()).find(cookie => cookie.name === 'TRIPSHIELD_SESSION')
    const csrf = (await context.cookies()).find(cookie => cookie.name === 'TRIPSHIELD_CSRF')
    expect(first).toBeTruthy()
    expect(first?.httpOnly).toBe(true)
    expect(first?.sameSite).toBe('Lax')
    expect(first?.path).toBe('/api')
    expect(first?.secure).toBe(false)
    expect(csrf?.httpOnly).toBe(false)
    expect(await page.evaluate(() => sessionStorage.getItem('tripshield-token'))).toBeNull()
    await page.reload()
    await expect(page.getByText('Coordinator workspace')).toBeVisible()
    await page.goto('/trips')
    await expect(page.getByText('Coordinator workspace')).toBeVisible()
    const csrfDenied = await page.evaluate(() => fetch('/api/trips/999999/cancel', { method: 'POST' }).then(response => response.status))
    expect(csrfDenied).toBe(403)
    const secondLogin = await page.evaluate(async ({ email, password }) => {
      const response = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-TripShield-Browser': '1' }, body: JSON.stringify({ email, password }) })
      return response.status
    }, { email: coordinatorEmail, password: coordinatorPassword })
    expect(secondLogin).toBe(200)
    const rotated = (await context.cookies()).find(cookie => cookie.name === 'TRIPSHIELD_SESSION')
    expect(rotated?.value).not.toBe(first?.value)
    const oldContext = await request.newContext({ baseURL, extraHTTPHeaders: { Cookie: `TRIPSHIELD_SESSION=${first?.value}` } })
    try { expect((await oldContext.get('/api/auth/me')).status()).toBe(401) } finally { await oldContext.dispose() }
    await page.getByRole('button', { name: /Sign out/ }).click()
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
    await page.goto('/trips')
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
    expect((await page.request.get('/api/trips')).status()).toBe(401)

    await page.getByRole('button', { name: 'Create an account' }).click()
    await page.getByLabel('Full name').fill('Security Test Traveler')
    await page.getByLabel('Email address').fill(travelerEmail)
    await page.getByLabel('Password', { exact: true }).fill(travelerPassword)
    await page.getByLabel('Confirm password').fill(travelerPassword)
    await page.getByRole('button', { name: 'Create traveler account' }).click()
    await expect(page.getByText('Traveler workspace')).toBeVisible()
    expect((await page.request.get('/api/travelers')).status()).toBe(403)
  } finally { await context.close() }
})
