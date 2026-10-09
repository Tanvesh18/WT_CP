import type { Session, TripItem } from '../types'
import { formatDate, formatTime } from './format'
import { Button, EmptyState } from './ui'

const label = { FLIGHT: 'Flight', HOTEL: 'Stay', TRANSPORT: 'Ground transport' }
const eventLabel = { FLIGHT: 'cancellation', HOTEL: 'hotel issue', TRANSPORT: 'transport issue' }
export function ItineraryTimeline({ items, role, cancelled, busy, onSimulate, onOptions }: { items: TripItem[]; role: Session['role']; cancelled: boolean; busy: boolean; onSimulate: (item: TripItem, weather?: boolean) => void; onOptions: (item: TripItem) => void }) {
  const sorted = [...items].sort((a, b) => a.startsAt.localeCompare(b.startsAt) || a.id - b.id)
  if (!sorted.length) return <EmptyState title="No itinerary segments">Add segments when editing this trip.</EmptyState>
  return <div className="itinerary">{sorted.map((item, index) => {
    const date = item.startsAt.slice(0, 10)
    const showDate = index === 0 || date !== sorted[index - 1].startsAt.slice(0, 10)
    return <div key={item.id}>
      {showDate && <div className="itinerary__date"><span>{formatDate(date)}</span></div>}
      <article className={'segment segment--' + item.status.toLowerCase()}>
        <div className="segment__rail"><span/></div>
        <div className="segment__body">
          <div className="segment__meta"><span className="eyebrow">{label[item.kind]}</span><span>{formatTime(item.startsAt)} – {formatTime(item.endsAt)}</span></div>
          <div className="segment__title"><h4>{item.title}</h4><span className={'segment__status segment__status--' + item.status.toLowerCase()}>{item.status === 'AFFECTED' ? 'Affected' : item.status === 'AT_RISK' ? 'At risk' : item.status === 'REPLACED' ? 'Replaced' : 'Confirmed'}</span></div>
          <p>{item.location}</p>{item.changeNote && <p className="segment__change">{item.changeNote}</p>}
          {role === 'COORDINATOR' && !cancelled && item.status === 'CONFIRMED' && <div className="segment__actions"><Button disabled={busy} onClick={() => onSimulate(item)}>Simulate {eventLabel[item.kind]}</Button><Button disabled={busy} onClick={() => onSimulate(item, true)}>Simulate weather</Button></div>}
          {role === 'COORDINATOR' && !cancelled && (item.status === 'AFFECTED' || item.status === 'AT_RISK') && <div className="segment__actions"><Button variant="primary" disabled={busy} onClick={() => onOptions(item)}>Review alternatives →</Button></div>}
        </div>
      </article>
    </div>
  })}</div>
}
