import { useEffect, useMemo, useState } from 'react'
import { AirportSelect } from '../components/AirportSelect'
import { FlightOfferPicker, type FlightOffer } from '../components/FlightOfferPicker'
import { money, offerExpired } from '../services/flightOffers'
import { Button } from '../components/ui'
import { ItineraryTimeline } from '../components/ItineraryTimeline'
import RouteMap from '../RouteMap'
import { airportByCode } from '../services/airports'
import { api } from '../services/api'
import type { ItemDraft, TripDraft, TripItem, Session } from '../types'

type Traveler = { id: number; name: string; email: string }
type Choice = 'none' | 'central' | 'airport' | 'premium' | 'manual'
const hotelOptions = [
  { id: 'central', name: 'City centre business stay', detail: 'Convenient for meetings · sample accommodation', nightly: 6200 },
  { id: 'airport', name: 'Airport area stay', detail: 'Short transfer after arrival · sample accommodation', nightly: 4800 },
  { id: 'premium', name: 'Premium business stay', detail: 'Flexible workspace · sample accommodation', nightly: 9800 },
] as const
const transferOptions = [
  { id: 'sedan', name: 'Airport pickup · sedan', detail: 'Private transfer template', cost: 1500 },
  { id: 'express', name: 'Airport pickup · express', detail: 'Shared transfer template', cost: 650 },
] as const
const localToday = () => { const date = new Date(); return [date.getFullYear(), String(date.getMonth()+1).padStart(2,'0'), String(date.getDate()).padStart(2,'0')].join('-') }
const plusMinutes = (value: string, minutes: number) => new Date(new Date(value.slice(0,16) + ':00Z').getTime() + minutes * 60000).toISOString().slice(0,16)
const showTime = (value: string) => value.replace('T', ' · ').slice(0, 18)
const toItem = (offer: FlightOffer): ItemDraft => ({ kind: 'FLIGHT', title: offer.title || offer.operatingCarriers, location: offer.origin + ' → ' + offer.destination, startsAt: offer.departingAt.slice(0,16), endsAt: offer.arrivingAt.slice(0,16), flightSource: offer.source, flightOfferId: offer.id, flightAmount: offer.totalAmount, flightCurrency: offer.totalCurrency, flightExpiresAt: offer.expiresAt })

export function CreateTripPage({ session, onCancel, onSave, busy, error }: { session: Session; onCancel: () => void; onSave: (draft: TripDraft) => Promise<void>; busy: boolean; error: string }) {
  const [step, setStep] = useState(0)
  const [origin, setOrigin] = useState('')
  const [destination, setDestination] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [returnFlight, setReturnFlight] = useState(false)
  const [travelerId, setTravelerId] = useState('')
  const [guestName, setGuestName] = useState('')
  const [guestEmail, setGuestEmail] = useState('')
  const [travelers, setTravelers] = useState<Traveler[]>([])
  const [travelersError, setTravelersError] = useState('')
  const [outbound, setOutbound] = useState<FlightOffer | null>(null)
  const [inbound, setInbound] = useState<FlightOffer | null>(null)
  const [hotel, setHotel] = useState<Choice>('none')
  const [transfer, setTransfer] = useState('none')
  const [manualHotel, setManualHotel] = useState({ title: '', start: '', end: '' })
  const [manualTransfer, setManualTransfer] = useState({ title: '', start: '', end: '' })
  const [issue, setIssue] = useState('')
  useEffect(() => { if (session.role === 'TRAVELER') return; let active = true; api<Traveler[]>('/travelers').then(data => { if (active) setTravelers(data) }).catch(cause => { if (active) setTravelersError(cause instanceof Error ? cause.message : 'Could not load travelers') }); return () => { active = false } }, [session.role])
  const traveler = travelers.find(value => String(value.id) === travelerId)
  const city = airportByCode(destination)?.city || destination
  const selectedHotel = hotelOptions.find(option => option.id === hotel)
  const selectedTransfer = transferOptions.find(option => option.id === transfer)
  const nights = startDate && endDate ? Math.max(1, Math.round((new Date(endDate).getTime() - new Date(startDate).getTime()) / 86400000)) : 1
  const hotelStart = outbound ? plusMinutes(outbound.arrivingAt, selectedTransfer ? 120 : 60) : startDate + 'T15:00'
  const hotelEnd = endDate + 'T11:00'
  const totals = useMemo(() => {
    const values = new Map<string, number>()
    for (const offer of [outbound, returnFlight ? inbound : null]) if (offer) values.set(offer.totalCurrency, (values.get(offer.totalCurrency) || 0) + Number(offer.totalAmount))
    const sampleCost = (selectedHotel ? nights * selectedHotel.nightly : 0) + (selectedTransfer ? selectedTransfer.cost : 0)
    if (sampleCost) values.set('INR', (values.get('INR') || 0) + sampleCost)
    return [...values.entries()]
  }, [outbound, inbound, returnFlight, selectedHotel, selectedTransfer, nights])
  const items = useMemo(() => {
    const list: ItemDraft[] = []
    if (outbound) list.push(toItem(outbound))
    if (selectedTransfer && outbound) list.push({ kind: 'TRANSPORT', title: selectedTransfer.name + ' · sample', location: destination + ' airport → ' + city, startsAt: plusMinutes(outbound.arrivingAt, 45), endsAt: plusMinutes(outbound.arrivingAt, 45 + (selectedTransfer.id === 'express' ? 75 : 50)) })
    if (transfer === 'manual') list.push({ kind: 'TRANSPORT', title: manualTransfer.title.trim(), location: city, startsAt: manualTransfer.start, endsAt: manualTransfer.end })
    if (selectedHotel) list.push({ kind: 'HOTEL', title: selectedHotel.name + ' · sample', location: city, startsAt: hotelStart, endsAt: hotelEnd })
    if (hotel === 'manual') list.push({ kind: 'HOTEL', title: manualHotel.title.trim(), location: city, startsAt: manualHotel.start, endsAt: manualHotel.end })
    if (returnFlight && inbound) list.push(toItem(inbound))
    return list.sort((a,b) => a.startsAt.localeCompare(b.startsAt))
  }, [outbound, inbound, returnFlight, selectedTransfer, transfer, destination, city, manualTransfer, selectedHotel, hotel, hotelStart, hotelEnd, manualHotel])
  const previewItems: TripItem[] = [outbound, returnFlight ? inbound : null].filter((offer): offer is FlightOffer => Boolean(offer)).map((offer,index) => ({ id: index + 1, kind: 'FLIGHT', title: offer.title, location: offer.origin + ' → ' + offer.destination, startsAt: offer.departingAt, endsAt: offer.arrivingAt, status: 'CONFIRMED', changeNote: null, flightSource: offer.source, flightAmount: offer.totalAmount, flightCurrency: offer.totalCurrency, flightOperatingCarriers: offer.operatingCarriers, flightDurationMinutes: offer.durationMinutes, flightStops: offer.stops, flightOriginTimeZone: offer.originTimeZone, flightDestinationTimeZone: offer.destinationTimeZone, flightRouteJson: JSON.stringify(offer.legs) }))
  const conflicts = useMemo(() => {
    const warnings: string[] = []
    for (const item of items) {
      if (!item.title || !item.startsAt || !item.endsAt) warnings.push('Complete all selected accommodation and transfer details.')
      else if (item.kind !== 'FLIGHT' && item.endsAt <= item.startsAt) warnings.push(item.title + ' ends before it starts.')
      if (item.startsAt && startDate && item.startsAt.slice(0,10) < startDate || item.endsAt && endDate && item.endsAt.slice(0,10) > endDate) warnings.push((item.title || 'A segment') + ' falls outside the trip dates.')
    }
    if (selectedHotel && hotelEnd <= hotelStart) warnings.push('Hotel checkout must be after arrival. Extend the trip dates or skip the stay.')
    if (inbound && selectedHotel && inbound.departingAt.slice(0,16) < hotelEnd) warnings.push('Hotel checkout is after the return flight departs. Adjust or remove the stay.')
    if (outbound && offerExpired(outbound) || inbound && offerExpired(inbound)) warnings.push('A selected Duffel offer has expired. Search and select a current offer.')
    return [...new Set(warnings)]
  }, [items, startDate, endDate, selectedHotel, hotelStart, hotelEnd, outbound, inbound])
  function next() {
    setIssue('')
    if (step === 0) {
      if (!origin || !destination || origin === destination) return setIssue('Choose two different airports from the suggestions.')
      if (!startDate || !endDate || startDate < localToday() || endDate < startDate) return setIssue('Choose valid travel dates, starting today or later.')
      if (session.role === 'COORDINATOR' && !traveler && (!guestName.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(guestEmail))) return setIssue('Select a registered traveler or enter a guest name and valid email.')
    }
    if (step === 1) {
      if (!outbound) return setIssue('Select an outbound Duffel flight offer before continuing.')
      if (returnFlight && !inbound) return setIssue('Select a return flight offer or turn off return flight.')
      if (offerExpired(outbound) || inbound && offerExpired(inbound)) return setIssue('A selected offer expired. Search again.')
    }
    setStep(step + 1)
  }
  async function save() {
    setIssue('')
    if (conflicts.length) return setIssue('Resolve the schedule issues before saving.')
    if (!outbound || offerExpired(outbound) || returnFlight && (!inbound || offerExpired(inbound))) return setIssue('Select current flight offers before saving.')
    await onSave({ traveler: session.role === 'TRAVELER' ? session.name : traveler?.name || guestName.trim(), travelerEmail: session.role === 'TRAVELER' ? session.email : traveler?.email || guestEmail.trim(), origin: airportByCode(origin)?.city || origin, destination: city, originAirportCode: origin, destinationAirportCode: destination, startDate, endDate, items })
  }
  const steps = ['Trip details','Flights','Stay & transfer','Review']
  return <div className="create-trip"><div className="create-trip__top"><div><span className="eyebrow">TRAVEL PLANNING</span><h2>{session.role === 'TRAVELER' ? 'Request a trip' : 'Create a trip'}</h2><p>Build an itinerary from flight offers and planning options. This submits a request; it does not buy a ticket or reserve a room.</p></div><Button type="button" onClick={onCancel}>Exit planner</Button></div>
    <ol className="wizard-progress" aria-label="Trip creation progress">{steps.map((name,index) => <li className={index === step ? 'active' : index < step ? 'done' : ''} key={name}><span>{index < step ? '✓' : index + 1}</span><strong>{name}</strong></li>)}</ol>
    {(issue || error) && <div className="alert alert--error" role="alert">{issue || error}</div>}
    <div className="wizard-panel">
      {step === 0 && <><div className="wizard-panel__heading"><span className="eyebrow">01 / DETAILS</span><h3>Where are you traveling?</h3><p>Search for a city or airport, then choose it from the list. Flight availability is checked in the next step.</p></div><div className="wizard-fields"><AirportSelect label="Departure airport" value={origin} exclude={destination} onChange={code => { setOrigin(code); setOutbound(null); setInbound(null) }}/><AirportSelect label="Destination airport" value={destination} exclude={origin} onChange={code => { setDestination(code); setOutbound(null); setInbound(null) }}/><label>Departure date<input type="date" min={localToday()} value={startDate} onChange={e => { setStartDate(e.target.value); setOutbound(null); setInbound(null) }}/></label><label>Trip end date<input type="date" min={startDate && startDate > localToday() ? startDate : localToday()} value={endDate} onChange={e => { setEndDate(e.target.value); setInbound(null) }}/></label></div><label className="wizard-check"><input type="checkbox" checked={returnFlight} onChange={e => { setReturnFlight(e.target.checked); setInbound(null) }}/> Include a return flight on the trip end date</label>{session.role === 'COORDINATOR' && <><div className="wizard-divider"/><div className="wizard-panel__heading"><h3>Assign traveler</h3><p>Select an existing traveler account, or enter a guest traveler who can register later with the same email.</p></div>{travelersError && <div className="alert alert--error" role="alert">{travelersError}</div>}<label className="wizard-traveler">Registered traveler<select value={travelerId} onChange={e => setTravelerId(e.target.value)}><option value="">Enter guest traveler details</option>{travelers.map(person => <option key={person.id} value={person.id}>{person.name} · {person.email}</option>)}</select></label>{!travelerId && <div className="wizard-fields"><label>Traveler name<input maxLength={120} autoComplete="name" value={guestName} onChange={e => setGuestName(e.target.value)}/></label><label>Traveler email<input type="email" autoComplete="email" value={guestEmail} onChange={e => setGuestEmail(e.target.value)}/></label></div>}</>}</>}
      {step === 1 && <><div className="wizard-panel__heading"><span className="eyebrow">02 / FLIGHTS</span><h3>Choose the flights</h3><p>These results come from Duffel. With a test token, offers and prices are simulated by its test environment.</p></div><FlightOfferPicker key={origin + destination + startDate} origin={origin} destination={destination} date={startDate} selected={outbound} onSelect={setOutbound}/>{returnFlight && <FlightOfferPicker key={destination + origin + endDate} origin={destination} destination={origin} date={endDate} selected={inbound} onSelect={setInbound}/>}
      {previewItems.length > 0 && <div className="selected-flight-preview"><div><span className="eyebrow">SELECTED ITINERARY</span><h3>Flight timeline</h3><ItineraryTimeline items={previewItems} role="TRAVELER" cancelled={false} busy={false}/></div><RouteMap items={previewItems}/></div>}</>}
      {step === 2 && <><div className="wizard-panel__heading"><span className="eyebrow">03 / GROUND PLAN</span><h3>Complete the journey</h3><p>These are seeded planning templates with estimated costs. They are separate from Duffel offers and do not reserve a room or vehicle.</p></div><h4>Accommodation in {city}</h4><div className="choice-grid"><button type="button" className={hotel === 'none' ? 'choice-card selected' : 'choice-card'} onClick={() => setHotel('none')}><strong>No accommodation</strong><small>Skip this step</small></button>{hotelOptions.map(option => <button type="button" key={option.id} className={hotel === option.id ? 'choice-card selected' : 'choice-card'} onClick={() => setHotel(option.id)}><strong>{option.name}</strong><small>{option.detail}</small><b>{money(String(option.nightly), 'INR')} / night est.</b></button>)}<button type="button" className={hotel === 'manual' ? 'choice-card selected' : 'choice-card'} onClick={() => setHotel('manual')}><strong>Enter accommodation manually</strong><small>Use an existing reservation</small></button></div>{hotel === 'manual' && <div className="wizard-fields"><label>Accommodation name<input value={manualHotel.title} onChange={e => setManualHotel({ ...manualHotel, title: e.target.value })}/></label><label>Check-in<input type="datetime-local" value={manualHotel.start} onChange={e => setManualHotel({ ...manualHotel, start: e.target.value })}/></label><label>Check-out<input type="datetime-local" value={manualHotel.end} onChange={e => setManualHotel({ ...manualHotel, end: e.target.value })}/></label></div>}<div className="wizard-divider"/><h4>Arrival transfer</h4><div className="choice-grid"><button type="button" className={transfer === 'none' ? 'choice-card selected' : 'choice-card'} onClick={() => setTransfer('none')}><strong>No transfer</strong><small>Skip this step</small></button>{transferOptions.map(option => <button type="button" key={option.id} className={transfer === option.id ? 'choice-card selected' : 'choice-card'} onClick={() => setTransfer(option.id)}><strong>{option.name}</strong><small>{option.detail}</small><b>{money(String(option.cost), 'INR')} est.</b></button>)}<button type="button" className={transfer === 'manual' ? 'choice-card selected' : 'choice-card'} onClick={() => setTransfer('manual')}><strong>Enter transfer manually</strong><small>Use an existing arrangement</small></button></div>{transfer === 'manual' && <div className="wizard-fields"><label>Transfer name<input value={manualTransfer.title} onChange={e => setManualTransfer({ ...manualTransfer, title: e.target.value })}/></label><label>Pickup<input type="datetime-local" value={manualTransfer.start} onChange={e => setManualTransfer({ ...manualTransfer, start: e.target.value })}/></label><label>Drop-off<input type="datetime-local" value={manualTransfer.end} onChange={e => setManualTransfer({ ...manualTransfer, end: e.target.value })}/></label></div>}</>}
      {step === 3 && <><div className="wizard-panel__heading"><span className="eyebrow">04 / REVIEW</span><h3>Review before saving</h3><p>{airportByCode(origin)?.city} ({origin}) → {city} ({destination}) · {startDate} – {endDate} · {session.role === 'TRAVELER' ? session.name : traveler?.name || guestName}</p></div>{conflicts.length > 0 && <div className="alert alert--error" role="alert"><strong>Schedule needs attention</strong><ul>{conflicts.map(warning => <li key={warning}>{warning}</li>)}</ul></div>}<div className="review-grid"><div><h4>Itinerary</h4><ol className="review-timeline">{items.map((item,index) => <li key={index}><span>{item.kind}</span><strong>{item.title || 'Incomplete segment'}</strong><small>{item.location} · {showTime(item.startsAt)} → {showTime(item.endsAt)}</small>{item.flightSource && <em>{item.flightSource === 'DUFFEL_TEST' ? 'Duffel test offer' : 'Duffel live offer'} · {item.flightCurrency} {item.flightAmount}</em>}</li>)}</ol></div><aside className="review-cost"><h4>Estimated costs</h4>{outbound && <p>Outbound flight <strong>{money(outbound.totalAmount, outbound.totalCurrency)}</strong></p>}{returnFlight && inbound && <p>Return flight <strong>{money(inbound.totalAmount, inbound.totalCurrency)}</strong></p>}{selectedHotel && <p>Sample stay · {nights} night(s) <strong>{money(String(nights * selectedHotel.nightly), 'INR')}</strong></p>}{selectedTransfer && <p>Sample transfer <strong>{money(String(selectedTransfer.cost), 'INR')}</strong></p>}<div className="review-cost__totals">{totals.map(([currency, amount]) => <p key={currency}><strong>Estimated total · {currency}</strong><strong>{money(String(amount), currency)}</strong></p>)}</div><div className="review-cost__note">Currencies are shown separately; no exchange rate is assumed. Hotel and transfer amounts are planning estimates. Saving does not book or pay for anything.</div></aside></div></>}
    </div><div className="wizard-footer"><Button type="button" onClick={step === 0 ? onCancel : () => { setIssue(''); setStep(step - 1) }}>{step === 0 ? 'Cancel' : 'Back'}</Button><span>Step {step + 1} of {steps.length}</span>{step < 3 ? <Button type="button" variant="primary" onClick={next}>Continue →</Button> : <Button type="button" variant="primary" disabled={busy || conflicts.length > 0} onClick={() => void save()}>{busy ? 'Saving trip…' : session.role === 'TRAVELER' ? 'Submit booking request' : 'Save trip'}</Button>}</div>
  </div>
}
