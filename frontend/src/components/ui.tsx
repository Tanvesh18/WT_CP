import { useEffect, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react'
import type { Trip } from '../types'

export function Button({ variant = 'secondary', className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'quiet' | 'danger' }) {
  return <button className={'button button--' + variant + ' ' + className} {...props} />
}
export function StatusBadge({ status }: { status: Trip['status'] }) {
  const labels = { ON_TRACK: 'On track', NEEDS_ATTENTION: 'Needs attention', REQUESTED: 'Awaiting review', REJECTED: 'Rejected', CANCELLED: 'Cancelled' }
  return <span className={'status status--' + status.toLowerCase()}>{labels[status]}</span>
}
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="empty-state"><span className="empty-state__icon" aria-hidden="true">◎</span><h3>{title}</h3><p>{children}</p></div>
}
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const closeRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLElement>(null)
  useEffect(() => {
    const before = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeRef.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
      if (event.key !== 'Tab') return
      const controls = [...(dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], summary') || [])]
      if (!controls.length) return
      const first = controls[0], last = controls[controls.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = previousOverflow; before?.focus() }
  }, [onClose])
  return <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}><section ref={dialogRef} className="modal" role="dialog" aria-modal="true" aria-label={title}><header className="modal__header"><h2>{title}</h2><button ref={closeRef} type="button" className="icon-button" onClick={onClose} aria-label="Close dialog">×</button></header>{children}</section></div>
}
