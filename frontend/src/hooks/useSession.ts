import { useEffect, useState } from 'react'
import { api, clearToken, saveToken, storedToken } from '../services/api'
import type { Session } from '../types'

export function useSession() {
  const [session, setSession] = useState<Session | null>(null)
  const [checking, setChecking] = useState(Boolean(storedToken()))
  const [expired, setExpired] = useState(false)
  useEffect(() => {
    let active = true
    if (storedToken()) api<Session>('/auth/me').then(user => {
      if (active) setSession({ ...user, token: storedToken() || '' })
    }).catch(() => { clearToken(); if (active) setExpired(true) }).finally(() => { if (active) setChecking(false) })
    const onExpired = () => { setSession(null); setExpired(true) }
    window.addEventListener('tripshield-session-expired', onExpired)
    return () => { active = false; window.removeEventListener('tripshield-session-expired', onExpired) }
  }, [])
  async function authenticate(mode: 'login' | 'register', details: { name: string; email: string; password: string }) {
    const user = await api<Session>('/auth/' + mode, { method: 'POST', body: JSON.stringify(details) })
    saveToken(user.token); setSession(user); setExpired(false)
  }
  async function logout() {
    try { await api('/auth/logout', { method: 'POST' }) } finally { clearToken(); setSession(null) }
  }
  return { session, checking, expired, authenticate, logout }
}
