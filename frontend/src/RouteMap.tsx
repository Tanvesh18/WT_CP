import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { TripItem } from './types'
import { routePoints } from './services/routeGeometry'

export default function RouteMap({ items }: { items: TripItem[] }) {
  const flights = items.filter(item => item.kind === 'FLIGHT' && item.status !== 'REPLACED')
  const [activeId, setActiveId] = useState<number | null>(null)
  const [tileError, setTileError] = useState(false)
  const mapNode = useRef<HTMLDivElement>(null)
  const active = flights.find(item => item.id === activeId) || flights[0]
  const flightRouteJson = active?.flightRouteJson
  const location = active?.location
  const points = routePoints(flightRouteJson, location)

  useEffect(() => {
    const node = mapNode.current
    const route = routePoints(flightRouteJson, location)
    if (!node || route.length < 2) return
    const map = L.map(node, { scrollWheelZoom: false, zoomControl: true })
    const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 18,
    }).addTo(map)
    tiles.on('tileerror', () => setTileError(true))
    const positions = route.map(point => L.latLng(point.lat, point.lon))
    L.polyline(positions, { color: '#13757a', weight: 4, opacity: 0.9, dashArray: '8 6' }).addTo(map)
    route.forEach((point, index) => {
      L.circleMarker(positions[index], {
        radius: 7,
        color: '#fff', weight: 2,
        fillColor: index === 0 ? '#143747' : '#13757a', fillOpacity: 1,
      }).addTo(map).bindTooltip(point.code, { permanent: true, direction: 'top', offset: [0, -8] })
    })
    const bounds = L.latLngBounds(positions)
    const fit = () => { map.invalidateSize({ pan: false }); map.fitBounds(bounds, { padding: [42, 42], maxZoom: 9, animate: false }) }
    const observer = new ResizeObserver(fit)
    observer.observe(node)
    const frame = requestAnimationFrame(fit)
    return () => { cancelAnimationFrame(frame); observer.disconnect(); map.remove() }
  }, [flightRouteJson, location])

  return <section className="route-map" aria-label="Flight route map">
    <div className="route-map__head"><div><span className="eyebrow">GEOGRAPHIC ROUTE</span><h3>Flight path</h3></div></div>
    {flights.length > 1 && <div className="route-map__flights" aria-label="Choose flight route">{flights.map(item => <button type="button" key={item.id} className={active?.id === item.id ? 'active' : ''} onClick={() => { setActiveId(item.id); setTileError(false) }}>{item.location}</button>)}</div>}
    {points.length >= 2 ? <><div className="geo-map" ref={mapNode} role="img" aria-label={'Route through ' + points.map(point => point.code).join(', ')} /><ol className="route-map__stops">{points.map((point, index) => <li key={index}><span>{index === 0 ? 'Departure' : index === points.length - 1 ? 'Arrival' : 'Connection'}</span><strong>{point.code}</strong><small>{point.name}</small></li>)}</ol>{tileError && <p className="map-caption">Map tiles are unavailable; airport markers still show the route.</p>}<p className="map-caption">The line connects airports geographically; it is not a tracked aircraft path.</p></> : <div className="route-map__fallback"><strong>Route coordinates unavailable</strong><p>{active ? 'Airport coordinates are unavailable for this route.' : 'Select a Duffel flight to see its airport route here.'}</p></div>}
  </section>
}
