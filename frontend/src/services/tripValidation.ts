import type { TripDraft } from '../types'

export function validateTripDraft(draft: TripDraft): string | null {
  if (!draft.traveler.trim() || !draft.travelerEmail.trim() || !draft.origin.trim() || !draft.destination.trim()) return 'Complete the traveler and route details.'
  if (!draft.startDate || !draft.endDate || draft.endDate < draft.startDate) return 'Choose an end date on or after the start date.'
  if (!draft.items.length) return 'Add at least one itinerary segment.'
  for (const [index, item] of draft.items.entries()) {
    const label = 'Segment ' + (index + 1)
    if (!item.title.trim() || !item.location.trim() || !item.startsAt || !item.endsAt) return label + ' needs a title, location, start, and end.'
    if (item.endsAt <= item.startsAt) return label + ' must end after it starts.'
    if (item.startsAt.slice(0, 10) < draft.startDate || item.endsAt.slice(0, 10) > draft.endDate) return label + ' must fit within the trip dates.'
  }
  return null
}
