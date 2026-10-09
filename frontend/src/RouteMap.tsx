import { useState } from 'react'

// Approximate city centers for the prototype's schematic India route view.
const cities: Record<string, [number, number]> = {
  pune: [73.8567, 18.5204], mumbai: [72.8777, 19.076], delhi: [77.1025, 28.7041], 'new delhi': [77.209, 28.6139],
  bengaluru: [77.5946, 12.9716], bangalore: [77.5946, 12.9716], hyderabad: [78.4867, 17.385],
  chennai: [80.2707, 13.0827], kolkata: [88.3639, 22.5726], ahmedabad: [72.5714, 23.0225],
  jaipur: [75.7873, 26.9124], goa: [73.8278, 15.4909], kochi: [76.2673, 9.9312],
  lucknow: [80.9462, 26.8467], nagpur: [79.0882, 21.1458], surat: [72.8311, 21.1702],
  bhopal: [77.4126, 23.2599], chandigarh: [76.7794, 30.7333], indore: [75.8577, 22.7196],
  patna: [85.1376, 25.5941], bhubaneswar: [85.8245, 20.2961], guwahati: [91.7362, 26.1445],
  vadodara: [73.1812, 22.3072], coimbatore: [76.9558, 11.0168], visakhapatnam: [83.2185, 17.6868],
}
const lookup = (name: string) => cities[name.trim().toLowerCase()]
const project = ([lon, lat]: [number, number]): [number, number] => [35 + (lon - 68) * 17, 405 - (lat - 6) * 13]
export default function RouteMap({ origin, destination, locations }: { origin: string; destination: string; locations: string[] }) {
  const [active, setActive] = useState('')
  const stops = [origin, ...locations, destination].map(s => s.trim()).filter((name, index, all) => name && all.findIndex(other => other.toLowerCase() === name.toLowerCase()) === index)
  const mapped = stops.filter(name => lookup(name))
  const segments = mapped.slice(1).map((name, index) => [mapped[index], name] as const)
  const canMapRoute = Boolean(lookup(origin) && lookup(destination))
  return <div className="route-map">
    <div className="route-map__head"><div><span className="eyebrow">ROUTE VIEW</span><h3>{origin} <span aria-hidden="true">→</span> {destination}</h3></div><span className="route-map__legend">Schematic · India</span></div>
    {canMapRoute ? <div className="map-board"><svg viewBox="0 0 520 420" role="img" aria-label={'Schematic route from ' + origin + ' to ' + destination}>
      <defs><pattern id="route-grid" width="44" height="44" patternUnits="userSpaceOnUse"><path d="M 44 0 L 0 0 0 44" fill="none" stroke="#dce9e8" strokeWidth="1" /></pattern></defs>
      <rect width="520" height="420" fill="#f1f7f6"/><rect width="520" height="420" fill="url(#route-grid)"/>
      <text x="20" y="28" className="map-region">INDIA · CITY CENTERS</text>
      {segments.map(([a, b]) => { const [x1, y1] = project(lookup(a)); const [x2, y2] = project(lookup(b)); return <line key={a + b} x1={x1} y1={y1} x2={x2} y2={y2} stroke="#227e83" strokeWidth="2.5" strokeDasharray="7 5"/> })}
      {mapped.map((name, index) => { const [x, y] = project(lookup(name)); return <g key={name}><circle cx={x} cy={y} r={active === name ? 10 : 7} fill={index === 0 ? '#163947' : '#188085'} stroke="white" strokeWidth="2.5"/><text x={x + 12} y={y + 4}>{name}</text></g> })}
    </svg></div> : <div className="route-map__fallback"><span className="route-map__endpoint">{origin}</span><span aria-hidden="true">⟶</span><span className="route-map__endpoint">{destination}</span><p>City coordinates are unavailable for this route. The itinerary below still shows every stop.</p></div>}
    <div className="route-map__stops"><h4>Route stops</h4><ol>{stops.map((name, index) => <li key={name}><button type="button" className={active === name ? 'active' : ''} onClick={() => setActive(name)}><span className="route-map__number">{index + 1}</span><span>{name}</span><small>{index === 0 ? 'Origin' : index === stops.length - 1 ? 'Destination' : lookup(name) ? 'Itinerary stop' : 'Location not mapped'}</small></button></li>)}</ol></div>
    <p className="map-caption">Approximate city centers for supported Indian cities. This view does not track the traveler or show live routes.</p>
  </div>
}
