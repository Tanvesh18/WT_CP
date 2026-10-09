import { useMemo, useState } from 'react'
import { api } from '../services/api'
import { Button } from './ui'
import { money, offerExpired } from '../services/flightOffers'

export type FlightLeg = { flightNumber: string; airline: string; origin: string; destination: string; departingAt: string; arrivingAt: string; originTimeZone: string; destinationTimeZone: string; originName?: string; destinationName?: string; originLatitude?: number | null; originLongitude?: number | null; destinationLatitude?: number | null; destinationLongitude?: number | null; technicalStops?: { code: string; name: string; latitude: number | null; longitude: number | null }[] }
export type FlightOffer = { id: string; source: 'DUFFEL_TEST' | 'DUFFEL_LIVE'; title: string; origin: string; destination: string; departingAt: string; arrivingAt: string; originTimeZone: string; destinationTimeZone: string; totalAmount: string; totalCurrency: string; expiresAt: string; stops: number; durationMinutes: number; operatingCarriers: string; legs: FlightLeg[] }
type SearchResult = { mode: 'TEST' | 'LIVE'; offers: FlightOffer[] }
const clock = (value: string) => value?.slice(11, 16) || '—'
const duration = (minutes: number) => minutes > 0 ? Math.floor(minutes / 60) + 'h ' + minutes % 60 + 'm' : 'Duration unavailable'

export function FlightOfferPicker({ origin, destination, date, selected, onSelect }: { origin: string; destination: string; date: string; selected: FlightOffer | null; onSelect: (offer: FlightOffer) => void }) {
  const [result, setResult] = useState<SearchResult | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [sort, setSort] = useState<'price' | 'duration' | 'departure'>('price')
  const [nonstop, setNonstop] = useState(false)
  const [carrier, setCarrier] = useState('all')
  const [expanded, setExpanded] = useState<string | null>(null)
  const carriers = useMemo(() => [...new Set(result?.offers.map(offer => offer.operatingCarriers).filter(Boolean) || [])].sort(), [result])
  const visible = useMemo(() => (result?.offers || []).filter(offer => (!nonstop || offer.stops === 0) && (carrier === 'all' || offer.operatingCarriers === carrier)).sort((a,b) => sort === 'duration' ? a.durationMinutes - b.durationMinutes : sort === 'departure' ? a.departingAt.localeCompare(b.departingAt) : Number(a.totalAmount) - Number(b.totalAmount)), [result, nonstop, carrier, sort])
  async function search() {
    setBusy(true); setError(''); setResult(null); setCarrier('all'); setNonstop(false)
    try { setResult(await api<SearchResult>('/flights/search', { method: 'POST', body: JSON.stringify({ origin, destination, departureDate: date }) })) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Flight search failed.') }
    finally { setBusy(false) }
  }
  return <section className="flight-picker" aria-label={'Flights from ' + origin + ' to ' + destination}>
    <div className="flight-picker__head"><div><span className="eyebrow">DUFFEL FLIGHT OFFERS</span><h3>{origin} <span aria-hidden="true">→</span> {destination}</h3><p>{date} · 1 adult · Economy</p></div><Button type="button" variant="primary" onClick={search} disabled={busy || !origin || !destination || !date}>{busy ? 'Searching flights…' : result ? 'Refresh flights' : 'Search flights'}</Button></div>
    {busy && <div className="flight-picker__loading" role="status"><span className="loading-bar"/>Checking airline offers. This can take a few moments…</div>}
    {error && <div className="alert alert--error" role="alert">{error} <button type="button" onClick={search}>Try again</button></div>}
    {result && <><p className="flight-picker__source">{result.mode === 'TEST' ? 'Duffel test environment · schedules and prices are simulated' : 'Duffel live offers · availability and prices can change'} · Selection plans an itinerary; it does not book a ticket.</p>
      {result.offers.length > 0 && <div className="flight-picker__tools"><label>Sort by<select value={sort} onChange={e => setSort(e.target.value as typeof sort)}><option value="price">Lowest price</option><option value="duration">Shortest journey</option><option value="departure">Earliest departure</option></select></label><label>Airline<select value={carrier} onChange={e => setCarrier(e.target.value)}><option value="all">All airlines</option>{carriers.map(name => <option key={name}>{name}</option>)}</select></label><label className="flight-picker__check"><input type="checkbox" checked={nonstop} onChange={e => setNonstop(e.target.checked)}/> Nonstop only</label></div>}
      {result.offers.length === 0 ? <div className="flight-picker__empty"><strong>No flights returned for this route and date</strong><p>Try a different date or airport. TripShield does not generate substitute results.</p></div> : visible.length === 0 ? <div className="flight-picker__empty">No offers match these filters. Clear the airline or nonstop filter.</div> : <div className="flight-picker__results">{visible.map(offer => <article className={'offer-card' + (selected?.id === offer.id ? ' offer-card--selected' : '')} key={offer.id}>
        <div className="offer-card__main"><div className="offer-card__airline"><strong>{offer.operatingCarriers || 'Airline not provided'}</strong><small>{offer.title || offer.legs.map(leg => leg.flightNumber).join(' · ')}</small></div><div className="offer-card__schedule"><div><strong>{clock(offer.departingAt)}</strong><small>{offer.origin}</small></div><div className="offer-card__journey"><span>{duration(offer.durationMinutes)}</span><i/><span>{offer.stops === 0 ? 'Nonstop' : offer.stops + (offer.stops === 1 ? ' stop' : ' stops')}</span></div><div><strong>{clock(offer.arrivingAt)}</strong><small>{offer.destination}{offer.arrivingAt.slice(0,10) !== offer.departingAt.slice(0,10) ? ' · next day' : ''}</small></div></div><div className="offer-card__price"><strong>{money(offer.totalAmount, offer.totalCurrency)}</strong><small>1 traveler</small><Button type="button" variant={selected?.id === offer.id ? 'quiet' : 'primary'} disabled={offerExpired(offer)} onClick={() => onSelect(offer)}>{offerExpired(offer) ? 'Expired' : selected?.id === offer.id ? 'Selected ✓' : 'Select flight'}</Button></div></div>
        <button type="button" className="offer-card__details" aria-expanded={expanded === offer.id} onClick={() => setExpanded(expanded === offer.id ? null : offer.id)}>{expanded === offer.id ? 'Hide' : 'Show'} flight details</button>
        {expanded === offer.id && <div className="offer-card__legs">{offer.legs.map((leg,index) => <div key={index}><strong>{leg.airline} · {leg.flightNumber}</strong><span>{leg.origin} {clock(leg.departingAt)} → {leg.destination} {clock(leg.arrivingAt)}</span><small>{leg.originTimeZone} → {leg.destinationTimeZone}</small></div>)}<small>Offer expires {new Date(offer.expiresAt).toLocaleString()}</small></div>}
      </article>)}</div>}
    </>}
  </section>
}
