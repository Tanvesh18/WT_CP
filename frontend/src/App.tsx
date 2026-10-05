import { useEffect, useState, type FormEvent } from 'react'
import './App.css'

type Kind = 'FLIGHT' | 'HOTEL' | 'TRANSPORT'
type Item = { id: number; kind: Kind; title: string; location: string; startsAt: string; endsAt: string; status: string; changeNote: string | null }
type Trip = { id: number; traveler: string; origin: string; destination: string; startDate: string; endDate: string; status: string; items: Item[] }
type Alternative = { id: number; kind: Kind; title: string; location: string; delayMinutes: number; estimatedCost: number; description: string }
type Impact = { message: string; affectedItem: Item; alternatives: Alternative[] }
type ItemDraft = { kind: Kind; title: string; location: string; startsAt: string; endsAt: string }
const blank = (): ItemDraft => ({ kind: 'FLIGHT', title: '', location: '', startsAt: '', endsAt: '' })
const disruption: Record<Kind, string> = { FLIGHT: 'FLIGHT_CANCELLATION', HOTEL: 'HOTEL_UNAVAILABLE', TRANSPORT: 'TRANSPORT_DISRUPTION' }

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, { ...init, headers: { 'Content-Type': 'application/json', ...init?.headers } })
  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    throw new Error(data.detail || data.message || `Request failed (${response.status})`)
  }
  return response.json()
}

function App() {
  const [trips, setTrips] = useState<Trip[]>([])
  const [selectedId, select] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [traveler, setTraveler] = useState('')
  const [origin, setOrigin] = useState('')
  const [destination, setDestination] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [items, setItems] = useState<ItemDraft[]>([blank()])
  const [impact, setImpact] = useState<Impact | null>(null)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const selected = trips.find(t => t.id === selectedId)
  const attention = trips.filter(t => t.status === 'NEEDS_ATTENTION').length

  async function refresh(pick?: number) {
    const data = await api<Trip[]>('/trips')
    setTrips(data)
    if (pick !== undefined) select(pick)
    else select(current => current ?? data[0]?.id ?? null)
  }
  useEffect(() => {
    api<Trip[]>('/trips').then(data => {
      setTrips(data)
      select(data[0]?.id ?? null)
    }).catch(e => setError(e.message))
  }, [])
  async function run(action: () => Promise<void>) {
    setBusy(true); setError(''); setNotice('')
    try { await action() } catch (e) { setError(e instanceof Error ? e.message : 'Something went wrong') }
    finally { setBusy(false) }
  }
  function update(index: number, value: Partial<ItemDraft>) { setItems(current => current.map((item, i) => i === index ? { ...item, ...value } : item)) }
  function create(e: FormEvent) {
    e.preventDefault()
    run(async () => {
      const trip = await api<Trip>('/trips', { method: 'POST', body: JSON.stringify({ traveler, origin, destination, startDate, endDate, items }) })
      await refresh(trip.id); setCreating(false); setItems([blank()]); setTraveler(''); setOrigin(''); setDestination(''); setStartDate(''); setEndDate('')
      setNotice('Trip created successfully.')
    })
  }
  function simulate(item: Item, type = disruption[item.kind]) {
    if (!selected) return
    run(async () => {
      const result = await api<Impact>(`/trips/${selected.id}/disruptions`, { method: 'POST', body: JSON.stringify({ type, itemId: item.id }) })
      setImpact(result); await refresh(selected.id); setNotice(result.message)
    })
  }
  function options(item: Item) {
    if (selected) run(async () => setImpact(await api<Impact>(`/trips/${selected.id}/items/${item.id}/alternatives`)))
  }
  function apply(option: Alternative) {
    if (!selected || !impact) return
    run(async () => {
      await api<Trip>(`/trips/${selected.id}/items/${impact.affectedItem.id}/alternatives/${option.id}/apply`, { method: 'POST' })
      await refresh(selected.id); setImpact(null)
      setNotice(`Itinerary updated: ${option.title} replaces ${impact.affectedItem.title}.`)
    })
  }
  return <div className="app">
    <header><div className="brand"><span>✦</span> TripShield</div><small>Corporate travel operations</small></header>
    <main>
      <div className="heading"><div><label className="eyebrow">TRAVEL CONTROL CENTER</label><h1>Stay ahead of every trip.</h1><p>Plan travel, spot disruptions, and keep everyone moving.</p></div><button className="primary" onClick={() => setCreating(true)}>+ New trip</button></div>
      {error && <div className="notice error" role="alert">{error}</div>}{notice && <div className="notice success" role="status">{notice}</div>}
      <section className="stats"><div><small>Total trips</small><strong>{trips.length}</strong></div><div><small>Need attention</small><strong className="warn">{attention}</strong></div><div><small>On track</small><strong>{trips.length - attention}</strong></div></section>
      <div className="workspace"><aside className="panel"><h2>Trips <small>{trips.length}</small></h2>{trips.length === 0 && <p className="muted">Create your first trip to get started.</p>}{trips.map(t => <button className={`trip ${selectedId === t.id ? 'selected' : ''}`} key={t.id} onClick={() => { select(t.id); setImpact(null) }}><b>{t.origin} → {t.destination}</b><small>{t.traveler} · {t.startDate}</small><span className={`badge ${t.status === 'NEEDS_ATTENTION' ? 'bad' : 'good'}`}>{t.status === 'NEEDS_ATTENTION' ? 'Needs attention' : 'On track'}</span></button>)}</aside>
      <section className="panel detail">{!selected ? <div className="empty"><span>✈</span><h2>Your trips will appear here</h2><p>Create a trip with a flight, hotel, or transport booking to try disruption recovery.</p></div> : <><div className="detail-head"><div><label className="eyebrow">TRIP #{selected.id}</label><h2>{selected.origin} → {selected.destination}</h2><p>{selected.traveler} · {selected.startDate} to {selected.endDate}</p></div><span className={`badge ${selected.status === 'NEEDS_ATTENTION' ? 'bad' : 'good'}`}>{selected.status === 'NEEDS_ATTENTION' ? 'Needs attention' : 'On track'}</span></div><h3 className="timeline-title">Itinerary timeline</h3><div className="timeline">{selected.items.map(item => <article className={`event ${item.status.toLowerCase()}`} key={item.id}><div className="event-body"><div className="event-top"><label className="eyebrow">{item.kind}</label><small>{new Date(item.startsAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}</small></div><h4>{item.title}</h4><p>{item.location}</p>{item.changeNote && <p className="change">{item.changeNote}</p>}{item.status === 'CONFIRMED' && <><button disabled={busy} onClick={() => simulate(item)}>Simulate {item.kind === 'FLIGHT' ? 'cancellation' : item.kind === 'HOTEL' ? 'hotel unavailability' : 'transport issue'}</button><button disabled={busy} onClick={() => simulate(item, 'SEVERE_WEATHER')}>Simulate severe weather</button></>}{item.status === 'AFFECTED' && <button disabled={busy} onClick={() => options(item)}>Review alternatives →</button>}{item.status === 'REPLACED' && <small>Replaced</small>}</div></article>)}</div></>}</section></div>
    </main>
    {creating && <div className="overlay" onMouseDown={e => { if (e.target === e.currentTarget) setCreating(false) }}><div className="modal"><div className="modal-head"><div><label className="eyebrow">NEW ITINERARY</label><h2>Create a trip</h2></div><button className="close" onClick={() => setCreating(false)}>×</button></div><form onSubmit={create}><div className="grid"><label>Traveler<input required value={traveler} onChange={e => setTraveler(e.target.value)} placeholder="Employee name" /></label><label>Origin<input required value={origin} onChange={e => setOrigin(e.target.value)} placeholder="Pune" /></label><label>Destination<input required value={destination} onChange={e => setDestination(e.target.value)} placeholder="Delhi" /></label><label>Start date<input required type="date" value={startDate} onChange={e => setStartDate(e.target.value)} /></label><label>End date<input required type="date" min={startDate} value={endDate} onChange={e => setEndDate(e.target.value)} /></label></div><div className="form-title"><h3>Itinerary items</h3><button type="button" onClick={() => setItems([...items, blank()])}>+ Add item</button></div>{items.map((item, index) => <div className="item-form" key={index}><div className="form-title"><b>Item {index + 1}</b>{items.length > 1 && <button type="button" onClick={() => setItems(items.filter((_, i) => i !== index))}>Remove</button>}</div><div className="grid"><label>Type<select value={item.kind} onChange={e => update(index, { kind: e.target.value as Kind })}><option value="FLIGHT">Flight</option><option value="HOTEL">Hotel</option><option value="TRANSPORT">Transport</option></select></label><label>Title<input required value={item.title} onChange={e => update(index, { title: e.target.value })} placeholder="Flight AI 401" /></label><label>Location<input required value={item.location} onChange={e => update(index, { location: e.target.value })} placeholder="Airport / city" /></label><label>Starts<input required type="datetime-local" value={item.startsAt} onChange={e => update(index, { startsAt: e.target.value })} /></label><label>Ends<input required type="datetime-local" min={item.startsAt} value={item.endsAt} onChange={e => update(index, { endsAt: e.target.value })} /></label></div></div>)}<div className="actions"><button type="button" onClick={() => setCreating(false)}>Cancel</button><button className="primary" disabled={busy} type="submit">{busy ? 'Saving…' : 'Create trip'}</button></div></form></div></div>}
    {impact && <div className="overlay" onMouseDown={e => { if (e.target === e.currentTarget) setImpact(null) }}><div className="modal"><div className="modal-head"><div><label className="eyebrow">DISRUPTION RECOVERY</label><h2>Choose an alternative</h2><p>{impact.message}</p></div><button className="close" onClick={() => setImpact(null)}>×</button></div>{impact.alternatives.length ? impact.alternatives.map(option => <div className="option" key={option.id}><div><h3>{option.title}</h3><p>{option.description} · {option.location}</p><small>Delay: {option.delayMinutes} min · Estimated cost: ₹{option.estimatedCost.toLocaleString()}</small></div><button className="primary" disabled={busy} onClick={() => apply(option)}>Select</button></div>) : <p>No seeded alternatives available.</p>}</div></div>}
  </div>
}
export default App
