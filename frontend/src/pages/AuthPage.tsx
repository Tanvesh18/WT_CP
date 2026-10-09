import { useState, type FormEvent } from 'react'
import { Button } from '../components/ui'
import { ApiError } from '../services/api'

type Mode = 'login' | 'register'
export function AuthPage({ onAuthenticate, expired }: { onAuthenticate: (mode: Mode, details: { name: string; email: string; password: string }) => Promise<void>; expired: boolean }) {
  const [mode, setMode] = useState<Mode>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setFields({})
    if (mode === 'register' && password !== confirm) { setFields({ confirm: 'Passwords do not match.' }); return }
    setBusy(true)
    try { await onAuthenticate(mode, { name: name.trim(), email: email.trim(), password }) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not sign in.'); if (cause instanceof ApiError) setFields(cause.fieldErrors) }
    finally { setBusy(false) }
  }
  function switchMode() { setMode(mode === 'login' ? 'register' : 'login'); setError(''); setFields({}); setPassword(''); setConfirm('') }
  return <main className="auth"><div className="auth__brand"><div className="brand-mark">T<span>✦</span></div><strong>TripShield</strong></div><div className="auth__layout"><section className="auth__story"><span className="eyebrow">CORPORATE TRAVEL OPERATIONS</span><h1>Travel plans change.<br/>Stay in control.</h1><p>One clear view of every journey, disruption, and next step. Built for the people who travel and the teams who support them.</p><div className="auth__story-footer"><span>01 / Plan</span><span>02 / Monitor</span><span>03 / Recover</span></div></section><section className="auth__card"><span className="eyebrow">SECURE ACCESS</span><h2>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h2><p>{mode === 'login' ? 'Sign in to continue to your workspace.' : 'Traveler accounts can be created here. Coordinators are provisioned by your organization.'}</p>{(error || expired) && <div className="alert alert--error" role="alert">{error || 'Your session ended. Please sign in again.'}</div>}<form onSubmit={submit} noValidate={false}>{mode === 'register' && <label>Full name<input autoComplete="name" required minLength={2} maxLength={120} value={name} onChange={e => setName(e.target.value)} aria-invalid={Boolean(fields.name)} />{fields.name && <small className="field-error">{fields.name}</small>}</label>}<label>Email address<input type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} aria-invalid={Boolean(fields.email)} />{fields.email && <small className="field-error">{fields.email}</small>}</label><label>Password<input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'register' ? 8 : undefined} value={password} onChange={e => setPassword(e.target.value)} aria-invalid={Boolean(fields.password)} />{fields.password && <small className="field-error">{fields.password}</small>}</label>{mode === 'register' && <label>Confirm password<input type="password" autoComplete="new-password" required value={confirm} onChange={e => setConfirm(e.target.value)} aria-invalid={Boolean(fields.confirm)} />{fields.confirm && <small className="field-error">{fields.confirm}</small>}</label>}<Button variant="primary" type="submit" disabled={busy} className="full-width">{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create traveler account'}</Button></form><div className="auth__switch">{mode === 'login' ? 'New to TripShield?' : 'Already have an account?'} <button type="button" onClick={switchMode}>{mode === 'login' ? 'Create an account' : 'Sign in'}</button></div></section></div></main>
}
