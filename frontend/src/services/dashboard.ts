import type { Trip } from '../types'

export const localDay = (date = new Date()) => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
export const addDays = (day: string, days: number) => { const date = new Date(day + 'T12:00:00'); date.setDate(date.getDate() + days); return localDay(date) }
export const upcomingTrips = (trips: Trip[], today = localDay()) => trips.filter(trip => trip.status !== 'CANCELLED' && trip.endDate >= today).sort((a,b) => a.startDate.localeCompare(b.startDate) || a.id - b.id)
export const activeToday = (trips: Trip[], today = localDay()) => trips.filter(trip => trip.status !== 'CANCELLED' && trip.startDate <= today && trip.endDate >= today)
export const attentionTrips = (trips: Trip[]) => trips.filter(trip => trip.status !== 'CANCELLED' && (trip.status === 'NEEDS_ATTENTION' || trip.checkInStatus === 'NEEDS_HELP')).sort((a,b) => (b.checkInStatus === 'NEEDS_HELP' ? 1 : 0) - (a.checkInStatus === 'NEEDS_HELP' ? 1 : 0) || ({ HIGH: 3, MEDIUM: 2, LOW: 1 }[b.riskLevel] - { HIGH: 3, MEDIUM: 2, LOW: 1 }[a.riskLevel]) || a.startDate.localeCompare(b.startDate))
export const departuresSoon = (trips: Trip[], today = localDay()) => trips.filter(trip => trip.status !== 'CANCELLED' && trip.startDate >= today && trip.startDate <= addDays(today, 7))
export const daysUntil = (day: string, today = localDay()) => Math.round((new Date(day + 'T12:00:00').getTime() - new Date(today + 'T12:00:00').getTime()) / 86400000)
export const nextSegment = (trip: Trip, now = new Date()) => { const stamp = typeof now === 'string' ? now : localDay(now) + 'T' + [now.getHours(), now.getMinutes()].map(value => String(value).padStart(2, '0')).join(':'); return trip.items.filter(item => item.status !== 'REPLACED' && item.endsAt >= stamp).sort((a,b) => a.startsAt.localeCompare(b.startsAt))[0] || null }
