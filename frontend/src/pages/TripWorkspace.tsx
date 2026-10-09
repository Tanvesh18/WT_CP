import { useEffect, useMemo, useState } from 'react'
import RouteMap from '../RouteMap'
import { formatDate, formatTime } from '../components/format'
import { ItineraryTimeline } from '../components/ItineraryTimeline'
import { RecoveryDialog } from '../components/RecoveryDialog'
import { TripFilters } from '../components/TripFilters'
import { Button, EmptyState, StatusBadge } from '../components/ui'
import { api } from '../services/api'
import { emptyCriteria, filterTrips, type TripCriteria } from '../services/tripFilters'
import type { Alternative, Impact, Session, Trip, TripEvent, TripItem } from '../types'

const disruption = { FLIGHT: 'FLIGHT_CANCELLATION', HOTEL: 'HOTEL_UNAVAILABLE', TRANSPORT: 'TRANSPORT_DISRUPTION' }
type Props = { session: Session; trips: Trip[]; selectedId: number | null; onSelect: (id: number) => void; onRefresh: (id?: number) => Promise<void>; onEdit: (trip: Trip) => void }
export function TripWorkspace({ session, trips, selectedId, onSelect, onRefresh, onEdit }: Props) {
  const [criteria, setCriteria] = useState<TripCriteria>(emptyCriteria)
  const [view, setView] = useState<'timeline' | 'map'>('timeline')
  const [history, setHistory] = useState<TripEvent[]>([])
  const [historyError, setHistoryError] = useState('')
  const [impact, setImpact] = useState<Impact | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const selected = trips.find(trip => trip.id === selectedId) || null
  const filtered = useMemo(() => filterTrips(trips, criteria), [trips, criteria])
  useEffect(() => {
    if (selectedId === null) return
    let active = true
    api<TripEvent[]>('/trips/' + selectedId + '/history').then(data => { if (active) { setHistory(data); setHistoryError('') } }).catch(cause => { if (active) setHistoryError(cause instanceof Error ? cause.message : 'Could not load history') })
    return () => { active = false }
  }, [selectedId, trips])
  async function run(action: () => Promise<void>) {
    setBusy(true); setError(''); setNotice('')
    try { await action() } catch (cause) { setError(cause instanceof Error ? cause.message : 'Action failed') }
    finally { setBusy(false) }
  }
  function simulate(item: TripItem, weather = false) {
    if (!selected) return
    void run(async () => {
      const result = await api<Impact>('/trips/' + selected.id + '/disruptions', { method: 'POST', body: JSON.stringify({ type: weather ? 'SEVERE_WEATHER' : disruption[item.kind], itemId: item.id }) })
      setImpact(result); await onRefresh(selected.id); setNotice(result.message)
    })
  }
  function alternatives(item: TripItem) {
    if (!selected) return
    void run(async () => setImpact(await api<Impact>('/trips/' + selected.id + '/items/' + item.id + '/alternatives')))
  }
  function apply(option: Alternative) {
    if (!selected || !impact) return
    void run(async () => {
      await api('/trips/' + selected.id + '/items/' + impact.affectedItem.id + '/alternatives/' + option.id + '/apply', { method: 'POST' })
      await onRefresh(selected.id); setImpact(null); setNotice('Recovery option applied.')
    })
  }
  function checkIn(status: 'SAFE' | 'NEEDS_HELP') {
    if (!selected) return
    void run(async () => {
      await api('/trips/' + selected.id + '/check-in', { method: 'POST', body: JSON.stringify({ status, note }) })
      await onRefresh(selected.id); setNote(''); setNotice(status === 'SAFE' ? 'Check-in recorded.' : 'Help request recorded for your coordinator.')
    })
  }
  function cancel() {
    if (!selected || !window.confirm('Cancel trip #' + selected.id + '?')) return
    void run(async () => { await api('/trips/' + selected.id + '/cancel', { method: 'POST' }); await onRefresh(selected.id); setNotice('Trip cancelled.') })
  }
  return <div className="workspace-page">
    <div className="page-intro"><div><span className="eyebrow">JOURNEY MANAGEMENT</span><h2>{session.role === 'COORDINATOR' ? 'All trips' : 'My trips'}</h2><p>Review itineraries, changes, and activity in one place.</p></div></div>
    {error && <div className="alert alert--error" role="alert">{error}</div>}
    {notice && <div className="alert alert--success" role="status">{notice}</div>}
    <div className="workspace">
      <aside className="surface trip-list" aria-label="Trip list">
        <div className="section-head"><h3>Trips</h3><span className="count-pill">{trips.length}</span></div>
        <TripFilters criteria={criteria} onChange={setCriteria} total={trips.length} shown={filtered.length}/>
        {filtered.length ? <div className="trip-list__rows">{filtered.map(trip => <button className={'trip-list__item' + (selectedId === trip.id ? ' selected' : '')} key={trip.id} aria-current={selectedId === trip.id ? 'true' : undefined} onClick={() => { onSelect(trip.id); setImpact(null); setView('timeline') }}><strong>{trip.origin} <span aria-hidden="true">→</span> {trip.destination}</strong><small>{trip.traveler} · {formatDate(trip.startDate)}</small><div className="trip-list__badges"><StatusBadge status={trip.status}/><span className={'risk-chip risk-chip--' + trip.riskLevel.toLowerCase()}>{trip.riskLevel.toLowerCase()} risk</span></div></button>)}</div> : <EmptyState title="No matching trips">Try changing or clearing the filters.</EmptyState>}
      </aside>
      <section className="surface trip-detail" aria-label="Trip details">
        {selected ? <>
          <div className="trip-detail__head"><div><span className="eyebrow">TRIP #{selected.id}</span><h2>{selected.origin} <span aria-hidden="true">→</span> {selected.destination}</h2><p>{selected.traveler} · {formatDate(selected.startDate)} – {formatDate(selected.endDate)}</p></div><StatusBadge status={selected.status}/></div>
          {session.role === 'COORDINATOR' && selected.status !== 'CANCELLED' && <div className="trip-detail__actions"><Button onClick={() => onEdit(selected)}>Edit trip</Button><Button variant="danger" onClick={cancel} disabled={busy}>Cancel trip</Button></div>}
          <div className="risk-note"><span className={'risk-dot risk-dot--' + selected.riskLevel.toLowerCase()}></span><strong>{selected.riskLevel.toLowerCase()} risk</strong><span>{selected.riskReason}</span></div>
          <div className="tabs" role="tablist" aria-label="Itinerary view"><button role="tab" aria-selected={view === 'timeline'} className={view === 'timeline' ? 'active' : ''} onClick={() => setView('timeline')}>Timeline</button><button role="tab" aria-selected={view === 'map'} className={view === 'map' ? 'active' : ''} onClick={() => setView('map')}>Route map</button></div>
          {view === 'map' ? <RouteMap origin={selected.origin} destination={selected.destination} locations={selected.items.filter(item => item.status !== 'REPLACED').map(item => item.location)} /> : <ItineraryTimeline items={selected.items} role={session.role} cancelled={selected.status === 'CANCELLED'} busy={busy} onSimulate={simulate} onOptions={alternatives}/>}
          <section className="detail-section"><div className="section-head"><div><span className="eyebrow">SAFETY</span><h3>Traveler check-in</h3></div></div><p>Record your status for the travel team. A help request is saved in this app.</p><div className="checkin-status">Current: <strong>{selected.checkInStatus.replaceAll('_', ' ')}</strong>{selected.checkedInAt && <span> · {formatTime(selected.checkedInAt)}</span>}{selected.checkInNote && <span> · {selected.checkInNote}</span>}</div><label className="sr-only" htmlFor="checkin-note">Check-in note</label><input id="checkin-note" maxLength={500} placeholder="Optional note for your coordinator" value={note} onChange={e => setNote(e.target.value)}/><div className="checkin-actions"><Button disabled={busy || selected.status === 'CANCELLED'} onClick={() => checkIn('SAFE')}>Mark safe</Button><Button variant="primary" disabled={busy || selected.status === 'CANCELLED'} onClick={() => checkIn('NEEDS_HELP')}>Request help</Button></div></section>
          <section className="detail-section"><div className="section-head"><div><span className="eyebrow">AUDIT TRAIL</span><h3>Activity history</h3></div></div>{historyError ? <div className="alert alert--error">{historyError}</div> : history.length ? <ol className="history">{history.map(event => <li key={event.id}><time>{formatTime(event.occurredAt)}</time><strong>{event.type.replaceAll('_', ' ')}</strong><p>{event.details}</p></li>)}</ol> : <p className="muted">No activity recorded yet.</p>}</section>
        </> : <EmptyState title="Select a trip">Choose a trip to see its itinerary and activity.</EmptyState>}
      </section>
    </div>
    {impact && <RecoveryDialog impact={impact} busy={busy} error={error} onClose={() => setImpact(null)} onApply={apply}/>}
  </div>
}
