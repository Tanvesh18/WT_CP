import { useEffect, useMemo, useState } from 'react'
import type { Alternative, Impact } from '../types'
import { money } from '../services/flightOffers'
import { formatTime } from './format'
import { Button, EmptyState, Modal } from './ui'

type Order = 'PRACTICAL' | 'ARRIVAL' | 'COST'
const timing = (minutes: number | null | undefined) => minutes == null ? 'Unknown' : minutes < 0 ? Math.abs(minutes) + ' min earlier' : minutes === 0 ? 'On time' : minutes + ' min later'
export function RecoveryDialog({ impact, busy, error, onClose, onApply, onRefresh, canManage }: { impact: Impact; busy: boolean; error: string; onClose: () => void; onApply: (option: Alternative) => void; onRefresh: () => void; canManage: boolean }) {
  const [order, setOrder] = useState<Order>('PRACTICAL')
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 30000); return () => window.clearInterval(timer) }, [])
  const duffelFlight = impact.affectedItem.kind === 'FLIGHT' && Boolean(impact.affectedItem.flightSource) && impact.affectedItem.status === 'AFFECTED'
  const options = useMemo(() => [...impact.alternatives].sort((a, b) => {
    if (order === 'COST') return (a.currency || 'INR').localeCompare(b.currency || 'INR') || a.estimatedCost - b.estimatedCost
    if (order === 'ARRIVAL') return (a.arrivalDelayMinutes ?? a.delayMinutes) - (b.arrivalDelayMinutes ?? b.delayMinutes) || a.impactedSegments.length - b.impactedSegments.length
    return a.impactedSegments.length - b.impactedSegments.length || (a.arrivalDelayMinutes ?? a.delayMinutes) - (b.arrivalDelayMinutes ?? b.delayMinutes) || a.estimatedCost - b.estimatedCost
  }), [impact.alternatives, order])
  return <Modal title="Review recovery options" onClose={onClose}>
    <p className="modal__intro">{impact.message}. {duffelFlight ? 'Replacement flights below are returned by Duffel. Selecting one updates the itinerary only; it does not book a ticket.' : 'These recovery options are simulated planning estimates.'}</p>
    {error && <div className="alert alert--error" role="alert">{error}</div>}
    {duffelFlight && <div className="recovery-duffel-note" role="status"><span>{impact.recoverySearchMessage || 'Offers can expire or change. Refresh before applying a replacement.'}</span>{canManage && <Button type="button" onClick={onRefresh} disabled={busy}>{busy ? 'Searching…' : 'Refresh Duffel offers'}</Button>}</div>}
    {impact.impactedSegments.length > 0 && <section className="recovery-impact"><h3>Connected itinerary at risk</h3><p>The simulated cancellation could leave too little time before these segments:</p><ul>{impact.impactedSegments.map(segment => <li key={segment.id}><strong>{segment.title}</strong><span>{segment.reason}</span></li>)}</ul></section>}
    <div className="recovery-sort"><label>Compare by<select value={order} onChange={event => setOrder(event.target.value as Order)}><option value="PRACTICAL">Fewest itinerary conflicts</option><option value="ARRIVAL">Earliest arrival</option><option value="COST">Lowest price within currency</option></select></label><span>{options.length} {duffelFlight ? 'Duffel offers' : 'simulated options'}</span></div>
    {options.length ? <div className="alternatives">{options.map(option => <article className="alternative" key={option.id}>
      <div className="alternative__main"><span className="eyebrow">{option.source === 'DUFFEL_TEST' ? 'DUFFEL TEST OFFER' : option.source === 'DUFFEL_LIVE' ? 'DUFFEL LIVE OFFER' : 'SIMULATED OPTION'}</span><h3>{option.title}</h3><p>{option.source ? option.flightOperatingCarriers || option.description : option.description}</p><small>{option.location} · {option.source ? option.startsAt.replace('T', ' ').slice(0,16) + ' ' + (option.flightOriginTimeZone || 'local') + ' → ' + option.endsAt.replace('T', ' ').slice(0,16) + ' ' + (option.flightDestinationTimeZone || 'local') : formatTime(option.startsAt) + ' – ' + formatTime(option.endsAt)}</small>
      <div className="alternative__metrics"><span><strong>{money(String(option.estimatedCost), option.currency || 'INR')}</strong> {option.source ? 'offer price' : 'estimated'}</span><span><strong>{timing(option.arrivalDelayMinutes ?? option.delayMinutes)}</strong> {option.source ? 'arrival vs original' : 'departure timing'}</span><span className={option.impactedSegments.length ? 'impact-warning' : 'impact-clear'}><strong>{option.impactedSegments.length}</strong> downstream segments at risk</span></div>
      {option.source && <p className="alternative__comparison">{option.costDifference == null ? 'Original fare uses a different currency; no direct price comparison.' : 'Fare difference: ' + (option.costDifference >= 0 ? '+' : '−') + money(String(Math.abs(option.costDifference)), option.currency || 'INR')} · {option.flightStops === 0 ? 'Nonstop' : option.flightStops + ' stop(s)'}{option.flightDurationMinutes ? ' · ' + Math.floor(option.flightDurationMinutes / 60) + 'h ' + option.flightDurationMinutes % 60 + 'm' : ''}. Offer expires {option.flightExpiresAt ? new Date(option.flightExpiresAt).toLocaleString() : 'soon'}.</p>}
      <p className="alternative__practicality">{option.practicality}</p>{option.impactedSegments.length > 0 && <details><summary>See connection risks</summary><ul>{option.impactedSegments.map(segment => <li key={segment.id}>{segment.title}: {segment.reason}</li>)}</ul></details>}</div>
      {canManage ? <Button variant="primary" disabled={busy || Boolean(option.flightExpiresAt && Date.parse(option.flightExpiresAt) <= now)} onClick={() => onApply(option)}>{busy ? 'Applying…' : option.source ? 'Use in itinerary' : 'Apply option'}</Button> : <span className="muted">Coordinator action</span>}
    </article>)}</div> : <EmptyState title={duffelFlight ? 'No current Duffel replacement offers' : 'No valid options'}>{duffelFlight ? (canManage ? 'Try Refresh Duffel offers. TripShield will not invent flight availability.' : 'Your coordinator can refresh the search. TripShield will not invent flight availability.') : 'No simulated replacement fits this trip’s dates and confirmed bookings.'}</EmptyState>}
  </Modal>
}
