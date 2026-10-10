import coordinateData from './airportCoordinates.json'

export type AirportPoint = { code: string; name: string; lat: number; lon: number }
type AirportStop = { code: string; name?: string; latitude?: number | null; longitude?: number | null }
type RouteLeg = { origin: string; destination: string; originName?: string; destinationName?: string; originLatitude?: number | null; originLongitude?: number | null; destinationLatitude?: number | null; destinationLongitude?: number | null; technicalStops?: AirportStop[] }
const coordinates = coordinateData as unknown as Record<string, [number, number]>
const codeOf = (value: string | undefined) => (value || '').trim().toUpperCase()
const valid = (lat: number, lon: number) => Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180 && !(lat === 0 && lon === 0)

function point(codeValue: string | undefined, name: string | undefined, latitude: number | null | undefined, longitude: number | null | undefined): AirportPoint | null {
  const code = codeOf(codeValue)
  if (!/^[A-Z]{3}$/.test(code)) return null
  const reference = coordinates[code]
  const lat = reference ? reference[0] : latitude
  const lon = reference ? reference[1] : longitude
  if (typeof lat !== 'number' || typeof lon !== 'number' || !valid(lat, lon)) return null
  return { code, name: name || code, lat, lon }
}

function expectedCodes(location: string | null | undefined): [string, string] | null {
  const match = location?.trim().toUpperCase().match(/^([A-Z]{3}) *→ *([A-Z]{3})$/)
  return match ? [match[1], match[2]] : null
}

export function routePoints(json: string | null | undefined, location?: string | null): AirportPoint[] {
  const expected = expectedCodes(location)
  const fallback = () => {
    if (!expected) return []
    const start = point(expected[0], undefined, null, null)
    const end = point(expected[1], undefined, null, null)
    return start && end ? [start, end] : []
  }
  if (!json) return fallback()
  try {
    const legs = JSON.parse(json) as RouteLeg[]
    if (!Array.isArray(legs) || !legs.length) return fallback()
    const firstCode = codeOf(legs[0].origin)
    const lastCode = codeOf(legs[legs.length - 1].destination)
    if (expected && (firstCode !== expected[0] || lastCode !== expected[1])) return fallback()
    for (let index = 1; index < legs.length; index++) {
      if (codeOf(legs[index - 1].destination) !== codeOf(legs[index].origin)) return fallback()
    }
    const points: AirportPoint[] = []
    const add = (candidate: AirportPoint | null) => { if (candidate && points.at(-1)?.code !== candidate.code) points.push(candidate) }
    for (const leg of legs) {
      add(point(leg.origin, leg.originName, leg.originLatitude, leg.originLongitude))
      for (const stop of leg.technicalStops || []) add(point(stop.code, stop.name, stop.latitude, stop.longitude))
      add(point(leg.destination, leg.destinationName, leg.destinationLatitude, leg.destinationLongitude))
    }
    if (points.length < 2 || points[0].code !== firstCode || points.at(-1)?.code !== lastCode) return fallback()
    return points
  } catch { return fallback() }
}
