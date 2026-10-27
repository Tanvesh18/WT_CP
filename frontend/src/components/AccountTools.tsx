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
  const [open, setOpen] = useState(false)
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
    <div className="account-tools__bar"><div><span className="eyebrow">INBOX</span><h3>Notifications <span className="count-pill">{feed?.unreadCount ?? '…'} unread</span></h3></div><div className="account-tools__actions"><Button variant="quiet" onClick={() => void load()}>Refresh</Button><Button onClick={() => setOpen(value => !value)} aria-expanded={open} aria-controls="notification-feed">{open ? 'Hide updates' : 'View updates'}</Button></div></div>
    {feed && feed.unreadCount > 0 && !open && <p className="account-tools__preview">{feed.items.find(item => !item.readAt)?.message}</p>}
    {error && <p className="alert alert--error" role="alert">{error}</p>}
    {!feed && !error && <p role="status" className="muted">Loading notifications…</p>}
    {open && <div id="notification-feed" className="account-tools__feed">{feed?.items.length === 0 && <p className="muted">No notifications yet.</p>}{feed?.items.map(item => <div className={'notification-row' + (item.readAt ? '' : ' notification-row--unread')} key={item.id}><div><strong>{item.type.replaceAll('_', ' ')}</strong><p>{item.message}</p><small>Trip #{item.tripId} · {new Date(item.createdAt).toLocaleString()}</small></div>{!item.readAt && <Button variant="quiet" onClick={() => void markRead(item.id)}>Mark read</Button>}</div>)}</div>}
    {session.role === 'TRAVELER' && <details className="account-tools__claim"><summary>Have an invitation code? Claim a trip</summary><form onSubmit={event => void claim(event)}><label>Single-use invitation code<input value={code} onChange={event => setCode(event.target.value)} placeholder="Enter invitation code" required /></label><Button type="submit" disabled={busy}>{busy ? 'Claiming…' : 'Claim trip'}</Button></form></details>}
  </section>
}
