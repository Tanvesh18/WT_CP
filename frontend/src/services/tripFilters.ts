import type { Trip } from '../types'
export type TripCriteria = { query: string; status: string; risk: string; from: string; to: string; sort: 'DATE' | 'LATEST' | 'RISK' }
export const emptyCriteria = (): TripCriteria => ({ query: '', status: 'ALL', risk: 'ALL', from: '', to: '', sort: 'DATE' })
const riskRank = { HIGH: 0, MEDIUM: 1, LOW: 2 }
export function filterTrips(trips: Trip[], criteria: TripCriteria): Trip[] {
  const term = criteria.query.trim().toLocaleLowerCase()
  return trips.filter(trip =>
    (criteria.status === 'ALL' || trip.status === criteria.status) &&
    (criteria.risk === 'ALL' || trip.riskLevel === criteria.risk) &&
    (!criteria.from || trip.endDate >= criteria.from) &&
    (!criteria.to || trip.startDate <= criteria.to) &&
    (!term || [trip.traveler, trip.travelerEmail, trip.origin, trip.destination, trip.startDate, trip.endDate, String(trip.id)].some(value => value?.toLocaleLowerCase().includes(term)))
  ).sort((a, b) => criteria.sort === 'RISK' ? riskRank[a.riskLevel] - riskRank[b.riskLevel] || a.startDate.localeCompare(b.startDate) : criteria.sort === 'LATEST' ? b.startDate.localeCompare(a.startDate) : a.startDate.localeCompare(b.startDate))
}
