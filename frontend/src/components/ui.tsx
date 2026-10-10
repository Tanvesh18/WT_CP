import { useEffect, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react'
import type { Trip } from '../types'

export function Button({ variant = 'secondary', className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'quiet' | 'danger' }) {
  return <button className={'button button--' + variant + ' ' + className} {...props} />
}
export function StatusBadge({ status }: { status: Trip['status'] }) {
  const labels = { ON_TRACK: 'On track', NEEDS_ATTENTION: 'Needs attention', REQUESTED: 'Awaiting review', CANCELLED: 'Cancelled' }
  return <span className={'status status--' + status.toLowerCase()}>{labels[status]}</span>
}
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="empty-state"><span className="empty-state__icon" aria-hidden="true">◎</span><h3>{title}</h3><p>{children}</p></div>
}
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const closeRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const before = document.activeElement as HTMLElement | null
    closeRef.current?.focus()
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('keydown', onKey); before?.focus() }
  }, [onClose])
  return <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}><section className="modal" role="dialog" aria-modal="true" aria-label={title}><header className="modal__header"><h2>{title}</h2><button ref={closeRef} type="button" className="icon-button" onClick={onClose} aria-label="Close dialog">×</button></header>{children}</section></div>
}
