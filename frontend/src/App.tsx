import { useState } from 'react'
import './App.css'
import { AppShell, type View } from './components/AppShell'
import { useSession } from './hooks/useSession'
import { useTrips } from './hooks/useTrips'
import { AuthPage } from './pages/AuthPage'
import { CoordinatorDashboard, TravelerDashboard } from './pages/Dashboards'
import { TripEditor } from './pages/TripEditor'
import { CreateTripPage } from './pages/CreateTripPage'
import { TripWorkspace } from './pages/TripWorkspace'
import { api } from './services/api'
import type { Trip, TripDraft } from './types'

export default function App() {
  const { session, checking, expired, authenticate, logout } = useSession()
  const { trips, loading, error: loadError, refresh } = useTrips(Boolean(session))
  const [view, setView] = useState<View>('overview')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editing, setEditing] = useState<Trip | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [notice, setNotice] = useState('')
  function openTrip(id: number) { setSelectedId(id); setView('trips') }
  function openEditor(trip: Trip) { setEditing(trip); setSaveError(''); setEditorOpen(true) }
  function openCreate() { setSaveError(''); setView('create') }
  async function saveTrip(draft: TripDraft, id?: number) {
    setSaving(true); setSaveError('')
    try {
      const saved = await api<Trip>(id ? '/trips/' + id : '/trips', { method: id ? 'PUT' : 'POST', body: JSON.stringify(draft) })
      await refresh(); setEditorOpen(false); setEditing(null); setSelectedId(saved.id); setView('trips'); setNotice(id ? 'Trip updated.' : 'Trip created.')
    } catch (cause) { setSaveError(cause instanceof Error ? cause.message : 'Could not save trip.') }
    finally { setSaving(false) }
  }
  if (checking) return <div className="boot-screen" role="status">Loading TripShield…</div>
  if (!session) return <AuthPage onAuthenticate={authenticate} expired={expired}/>
  return <AppShell session={session} view={view} setView={setView} onLogout={() => void logout()} onNewTrip={openCreate}>{notice && <div className="alert alert--success" role="status">{notice}</div>}{loadError && <div className="alert alert--error" role="alert">{loadError} <button onClick={() => void refresh().catch(() => {})}>Retry</button></div>}{loading && <div className="loading-bar" role="status">Loading trips…</div>}{view === 'create' ? <CreateTripPage onCancel={() => setView('trips')} onSave={saveTrip} busy={saving} error={saveError}/> : view === 'overview' ? session.role === 'COORDINATOR' ? <CoordinatorDashboard trips={trips} onOpen={openTrip} onTrips={() => setView('trips')} onNewTrip={openCreate}/> : <TravelerDashboard trips={trips} onOpen={openTrip} onTrips={() => setView('trips')}/> : <TripWorkspace session={session} trips={trips} selectedId={selectedId} onSelect={setSelectedId} onRefresh={async () => { await refresh() }} onEdit={openEditor}/ >}{editorOpen && <TripEditor trip={editing} onClose={() => setEditorOpen(false)} onSave={saveTrip} busy={saving} error={saveError}/>}</AppShell>
}
