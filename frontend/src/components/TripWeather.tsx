import { useEffect, useState } from 'react'
import { airportByCode } from '../services/airports'

type Place = { name: string; country?: string; admin1?: string; latitude: number; longitude: number }
type Forecast = { daily: { time: string[]; weather_code: number[]; temperature_2m_max: number[]; temperature_2m_min: number[]; precipitation_probability_max: (number | null)[]; wind_speed_10m_max: number[] } }
function condition(code: number) {
  if (code === 0) return 'Clear'
  if (code <= 3) return 'Partly cloudy or overcast'
  if (code <= 48) return 'Fog'
  if (code <= 67 || (code >= 80 && code <= 82)) return 'Rain or showers'
  if (code <= 77 || code === 85 || code === 86) return 'Snow'
  if (code >= 95) return 'Thunderstorms'
  return 'Changing conditions'
}
function weatherVisual(code: number) {
  if (code === 0) return { icon: '☀️', tone: 'sunny', label: 'Clear skies' }
  if (code <= 3) return { icon: '⛅', tone: 'cloudy', label: 'Mixed skies' }
  if (code <= 48) return { icon: '🌫️', tone: 'foggy', label: 'Foggy' }
  if (code <= 67 || (code >= 80 && code <= 82)) return { icon: '🌧️', tone: 'rainy', label: 'Rain likely' }
  if (code <= 77 || code === 85 || code === 86) return { icon: '❄️', tone: 'snowy', label: 'Snow likely' }
  if (code >= 95) return { icon: '⛈️', tone: 'stormy', label: 'Storm risk' }
  return { icon: '🌤️', tone: 'changing', label: 'Changing skies' }
}
function displayDate(date: string) {
  return new Intl.DateTimeFormat(undefined, { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date(`${date}T12:00:00`))
}
export function TripWeather({ destination, airportCode, startDate, endDate }: { destination: string; airportCode?: string | null; startDate: string; endDate: string }) {
  const [result, setResult] = useState<{ place: Place; forecast: Forecast } | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      setBusy(true); setError(''); setResult(null)
      try {
        const airport = airportCode ? airportByCode(airportCode) : undefined
        const query = new URLSearchParams({ name: airport?.city || destination, count: '10', language: 'en', format: 'json' })
        const geo = await fetch('https://geocoding-api.open-meteo.com/v1/search?' + query, { signal: controller.signal })
        if (!geo.ok) throw new Error('Location search failed')
        const locations: Place[] = (await geo.json()).results || []
        const country = airport?.country === 'UAE' ? 'United Arab Emirates' : airport?.country
        const place = country ? locations.find(p => p.country === country) : locations[0]
        if (!place) throw new Error('Destination could not be located. Check the trip destination.')
        const params = new URLSearchParams({ latitude: String(place.latitude), longitude: String(place.longitude), daily: 'weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max', timezone: 'auto', forecast_days: '16' })
        const response = await fetch('https://api.open-meteo.com/v1/forecast?' + params, { signal: controller.signal })
        if (!response.ok) throw new Error('Weather service is unavailable')
        const forecast: Forecast = await response.json()
        if (!controller.signal.aborted) setResult({ place, forecast })
      } catch (cause) {
        if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Weather is unavailable')
      } finally { if (!controller.signal.aborted) setBusy(false) }
    }
    void load()
    const timer = window.setInterval(() => void load(), 30 * 60 * 1000)
    return () => { window.clearInterval(timer); controller.abort() }
  }, [destination, airportCode])
  const days = result?.forecast.daily.time.map((date, index) => ({ date, index })).filter(day => day.date >= startDate && day.date <= endDate) || []
  const warnings = result ? days.filter(({ index }) => [65, 67, 75, 77, 82, 86, 95, 96, 99].includes(result.forecast.daily.weather_code[index]) || result.forecast.daily.wind_speed_10m_max[index] >= 50) : []
  return <section className="detail-section" aria-label={`Weather for ${destination}`}>
    <div className="section-head"><div><span className="eyebrow">TRAVEL SAFETY</span><h3>Weather for {destination}</h3></div></div>
    {busy && <p role="status">Checking the forecast…</p>}
    {error && <p className="alert alert--error" role="alert">{error}. Please try again.</p>}
    {result && <div><p>Forecast for <strong>{[result.place.name, result.place.admin1, result.place.country].filter(Boolean).join(', ')}</strong> · <a href="https://open-meteo.com/" target="_blank" rel="noreferrer">Weather by Open-Meteo</a></p>
      {warnings.length > 0 && <div className="alert alert--error" role="alert"><strong>Weather needs attention</strong><p>Potentially disruptive weather is forecast on {warnings.map(day => day.date).join(', ')}. This alert covers heavy precipitation, thunderstorms, or winds of at least 50 km/h. Check official local warnings and your carrier before travelling.</p></div>}
      {days.length > 0 && warnings.length === 0 && <p className="weather-status" role="status"><span aria-hidden="true">✓</span> No heavy precipitation, thunderstorms, or winds of 50 km/h or more detected</p>}
      {days.length > 0 && (() => {
        const { index } = days[0]
        const visual = weatherVisual(result.forecast.daily.weather_code[index])
        const rain = result.forecast.daily.precipitation_probability_max[index]
        return <div className={`weather-hero weather-hero--${visual.tone}`}>
          <div className="weather-hero__glow" aria-hidden="true"></div>
          <div className="weather-hero__copy"><span className="weather-hero__label">TRIP DAY ONE · {displayDate(days[0].date)}</span><strong>{visual.label}</strong><p>{condition(result.forecast.daily.weather_code[index])} in {result.place.name}</p><div className="weather-hero__stats"><span><b>{result.forecast.daily.temperature_2m_max[index]}°</b> high</span><span><b>{result.forecast.daily.temperature_2m_min[index]}°</b> low</span><span><b>{rain == null ? '—' : `${rain}%`}</b> rain</span></div></div>
          <div className="weather-hero__visual" aria-hidden="true"><span>{visual.icon}</span><small>{result.forecast.daily.wind_speed_10m_max[index]} km/h wind</small></div>
        </div>
      })()}
      {days.length ? <div className="weather-cards">{days.map(({ date, index }) => {
        const visual = weatherVisual(result.forecast.daily.weather_code[index])
        const rain = result.forecast.daily.precipitation_probability_max[index]
        return <article className={`weather-card weather-card--${visual.tone}`} key={date}>
          <div className="weather-card__top"><div><strong>{displayDate(date)}</strong><small>{visual.label}</small></div><span className="weather-card__icon" aria-hidden="true">{visual.icon}</span></div>
          <p className="weather-card__condition">{condition(result.forecast.daily.weather_code[index])}</p>
          <div className="weather-card__temperature"><strong>{result.forecast.daily.temperature_2m_max[index]}°</strong><span>{result.forecast.daily.temperature_2m_min[index]}° low</span></div>
          <div className="weather-card__details"><span>💧 {rain == null ? '—' : `${rain}%`} rain</span><span>💨 {result.forecast.daily.wind_speed_10m_max[index]} km/h</span></div>
        </article>
      })}</div> : <p>The trip dates are outside the available forecast. Check again closer to departure.</p>}
      {days.length > 0 && (days[0].date > startDate || days[days.length - 1].date < endDate) && <p>Only dates within the available forecast are shown. Check again closer to the rest of your trip.</p>}
      <p>Updated while this view is open, approximately every 30 minutes. Forecasts can change and do not establish travel safety; check official weather warnings and carrier advisories.</p></div>}
  </section>
}
