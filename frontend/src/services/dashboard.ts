import type { Trip } from '../types'

export const localDay = (date = new Date()) => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
export const addDays = (day: string, days: number) => { const date = new Date(day + 'T12:00:00'); date.setDate(date.getDate() + days); return localDay(date) }
export const upcomingTrips = (trips: Trip[], today = localDay()) => trips.filter(trip => trip.status !== 'CANCELLED' && trip.endDate >= today).sort((a,b) => a.startDate.localeCompare(b.startDate) || a.id - b.id)
export const activeToday = (trips: Trip[], today = localDay()) => trips.filter(trip => trip.status !== 'CANCELLED' && trip.startDate <= today && trip.endDate >= today)
export const actionForTrip = (trip: Trip) => {
  const affected = trip.items.filter(item => item.status === 'AFFECTED' || item.status === 'AT_RISK')
  if (trip.status === 'REQUESTED') return { label: 'Review request', reason: 'Trip request is awaiting coordinator review.' }
  if (trip.checkInStatus === 'NEEDS_HELP') return { label: 'Open assistance', reason: trip.checkInNote?.trim() || 'Traveler requested help.' }
  if (affected.length || trip.status === 'NEEDS_ATTENTION') return { label: 'Review recovery', reason: affected.length ? `${affected.length} itinerary ${affected.length === 1 ? 'item needs' : 'items need'} attention.` : trip.riskReason || 'This trip needs attention.' }
  if (trip.budgetExceeded) return { label: 'Review budget', reason: 'Estimated itinerary exceeds the trip budget.' }
  return { label: 'View trip', reason: 'Review the saved itinerary.' }
}
export const attentionTrips = (trips: Trip[]) => trips.filter(trip => !['CANCELLED', 'REJECTED'].includes(trip.status) && (trip.status === 'REQUESTED' || trip.status === 'NEEDS_ATTENTION' || trip.checkInStatus === 'NEEDS_HELP' || Boolean(trip.budgetExceeded))).sort((a,b) => Number(b.checkInStatus === 'NEEDS_HELP') - Number(a.checkInStatus === 'NEEDS_HELP') || Number(b.items.some(item => item.status === 'AFFECTED' || item.status === 'AT_RISK')) - Number(a.items.some(item => item.status === 'AFFECTED' || item.status === 'AT_RISK')) || Number(b.status === 'REQUESTED') - Number(a.status === 'REQUESTED') || Number(Boolean(b.budgetExceeded)) - Number(Boolean(a.budgetExceeded)) || a.startDate.localeCompare(b.startDate))
export const departuresSoon = (trips: Trip[], today = localDay()) => trips.filter(trip => trip.status !== 'CANCELLED' && trip.startDate >= today && trip.startDate <= addDays(today, 7))
export const daysUntil = (day: string, today = localDay()) => Math.round((new Date(day + 'T12:00:00').getTime() - new Date(today + 'T12:00:00').getTime()) / 86400000)
export const nextSegment = (trip: Trip, now = new Date()) => { const stamp = typeof now === 'string' ? now : localDay(now) + 'T' + [now.getHours(), now.getMinutes()].map(value => String(value).padStart(2, '0')).join(':'); return trip.items.filter(item => item.status !== 'REPLACED' && item.endsAt >= stamp).sort((a,b) => a.startsAt.localeCompare(b.startsAt))[0] || null }
