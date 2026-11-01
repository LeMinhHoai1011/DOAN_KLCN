import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

export default function FormModal({ title, busy, onClose, children }: {
  title: string
  busy: boolean
  onClose: () => void
  children: ReactNode
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const element = dialog.current
    const previousOverflow = document.body.style.overflow
    element?.showModal()
    document.body.style.overflow = 'hidden'
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
    }
  }, [])

  return <dialog ref={dialog} aria-labelledby={titleId} aria-busy={busy}
    className="si-panel fixed inset-0 m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto p-0 text-ink backdrop:bg-ink/40"
    onCancel={event => { event.preventDefault(); if (!busy) onClose() }}
    onClick={event => {
      if (event.target !== event.currentTarget || busy) return
      const bounds = event.currentTarget.getBoundingClientRect()
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose()
    }}>
    <div className="border-hairline flex items-center justify-between border-b px-5 py-3.5">
      <h2 id={titleId} className="si-section-title">{title}</h2>
      <button type="button" disabled={busy} onClick={onClose} aria-label="Đóng hộp thoại"
        className="text-ink-muted hover:bg-subtle hover:text-ink rounded p-1.5 transition-colors"><X size={22} /></button>
    </div>
    {children}
  </dialog>
}
