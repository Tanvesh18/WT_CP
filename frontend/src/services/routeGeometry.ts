export type AirportPoint = { code: string; name: string; lat: number; lon: number }
type RouteLeg = { origin: string; destination: string; originName: string; destinationName: string; originLatitude: number | null; originLongitude: number | null; destinationLatitude: number | null; destinationLongitude: number | null; technicalStops?: { code: string; name: string; latitude: number | null; longitude: number | null }[] }
export function routePoints(json: string | null | undefined): AirportPoint[] {
  if (!json) return []
  try {
    const legs = JSON.parse(json) as RouteLeg[]
    if (!Array.isArray(legs) || !legs.length) return []
    const points: AirportPoint[] = []
    for (const [index, leg] of legs.entries()) {
      if (index === 0 && Number.isFinite(leg.originLatitude) && Number.isFinite(leg.originLongitude)) points.push({ code: leg.origin, name: leg.originName || leg.origin, lat: Number(leg.originLatitude), lon: Number(leg.originLongitude) })
      for (const stop of leg.technicalStops || []) if (Number.isFinite(stop.latitude) && Number.isFinite(stop.longitude)) points.push({ code: stop.code, name: stop.name || stop.code, lat: Number(stop.latitude), lon: Number(stop.longitude) })
      if (Number.isFinite(leg.destinationLatitude) && Number.isFinite(leg.destinationLongitude)) points.push({ code: leg.destination, name: leg.destinationName || leg.destination, lat: Number(leg.destinationLatitude), lon: Number(leg.destinationLongitude) })
    }
    return points.filter(point => Math.abs(point.lat) <= 90 && Math.abs(point.lon) <= 180)
  } catch { return [] }
}
