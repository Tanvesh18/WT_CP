import { useEffect, useState } from 'react'
import { api, clearLegacyToken } from '../services/api'
import type { Session } from '../types'

export function useSession() {
  const [session, setSession] = useState<Session | null>(null)
  const [checking, setChecking] = useState(true)
  const [expired, setExpired] = useState(false)
  useEffect(() => {
    let active = true
    clearLegacyToken()
    api<Session>('/auth/me').then(user => {
      if (active) setSession(user)
    }).catch(() => { if (active) setSession(null) }).finally(() => { if (active) setChecking(false) })
    const onExpired = () => { setSession(null); setExpired(true) }
    window.addEventListener('tripshield-session-expired', onExpired)
    return () => { active = false; window.removeEventListener('tripshield-session-expired', onExpired) }
  }, [])
  async function authenticate(mode: 'login' | 'register', details: { name: string; email: string; password: string }) {
    const user = await api<Session>('/auth/' + mode, { method: 'POST', body: JSON.stringify(details) })
    setSession(user); setExpired(false)
  }
  async function logout() {
    try { await api('/auth/logout', { method: 'POST' }) } finally { setSession(null) }
  }
  return { session, checking, expired, authenticate, logout }
}
