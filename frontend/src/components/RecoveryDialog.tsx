import { useMemo, useState } from 'react'
import type { Alternative, Impact } from '../types'
import { formatTime } from './format'
import { Button, EmptyState, Modal } from './ui'

type Order = 'PRACTICAL' | 'FASTEST' | 'COST'
export function RecoveryDialog({ impact, busy, error, onClose, onApply }: { impact: Impact; busy: boolean; error: string; onClose: () => void; onApply: (option: Alternative) => void }) {
  const [order, setOrder] = useState<Order>('PRACTICAL')
  const options = useMemo(() => [...impact.alternatives].sort((a, b) => {
    if (order === 'COST') return a.estimatedCost - b.estimatedCost || a.delayMinutes - b.delayMinutes
    if (order === 'FASTEST') return a.delayMinutes - b.delayMinutes || a.impactedSegments.length - b.impactedSegments.length
    return a.impactedSegments.length - b.impactedSegments.length || a.delayMinutes - b.delayMinutes || a.estimatedCost - b.estimatedCost
  }), [impact.alternatives, order])
  return <Modal title="Review recovery options" onClose={onClose}>
    <p className="modal__intro">{impact.message}. These are simulated alternatives with estimated costs.</p>
    {error && <div className="alert alert--error" role="alert">{error}</div>}
    {impact.impactedSegments.length > 0 && <section className="recovery-impact"><h3>Connected itinerary at risk</h3><p>The simulated disruption could leave too little time before these segments:</p><ul>{impact.impactedSegments.map(segment => <li key={segment.id}><strong>{segment.title}</strong><span>{segment.reason}</span></li>)}</ul></section>}
    <div className="recovery-sort"><label>Compare by<select value={order} onChange={event => setOrder(event.target.value as Order)}><option value="PRACTICAL">Fewest itinerary conflicts</option><option value="FASTEST">Least departure delay</option><option value="COST">Lowest estimated cost</option></select></label><span>{options.length} simulated options</span></div>
    {options.length ? <div className="alternatives">{options.map(option => <article className="alternative" key={option.id}>
      <div className="alternative__main"><span className="eyebrow">{option.kind}</span><h3>{option.title}</h3><p>{option.description}</p><small>{option.location} · {formatTime(option.startsAt)} – {formatTime(option.endsAt)}</small><div className="alternative__metrics"><span><strong>₹{option.estimatedCost.toLocaleString()}</strong> estimated</span><span><strong>{option.delayMinutes < 0 ? Math.abs(option.delayMinutes) + ' min earlier' : option.delayMinutes === 0 ? 'No departure delay' : option.delayMinutes + ' min later'}</strong> departure timing</span><span className={option.impactedSegments.length ? 'impact-warning' : 'impact-clear'}><strong>{option.impactedSegments.length}</strong> connected segments at risk</span></div><p className="alternative__practicality">{option.practicality}</p>{option.impactedSegments.length > 0 && <details><summary>See connection risks</summary><ul>{option.impactedSegments.map(segment => <li key={segment.id}>{segment.title}: {segment.reason}</li>)}</ul></details>}</div>
      <Button variant="primary" disabled={busy} onClick={() => onApply(option)}>{busy ? 'Applying…' : 'Apply option'}</Button>
    </article>)}</div> : <EmptyState title="No valid options">No simulated replacement fits this trip’s dates and confirmed bookings.</EmptyState>}
  </Modal>
}
