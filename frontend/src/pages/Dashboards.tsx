import type { ReactNode } from 'react'
import { formatDate } from '../components/format'
import { Button, EmptyState, StatusBadge } from '../components/ui'
import { activeToday, attentionTrips, departuresSoon, localDay, upcomingTrips } from '../services/dashboard'
import type { Trip } from '../types'

function Metric({ label, value, detail, tone }: { label: string; value: number; detail: string; tone?: 'attention' }) {
  return <div className={'overview-metric' + (tone ? ' overview-metric--' + tone : '')}><span>{label}</span><strong>{value}</strong><small>{detail}</small></div>
}
function Route({ trip }: { trip: Trip }) { return <span className="overview-route"><strong>{trip.originAirportCode || trip.origin}</strong><span aria-hidden="true">→</span><strong>{trip.destinationAirportCode || trip.destination}</strong></span> }
function JourneyRow({ trip, onOpen, context }: { trip: Trip; onOpen: (id: number) => void; context?: 'priority' | 'schedule' }) {
  const affected = trip.items.filter(item => item.status === 'AFFECTED' || item.status === 'AT_RISK')
  return <button type="button" className="overview-row" onClick={() => onOpen(trip.id)} aria-label={'Open trip from ' + trip.origin + ' to ' + trip.destination + ' for ' + trip.traveler}>
    <span className="overview-row__main"><Route trip={trip}/><small>{trip.traveler} · {formatDate(trip.startDate)} – {formatDate(trip.endDate)}</small>{context === 'priority' && <em>{trip.checkInStatus === 'NEEDS_HELP' ? 'Traveler requested help' : affected[0]?.changeNote || trip.riskReason}</em>}</span>
    <span className="overview-row__aside">{context === 'priority' ? <span className={'risk-chip risk-chip--' + trip.riskLevel.toLowerCase()}>{trip.riskLevel.toLowerCase()} risk</span> : <StatusBadge status={trip.status}/>}<span className="overview-row__arrow" aria-hidden="true">↗</span></span>
  </button>
}
function Section({ eyebrow, title, count, action, children }: { eyebrow: string; title: string; count?: number; action?: { label: string; onClick: () => void }; children: ReactNode }) {
  return <section className="surface overview-section"><div className="section-head"><div><span className="eyebrow">{eyebrow}</span><h3>{title}</h3></div><div className="overview-section__actions">{typeof count === 'number' && <span className="count-pill">{count}</span>}{action && <Button variant="quiet" onClick={action.onClick}>{action.label}</Button>}</div></div>{children}</section>
}
export function CoordinatorDashboard({ trips, onOpen, onTrips, onNewTrip }: { trips: Trip[]; onOpen: (id: number) => void; onTrips: () => void; onNewTrip: () => void }) {
  const today = localDay()
  const upcoming = upcomingTrips(trips, today)
  const traveling = activeToday(trips, today)
  const queue = attentionTrips(trips)
  const requests = trips.filter(trip => trip.status === 'REQUESTED')
  const soon = departuresSoon(trips, today)
  const pending = traveling.filter(trip => trip.checkInStatus === 'PENDING')
  const affected = trips.filter(trip => trip.status !== 'CANCELLED').flatMap(trip => trip.items).filter(item => item.status === 'AFFECTED').length
  const connections = trips.filter(trip => trip.status !== 'CANCELLED').flatMap(trip => trip.items).filter(item => item.status === 'AT_RISK').length
  return <div className="dashboard overview-page"><div className="overview-hero"><div><span className="eyebrow">TRAVEL OPERATIONS · {formatDate(today)}</span><h2>Operations overview</h2><p>See who is traveling, what changed, and which trips need a response.</p></div></div>
    <section className="overview-metrics" aria-label="Operations metrics"><Metric label="Traveling today" value={traveling.length} detail="Trips in progress"/><Metric label="Needs action" value={queue.length} detail="Disruption or help request" tone="attention"/><Metric label="Departing in 7 days" value={soon.length} detail="Upcoming departures"/><Metric label="Awaiting check-in" value={pending.length} detail="Travelers currently away"/></section>
    <div className="overview-layout"><div className="overview-layout__main"><Section eyebrow="PRIORITY QUEUE" title="Action required" count={queue.length} action={{ label: 'All trips →', onClick: onTrips }}>{queue.length ? <div className="overview-rows">{queue.slice(0, 6).map(trip => <JourneyRow key={trip.id} trip={trip} onOpen={onOpen} context="priority"/>)}</div> : <EmptyState title="No open travel issues">Disruptions and traveler help requests will appear here.</EmptyState>}</Section><Section eyebrow="BOOKING REQUESTS" title="Awaiting your review" count={requests.length}>{requests.length ? <div className="overview-rows">{requests.map(trip => <JourneyRow key={trip.id} trip={trip} onOpen={onOpen} context="schedule"/>)}</div> : <EmptyState title="No booking requests">Traveler requests will appear here.</EmptyState>}</Section><Section eyebrow="TRAVEL CALENDAR" title="Upcoming journeys" count={upcoming.length} action={{ label: 'View schedule →', onClick: onTrips }}>{upcoming.length ? <div className="overview-rows">{upcoming.slice(0, 5).map(trip => <JourneyRow key={trip.id} trip={trip} onOpen={onOpen} context="schedule"/>)}</div> : <div className="overview-empty"><EmptyState title="No journeys scheduled">Create a trip to start planning travel.</EmptyState><Button variant="primary" onClick={onNewTrip}>Create a trip</Button></div>}</Section></div><aside className="overview-layout__side"><section className="surface overview-brief"><span className="eyebrow">CURRENT SIGNALS</span><h3>Travel health</h3><div><span>Disrupted segments</span><strong>{affected}</strong></div><div><span>Connections at risk</span><strong>{connections}</strong></div><div><span>Help requests</span><strong>{trips.filter(trip => trip.status !== 'CANCELLED' && trip.checkInStatus === 'NEEDS_HELP').length}</strong></div><p>These counts come from saved itineraries and traveler check-ins. Disruptions shown here are simulated in TripShield.</p></section><section className="surface overview-brief"><span className="eyebrow">WORKFLOW</span><h3>Keep trips moving</h3><p>Review the priority queue, inspect affected segments, and compare recovery options in the trip workspace.</p><Button onClick={onTrips}>Open trip workspace →</Button></section></aside></div>
  </div>
}
export function TravelerDashboard({ trips, onOpen, onTrips }: { trips: Trip[]; onOpen: (id: number) => void; onTrips: () => void }) {
  const upcoming = upcomingTrips(trips)
  return <div className="dashboard overview-page"><div className="overview-hero"><div><span className="eyebrow">MY TRAVEL</span><h2>My trips</h2><p>Plan a journey and review the itineraries assigned to you.</p></div></div>
    <section className="overview-metrics" aria-label="Your trips"><Metric label="My trips" value={trips.length} detail="Saved itineraries"/><Metric label="Upcoming" value={upcoming.length} detail="Future journeys"/><Metric label="Awaiting review" value={trips.filter(trip => trip.status === 'REQUESTED').length} detail="Booking requests"/><Metric label="Cancelled" value={trips.filter(trip => trip.status === 'CANCELLED').length} detail="Past requests"/></section>
    <Section eyebrow="YOUR JOURNEYS" title="My trips" count={trips.length} action={{ label: 'View all →', onClick: onTrips }}>{trips.length ? <div className="overview-rows">{[...trips].sort((a,b) => a.startDate.localeCompare(b.startDate)).map(trip => <button type="button" className="overview-row" key={trip.id} onClick={() => onOpen(trip.id)}><span className="overview-row__main"><Route trip={trip}/><small>{formatDate(trip.startDate)} – {formatDate(trip.endDate)}</small></span><span className="overview-row__aside">{trip.status === 'REQUESTED' && <StatusBadge status={trip.status}/>}<span className="overview-row__arrow" aria-hidden="true">↗</span></span></button>)}</div> : <div className="overview-empty"><EmptyState title="No trips yet">Use “Book a trip” in the top bar to submit a request.</EmptyState></div>}</Section>
    <p className="muted">Flight offers are saved as itinerary plans. TripShield does not issue tickets or charge payments.</p>
  </div>
}
