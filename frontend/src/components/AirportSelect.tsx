import { useState } from 'react'
import { airportByCode, searchAirports } from '../services/airports'

export function AirportSelect({ label, value, onChange, exclude }: { label: string; value: string; onChange: (code: string) => void; exclude?: string }) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const selected = airportByCode(value)
  const matches = searchAirports(query, exclude)
  function choose(code: string) { onChange(code); setQuery(''); setOpen(false) }
  return <div className="airport-field">
    <label>{label}<input role="combobox" aria-expanded={open} aria-autocomplete="list" aria-controls={label.replaceAll(' ', '-') + '-airports'} value={open ? query : selected ? selected.city + ' (' + selected.code + ')' : ''} placeholder="Search city or airport" onFocus={() => { setOpen(true); setQuery('') }} onChange={event => { setQuery(event.target.value); setOpen(true); setActive(0); onChange('') }} onKeyDown={event => { if (event.key === 'ArrowDown') { event.preventDefault(); setActive(Math.min(active + 1, matches.length - 1)) } if (event.key === 'ArrowUp') { event.preventDefault(); setActive(Math.max(active - 1, 0)) } if (event.key === 'Enter' && open && matches[active]) { event.preventDefault(); choose(matches[active].code) } if (event.key === 'Escape') setOpen(false) }} onBlur={() => window.setTimeout(() => setOpen(false), 120)} /></label>
    {open && <div className="airport-field__list" id={label.replaceAll(' ', '-') + '-airports'} role="listbox">{matches.length ? matches.map((airport, index) => <button type="button" role="option" aria-selected={index === active} key={airport.code} className={index === active ? 'active' : ''} onMouseDown={event => event.preventDefault()} onClick={() => choose(airport.code)}><strong>{airport.city} <span>{airport.code}</span></strong><small>{airport.name} · {airport.country}</small></button>) : <p>No matching airport in the search list.</p>}</div>}
  </div>
}
