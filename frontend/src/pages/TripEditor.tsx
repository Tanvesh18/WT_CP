import { useState, type FormEvent } from 'react'
import type { ItemDraft, Kind, Trip, TripDraft } from '../types'
import { Button, Modal } from '../components/ui'
import { validateTripDraft } from '../services/tripValidation'

const blankItem = (): ItemDraft => ({ kind: 'FLIGHT', title: '', location: '', startsAt: '', endsAt: '' })
const blankTrip = (): TripDraft => ({ traveler: '', travelerEmail: '', origin: '', destination: '', startDate: '', endDate: '', items: [blankItem()] })
function initialDraft(trip: Trip | null): TripDraft {
  if (!trip) return blankTrip()
  const items = trip.items.filter(item => item.status === 'CONFIRMED').map(item => ({ id: item.id, kind: item.kind, title: item.title, location: item.location, startsAt: item.startsAt.slice(0, 16), endsAt: item.endsAt.slice(0, 16) }))
  return { traveler: trip.traveler, travelerEmail: trip.travelerEmail || '', origin: trip.origin, destination: trip.destination, startDate: trip.startDate, endDate: trip.endDate, items: items.length ? items : [blankItem()] }
}
export function TripEditor({ trip, onClose, onSave, busy, error }: { trip: Trip | null; onClose: () => void; onSave: (draft: TripDraft, id?: number) => Promise<void>; busy: boolean; error: string }) {
  const [draft, setDraft] = useState<TripDraft>(() => initialDraft(trip))
  const [localError, setLocalError] = useState('')
  function field(key: keyof Omit<TripDraft, 'items'>, value: string) { setDraft(current => ({ ...current, [key]: value })); setLocalError('') }
  function itemField(index: number, key: keyof ItemDraft, value: string) { setDraft(current => ({ ...current, items: current.items.map((item, i) => i === index ? { ...item, [key]: value } : item) })); setLocalError('') }
  async function submit(event: FormEvent) {
    event.preventDefault()
    const issue = validateTripDraft(draft)
    if (issue) { setLocalError(issue); return }
    setLocalError('')
    await onSave({ ...draft, traveler: draft.traveler.trim(), travelerEmail: draft.travelerEmail.trim(), origin: draft.origin.trim(), destination: draft.destination.trim(), items: draft.items.map(item => ({ ...item, title: item.title.trim(), location: item.location.trim() })) }, trip?.id)
  }
  return <Modal title={trip ? 'Edit trip' : 'Create a trip'} onClose={onClose}>
    <form className="editor" onSubmit={submit}>
      {(error || localError) && <div className="alert alert--error" role="alert">{localError || error}</div>}
      <p className="modal__intro">Assign a traveler and add each flight, stay, or transfer in travel order.</p>
      <div className="form-grid">
        <label>Traveler name<input required maxLength={120} autoComplete="name" value={draft.traveler} onChange={e => field('traveler', e.target.value)} /></label>
        <label>Traveler email<input type="email" required autoComplete="email" value={draft.travelerEmail} onChange={e => field('travelerEmail', e.target.value)} /></label>
        <label>Origin<input required value={draft.origin} onChange={e => field('origin', e.target.value)} /></label>
        <label>Destination<input required value={draft.destination} onChange={e => field('destination', e.target.value)} /></label>
        <label>Start date<input type="date" required value={draft.startDate} onChange={e => field('startDate', e.target.value)} /></label>
        <label>End date<input type="date" required min={draft.startDate} value={draft.endDate} onChange={e => field('endDate', e.target.value)} /></label>
      </div>
      <div className="section-head editor__items-head"><div><span className="eyebrow">TRAVEL PLAN</span><h3>Itinerary segments</h3></div><Button type="button" onClick={() => setDraft(current => ({ ...current, items: [...current.items, blankItem()] }))}>＋ Add segment</Button></div>
      <p className="form-help">Segments must fall within the trip dates. You can add more than one flight, stay, or transfer.</p>
      {draft.items.map((item, index) => <fieldset className="item-editor" key={item.id ?? 'new-' + index}>
        <legend>Segment {index + 1}</legend>
        {draft.items.length > 1 && <Button type="button" variant="quiet" className="item-editor__remove" onClick={() => setDraft(current => ({ ...current, items: current.items.filter((_, i) => i !== index) }))}>Remove</Button>}
        <div className="form-grid">
          <label>Type<select value={item.kind} onChange={e => itemField(index, 'kind', e.target.value as Kind)}><option value="FLIGHT">Flight</option><option value="HOTEL">Hotel</option><option value="TRANSPORT">Ground transport</option></select></label>
          <label>Title<input required value={item.title} onChange={e => itemField(index, 'title', e.target.value)} placeholder={item.kind === 'FLIGHT' ? 'Flight AI 401' : item.kind === 'HOTEL' ? 'Hotel reservation' : 'Airport transfer'} /></label>
          <label>Location<input required value={item.location} onChange={e => itemField(index, 'location', e.target.value)} placeholder="City or airport" /></label>
          <label>Starts<input type="datetime-local" required min={draft.startDate ? draft.startDate + 'T00:00' : undefined} value={item.startsAt} onChange={e => itemField(index, 'startsAt', e.target.value)} /></label>
          <label>Ends<input type="datetime-local" required min={item.startsAt || undefined} max={draft.endDate ? draft.endDate + 'T23:59' : undefined} value={item.endsAt} onChange={e => itemField(index, 'endsAt', e.target.value)} /></label>
        </div>
      </fieldset>)}
      <div className="modal__actions"><Button type="button" onClick={onClose}>Cancel</Button><Button type="submit" variant="primary" disabled={busy}>{busy ? 'Saving…' : trip ? 'Save changes' : 'Create trip'}</Button></div>
    </form>
  </Modal>
}
