import { useCallback, useEffect, useState } from 'react'
import { api } from '../services/api'
import type { Trip } from '../types'

export function useTrips(enabled: boolean) {
  const [trips, setTrips] = useState<Trip[]>([])
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')
  const refresh = useCallback(async () => {
    setLoading(true); setError('')
    try { const data = await api<Trip[]>('/trips'); setTrips(data); return data }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not load trips'); throw cause }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { if (enabled) { void Promise.resolve().then(refresh).catch(() => {}) } }, [enabled, refresh])
  return { trips, loading, error, refresh }
}
