import type { TripCriteria } from '../services/tripFilters'
import { emptyCriteria } from '../services/tripFilters'
import { Button } from './ui'
export function TripFilters({ criteria, onChange, total, shown }: { criteria: TripCriteria; onChange: (next: TripCriteria) => void; total: number; shown: number }) {
  const set = (patch: Partial<TripCriteria>) => onChange({ ...criteria, ...patch })
  const hasFilters = criteria.query || criteria.status !== 'ALL' || criteria.risk !== 'ALL' || criteria.from || criteria.to || criteria.sort !== 'DATE'
  return <div className="trip-filters">
    <label className="sr-only" htmlFor="trip-search">Search trips</label><input id="trip-search" type="search" placeholder="Search traveler, route, or trip ID" value={criteria.query} onChange={e => set({ query: e.target.value })} />
    <div className="trip-filters__grid">
      <label>Status<select value={criteria.status} onChange={e => set({ status: e.target.value })}><option value="ALL">All statuses</option><option value="ON_TRACK">On track</option><option value="NEEDS_ATTENTION">Needs attention</option><option value="CANCELLED">Cancelled</option></select></label>
      <label>Risk<select value={criteria.risk} onChange={e => set({ risk: e.target.value })}><option value="ALL">All risk levels</option><option value="HIGH">High</option><option value="MEDIUM">Medium</option><option value="LOW">Low</option></select></label>
      <label>From<input type="date" value={criteria.from} max={criteria.to || undefined} onChange={e => set({ from: e.target.value })} /></label>
      <label>Through<input type="date" value={criteria.to} min={criteria.from || undefined} onChange={e => set({ to: e.target.value })} /></label>
      <label>Sort<select value={criteria.sort} onChange={e => set({ sort: e.target.value as TripCriteria['sort'] })}><option value="DATE">Soonest first</option><option value="LATEST">Latest first</option><option value="RISK">Highest risk first</option></select></label>
    </div>
    <div className="trip-filters__footer"><span>{shown} of {total} trips</span>{hasFilters && <Button variant="quiet" onClick={() => onChange(emptyCriteria())}>Clear filters</Button>}</div>
  </div>
}
