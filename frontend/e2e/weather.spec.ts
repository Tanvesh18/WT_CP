import { test, expect } from '@playwright/test'

test('automatically displays weather alerts in the trip and planner', async ({ page }) => {
  const date = new Date().toISOString().slice(0, 10)
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/auth/me') ? { name: 'Test Traveler', email: 'test@example.com', role: 'TRAVELER' } : path.endsWith('/trips') ? [{ id: 1, traveler: 'Test Traveler', origin: 'Mumbai', destination: 'London', originAirportCode: 'BOM', destinationAirportCode: 'LHR', startDate: date, endDate: date, status: 'ON_TRACK', riskLevel: 'LOW', riskReason: '', checkInStatus: 'UNKNOWN', items: [] }] : []
    await route.fulfill({ json: data })
  })
  await page.route('https://geocoding-api.open-meteo.com/**', route => route.fulfill({ json: { results: [{ name: 'Mumbai', country: 'India', latitude: 19, longitude: 73 }, { name: 'London', country: 'United Kingdom', latitude: 51, longitude: 0 }] }, headers: { 'access-control-allow-origin': '*' } }))
  await page.route('https://api.open-meteo.com/**', route => route.fulfill({ json: { daily: { time: [date], weather_code: [95], temperature_2m_max: [28], temperature_2m_min: [20], precipitation_probability_max: [90], wind_speed_10m_max: [55] } }, headers: { 'access-control-allow-origin': '*' } }))
  await page.goto('/')
  await page.getByRole('button', { name: /^My trips$/ }).first().click()
  await page.locator('.trip-list__item').first().click()
  await expect(page.getByText('Weather needs attention')).toHaveCount(2)
  await expect(page.getByText(/winds of at least 50 km\/h/).first()).toBeVisible()
  await expect(page.getByRole('button', { name: /check weather/i })).toHaveCount(0)
  await page.getByRole('button', { name: /Book a trip/i }).first().click()
  await page.getByRole('combobox', { name: 'Departure airport' }).fill('Mumbai')
  await page.getByRole('option', { name: /Mumbai/ }).first().click()
  await page.getByRole('combobox', { name: 'Destination airport' }).fill('London')
  await page.getByRole('option', { name: /London/ }).first().click()
  await page.getByLabel('Departure date').fill(date)
  await page.getByLabel('Trip end date', { exact: true }).fill(date)
  await expect(page.getByText('Weather needs attention')).toHaveCount(2)
})

