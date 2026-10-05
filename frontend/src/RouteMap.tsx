import { useState } from 'react'

const cities: Record<string, [number, number]> = {
  pune: [73.8567, 18.5204], mumbai: [72.8777, 19.076], delhi: [77.1025, 28.7041],
  'new delhi': [77.209, 28.6139], bengaluru: [77.5946, 12.9716], bangalore: [77.5946, 12.9716],
  hyderabad: [78.4867, 17.385], chennai: [80.2707, 13.0827], kolkata: [88.3639, 22.5726],
  ahmedabad: [72.5714, 23.0225], jaipur: [75.7873, 26.9124], goa: [73.8278, 15.4909],
  kochi: [76.2673, 9.9312], lucknow: [80.9462, 26.8467], nagpur: [79.0882, 21.1458],
  surat: [72.8311, 21.1702], bhopal: [77.4126, 23.2599], chandigarh: [76.7794, 30.7333],
}
const point = (city: string) => cities[city.trim().toLowerCase()]
const project = ([lon, lat]: [number, number]): [number, number] => [30 + (lon - 68) * 15, 395 - (lat - 6) * 12]

export default function RouteMap({ origin, destination, locations }: { origin: string; destination: string; locations: string[] }) {
  const [active, setActive] = useState('')
  const plotted = [origin, ...locations, destination].filter((name, index, all) => point(name) && all.indexOf(name) === index)
  const route = [origin, destination].map(point).filter((value): value is [number, number] => Boolean(value)).map(project)
  return <div className="map-wrap">
    <div className="map-board"><svg viewBox="0 0 520 420" role="img" aria-label={`Schematic route map from ${origin} to ${destination}`}>
      <defs><pattern id="grid" width="50" height="50" patternUnits="userSpaceOnUse"><path d="M 50 0 L 0 0 0 50" fill="none" stroke="#dbe9e8" strokeWidth="1" /></pattern></defs>
      <rect width="520" height="420" rx="12" fill="#eaf5f3" /><rect width="520" height="420" fill="url(#grid)" />
      <text x="20" y="30" className="map-region">INDIA · ROUTE OVERVIEW</text>
      {route.length === 2 && <line x1={route[0][0]} y1={route[0][1]} x2={route[1][0]} y2={route[1][1]} stroke="#0b9c8d" strokeWidth="3" strokeDasharray="8 6" />}
      {plotted.map(name => { const [x, y] = project(point(name)!); return <g key={name} onClick={() => setActive(name)} className="map-marker"><circle cx={x} cy={y} r="9" fill="#0b9c8d" stroke="white" strokeWidth="3" /><text x={x + 13} y={y + 4}>{name}</text></g> })}
    </svg></div>
    <p className="map-caption">{active ? `${active} selected. ` : ''}Schematic city positions; itinerary addresses and live location are not tracked.</p>
    {(!point(origin) || !point(destination)) && <p className="map-caption">Add a supported city such as Pune, Mumbai, Delhi, Bengaluru, Hyderabad, Chennai, or Kolkata to plot the full route.</p>}
  </div>
}
