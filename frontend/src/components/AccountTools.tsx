import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../services/api'
import type { Session } from '../types'
import { Button } from './ui'

type Notice = { id: number; tripId: number; type: string; message: string; createdAt: string; readAt: string | null }
type Feed = { items: Notice[]; unreadCount: number }

export function AccountTools({ session, onClaim }: { session: Session; onClaim: () => Promise<void> }) {
  const [feed, setFeed] = useState<Feed | null>(null)
  const [error, setError] = useState('')
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  async function load() {
    try { setFeed(await api<Feed>('/notifications')); setError('') }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not load notifications') }
  }
  useEffect(() => {
    let active = true
    api<Feed>('/notifications').then(data => { if (active) setFeed(data) })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : 'Could not load notifications') })
    return () => { active = false }
  }, [])
  async function markRead(id: number) {
    try { await api('/notifications/' + id + '/read', { method: 'POST' }); await load() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not update notification') }
  }
  async function claim(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try { await api('/auth/claim-trip', { method: 'POST', body: JSON.stringify({ code }) }); setCode(''); await onClaim() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not claim trip') }
    finally { setBusy(false) }
  }
  return <section className="surface account-tools" aria-label="Account updates">
    {session.role === 'TRAVELER' && <form onSubmit={event => void claim(event)}><label>Claim an invited guest trip<input value={code} onChange={event => setCode(event.target.value)} placeholder="Single-use invitation code" required /></label><Button type="submit" disabled={busy}>{busy ? 'Claiming…' : 'Claim trip'}</Button></form>}
    <div className="section-head"><h3>Notifications {feed && <span className="count-pill">{feed.unreadCount} unread</span>}</h3><Button variant="quiet" onClick={() => void load()}>Refresh</Button></div>
    {error && <p className="alert alert--error" role="alert">{error}</p>}
    {!feed && !error && <p role="status">Loading notifications…</p>}
    {feed?.items.length === 0 && <p className="muted">No notifications yet.</p>}
    {feed?.items.map(item => <div className={'notification-row' + (item.readAt ? '' : ' notification-row--unread')} key={item.id}><div><strong>{item.type.replaceAll('_', ' ')}</strong><p>{item.message}</p><small>Trip #{item.tripId} · {new Date(item.createdAt).toLocaleString()}</small></div>{!item.readAt && <Button variant="quiet" onClick={() => void markRead(item.id)}>Mark read</Button>}</div>)}
  </section>
}
