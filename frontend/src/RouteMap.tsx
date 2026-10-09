import { useEffect, useRef, useState } from 'react'
import type { TripItem } from './types'
import { routePoints, type AirportPoint } from './services/routeGeometry'

const clamp = (value: number, low: number, high: number) => Math.min(high, Math.max(low, value))
const mercator = (point: AirportPoint) => ({ x: (point.lon + 180) / 360, y: (1 - Math.log(Math.tan(clamp(point.lat, -85, 85) * Math.PI / 180) + 1 / Math.cos(clamp(point.lat, -85, 85) * Math.PI / 180)) / Math.PI) / 2 })
export default function RouteMap({ items }: { items: TripItem[] }) {
  const flights = items.filter(item => item.kind === 'FLIGHT' && item.status !== 'REPLACED')
  const [activeId, setActiveId] = useState<number | null>(null)
  const [zoomOffset, setZoomOffset] = useState(0)
  const [tileError, setTileError] = useState(false)
  const [size, setSize] = useState({ width: 640, height: 360 })
  const container = useRef<HTMLDivElement>(null)
  useEffect(() => { if (!container.current) return; const observer = new ResizeObserver(entries => setSize({ width: Math.max(260, entries[0].contentRect.width), height: 360 })); observer.observe(container.current); return () => observer.disconnect() }, [])
  const active = flights.find(item => item.id === activeId) || flights[0]
  const points = routePoints(active?.flightRouteJson)
  const view = (() => {
    if (points.length < 2) return null
    const raw = points.map(mercator)
    const firstX = raw[0].x
    const normalized = raw.map(point => { let x = point.x; while (x - firstX > .5) x -= 1; while (firstX - x > .5) x += 1; return { x, y: point.y } })
    const xs = normalized.map(p => p.x), ys = normalized.map(p => p.y)
    const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys)
    const fitting = Math.floor(Math.log2(Math.min((size.width - 110) / (Math.max(.003, maxX - minX) * 256), (size.height - 110) / (Math.max(.003, maxY - minY) * 256))))
    const zoom = clamp(fitting + zoomOffset, 2, 10)
    const scale = 256 * 2 ** zoom
    const centerX = (minX + maxX) / 2 * scale, centerY = (minY + maxY) / 2 * scale
    const plotted = normalized.map(point => ({ x: point.x * scale - centerX + size.width / 2, y: point.y * scale - centerY + size.height / 2 }))
    const tiles: { x: number; y: number; left: number; top: number; key: string }[] = []
    const xStart = Math.floor((centerX - size.width / 2) / 256), xEnd = Math.floor((centerX + size.width / 2) / 256)
    const yStart = Math.floor((centerY - size.height / 2) / 256), yEnd = Math.floor((centerY + size.height / 2) / 256)
    for (let x = xStart; x <= xEnd; x++) for (let y = yStart; y <= yEnd; y++) if (y >= 0 && y < 2 ** zoom) tiles.push({ x: ((x % 2 ** zoom) + 2 ** zoom) % 2 ** zoom, y, left: x * 256 - centerX + size.width / 2, top: y * 256 - centerY + size.height / 2, key: x + '-' + y })
    return { zoom, plotted, tiles }
  })()
  return <section className="route-map" aria-label="Flight route map"><div className="route-map__head"><div><span className="eyebrow">GEOGRAPHIC ROUTE</span><h3>Flight path</h3></div>{view && <div className="route-map__zoom"><button type="button" onClick={() => setZoomOffset(value => clamp(value + 1, -2, 2))} aria-label="Zoom in">+</button><button type="button" onClick={() => setZoomOffset(value => clamp(value - 1, -2, 2))} aria-label="Zoom out">−</button></div>}</div>
    {flights.length > 1 && <div className="route-map__flights" aria-label="Choose flight route">{flights.map(item => <button type="button" key={item.id} className={active?.id === item.id ? 'active' : ''} onClick={() => { setActiveId(item.id); setZoomOffset(0) }}>{item.location}</button>)}</div>}
    {view ? <><div className="geo-map" ref={container} style={{ height: size.height }} role="img" aria-label={'Route through ' + points.map(point => point.code).join(', ')}>{view.tiles.map(tile => <img alt="" aria-hidden="true" loading="lazy" key={tile.key} src={`https://tile.openstreetmap.org/${view.zoom}/${tile.x}/${tile.y}.png`} style={{ left: tile.left, top: tile.top }} onError={() => setTileError(true)}/>)}<svg className="geo-map__overlay" viewBox={`0 0 ${size.width} ${size.height}`} aria-hidden="true"><polyline points={view.plotted.map(point => point.x + ',' + point.y).join(' ')} fill="none" stroke="#13757a" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" strokeDasharray="8 5"/>{view.plotted.map((point,index) => <g key={index}><circle cx={point.x} cy={point.y} r="7" fill={index === 0 ? '#143747' : '#13757a'} stroke="#fff" strokeWidth="2"/></g>)}</svg>{view.plotted.map((point,index) => <span className="geo-map__label" style={{ left: point.x, top: point.y }} key={index}>{points[index].code}</span>)}<div className="geo-map__attribution">© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors</div></div><ol className="route-map__stops">{points.map((point,index) => <li key={index}><span>{index === 0 ? 'Departure' : index === points.length - 1 ? 'Arrival' : 'Connection'}</span><strong>{point.code}</strong><small>{point.name}</small></li>)}</ol>{tileError && <p className="map-caption">Map tiles are unavailable; the airport markers still use Duffel coordinates.</p>}<p className="map-caption">Airport positions come from the selected Duffel offer. The line connects airports geographically; it is not a tracked aircraft path.</p></> : <div className="route-map__fallback"><strong>Route coordinates unavailable</strong><p>{active ? 'This flight was saved before airport coordinates were recorded, or Duffel did not return them.' : 'Select a Duffel flight to see its airport route here.'}</p></div>}
  </section>
}
