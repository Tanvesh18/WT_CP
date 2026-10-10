import type { Session, TripItem } from '../types'
import { formatDate, formatTime } from './format'
import { Button, EmptyState } from './ui'

const label = { FLIGHT: 'Flight', HOTEL: 'Stay', TRANSPORT: 'Ground transport' }
const eventLabel = { FLIGHT: 'cancellation', HOTEL: 'hotel issue', TRANSPORT: 'transport issue' }
export function ItineraryTimeline({ items, role, cancelled, busy, onSimulate, onOptions }: { items: TripItem[]; role: Session['role']; cancelled: boolean; busy: boolean; onSimulate?: (item: TripItem, weather?: boolean) => void; onOptions?: (item: TripItem) => void }) {
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
          <div className="segment__meta"><span className="eyebrow">{label[item.kind]}</span><span>{item.flightSource ? item.startsAt.slice(0,16).replace('T', ' ') + ' ' + (item.flightOriginTimeZone || 'local') + ' → ' + item.endsAt.slice(0,16).replace('T', ' ') + ' ' + (item.flightDestinationTimeZone || 'local') : formatTime(item.startsAt) + ' – ' + formatTime(item.endsAt)}</span></div>
          <div className="segment__title"><h4>{item.title}</h4>{role === 'COORDINATOR' && <span className={'segment__status segment__status--' + item.status.toLowerCase()}>{item.status === 'AFFECTED' ? 'Affected' : item.status === 'AT_RISK' ? 'At risk' : item.status === 'REPLACED' ? 'Replaced' : item.flightSource ? 'Planned' : 'Confirmed'}</span>}</div>
          <p>{item.location}</p>{item.flightSource && <p className="segment__flight-meta">{item.flightOperatingCarriers || 'Airline not provided'}{item.flightDurationMinutes ? ' · ' + Math.floor(item.flightDurationMinutes / 60) + 'h ' + item.flightDurationMinutes % 60 + 'm' : ''}{item.flightStops != null ? ' · ' + (item.flightStops === 0 ? 'Nonstop' : item.flightStops + ' stop(s)') : ''}</p>}{item.flightSource && <p className="segment__change">{item.flightSource === 'DUFFEL_TEST' ? 'Duffel test offer' : 'Duffel live offer'} · {item.flightCurrency} {item.flightAmount} · Itinerary only, no ticket booked.</p>}{role === 'COORDINATOR' && item.changeNote && <p className="segment__change">{item.changeNote}</p>}
          {role === 'COORDINATOR' && !cancelled && item.status === 'CONFIRMED' && <div className="segment__actions"><Button disabled={busy} onClick={() => onSimulate?.(item)}>Simulate {eventLabel[item.kind]}</Button><Button disabled={busy} onClick={() => onSimulate?.(item, true)}>Simulate weather</Button></div>}
          {role === 'COORDINATOR' && !cancelled && (item.status === 'AFFECTED' || item.status === 'AT_RISK') && <div className="segment__actions"><Button variant="primary" disabled={busy} onClick={() => onOptions?.(item)}>Review alternatives →</Button></div>}
        </div>
      </article>
    </div>
  })}</div>
}
