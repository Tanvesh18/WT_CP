import { useState } from 'react'
import { api } from '../services/api'
import { Button } from './ui'

export type FlightOffer = { id: string; source: string; title: string; origin: string; destination: string; departingAt: string; arrivingAt: string; totalAmount: string; totalCurrency: string; expiresAt: string; stops: number; operatingCarriers: string }
type Result = { mode: 'TEST' | 'LIVE'; offers: FlightOffer[] }

export function FlightSearch({ date, onSelect }: { date: string; onSelect: (offer: FlightOffer) => void }) {
  const [origin, setOrigin] = useState('')
  const [destination, setDestination] = useState('')
  const [departureDate, setDepartureDate] = useState(date)
  const [result, setResult] = useState<Result | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function search() {
    setError(''); setResult(null)
    if (origin === destination) { setError('Choose different airports.'); return }
    setBusy(true)
    try { setResult(await api<Result>('/flights/search', { method: 'POST', body: JSON.stringify({ origin, destination, departureDate }) })) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Flight search failed.') }
    finally { setBusy(false) }
  }
  return <div className="flight-search">
    <div className="form-grid">
      <label>From airport (IATA)<input required pattern="[A-Za-z]{3}" maxLength={3} placeholder="PNQ" value={origin} onChange={e => setOrigin(e.target.value.toUpperCase())} /></label>
      <label>To airport (IATA)<input required pattern="[A-Za-z]{3}" maxLength={3} placeholder="DEL" value={destination} onChange={e => setDestination(e.target.value.toUpperCase())} /></label>
      <label>Departure date<input required type="date" min={new Date().toISOString().slice(0, 10)} value={departureDate} onChange={e => setDepartureDate(e.target.value)} /></label>
      <Button type="button" variant="primary" disabled={busy || !/^[A-Z]{3}$/.test(origin) || !/^[A-Z]{3}$/.test(destination) || !departureDate} onClick={search}>{busy ? 'Searching…' : 'Search Duffel flights'}</Button>
    </div>
    <p className="form-help">Searches one adult in economy. Airport codes are three letters, such as PNQ and DEL. Results are offers, not bookings.</p>
    {error && <div className="alert alert--error" role="alert">{error}</div>}
    {result && <div aria-live="polite">
      <p className="form-help">{result.mode === 'TEST' ? 'Duffel test mode: these flights and prices are simulated.' : 'Duffel live offers. Price and availability can change before booking.'}</p>
      {result.offers.length === 0 && <p>No offers returned for this route and date.</p>}
      <div className="flight-search__results">{result.offers.map(offer => <div className="flight-search__offer" key={offer.id}>
        <div><strong>{offer.title || offer.operatingCarriers || 'Flight'}</strong> · {offer.origin} → {offer.destination}<br /><small>{offer.operatingCarriers} · {offer.stops === 0 ? 'Nonstop' : offer.stops + ' stop(s)'}</small><br /><small>{offer.departingAt} → {offer.arrivingAt}</small></div>
        <div><strong>{offer.totalCurrency} {offer.totalAmount}</strong><br /><Button type="button" onClick={() => onSelect(offer)}>Add to itinerary</Button></div>
      </div>)}</div>
    </div>}
  </div>
}
