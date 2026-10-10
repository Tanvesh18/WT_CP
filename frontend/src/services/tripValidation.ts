import type { TripDraft } from '../types'

const today = () => { const date = new Date(); return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-') }

export function validateTripDraft(draft: TripDraft): string | null {
  if (!draft.traveler.trim() || !draft.travelerEmail.trim() || !draft.origin.trim() || !draft.destination.trim()) return 'Complete the traveler and route details.'
  if (!draft.startDate || !draft.endDate) return 'Choose departure and trip end dates.'
  if (draft.startDate < today()) return 'Departure date cannot be before today.'
  if (draft.endDate < draft.startDate) return 'Trip end date must be on or after departure date.'
  if (!draft.items.length) return 'Add at least one itinerary segment.'
  for (const [index, item] of draft.items.entries()) {
    const label = 'Segment ' + (index + 1)
    if (!item.title.trim() || !item.location.trim() || !item.startsAt || !item.endsAt) return label + ' needs a title, location, start, and end.'
    if (item.endsAt <= item.startsAt) return label + ' must end after it starts.'
    if (item.startsAt.slice(0, 10) < draft.startDate || item.endsAt.slice(0, 10) > draft.endDate) return label + ' must fit within the trip dates.'
  }
  return null
}
