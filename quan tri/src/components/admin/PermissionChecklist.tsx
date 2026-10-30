import { useEffect, useRef } from 'react'
import type { PermissionGroup } from '../../services/roleService'

function GroupCheckbox({ checked, mixed, disabled, onChange }: {
  checked: boolean; mixed: boolean; disabled: boolean; onChange: (checked: boolean) => void
}) {
  const ref = useRef<HTMLInputElement>(null)
  useEffect(() => { if (ref.current) ref.current.indeterminate = mixed }, [mixed])
  return <input ref={ref} type="checkbox" checked={checked} disabled={disabled}
    onChange={event => onChange(event.target.checked)} className="accent-brand-600" />
}

export default function PermissionChecklist({ groups, selected, disabled, onToggle }: {
  groups: PermissionGroup[]; selected: number[]; disabled: boolean
  onToggle: (ids: number[], checked: boolean) => void
}) {
  return <div className="grid gap-4">{groups.map(group => {
    const editable = group.permissions.filter(p => p.active).map(p => p.id)
    const count = editable.filter(id => selected.includes(id)).length
    return <section key={group.id} className="rounded-card border-hairline border">
      <label className="border-hairline flex items-center gap-3 border-b px-4 py-3 font-semibold">
        <GroupCheckbox checked={editable.length > 0 && count === editable.length}
          mixed={count > 0 && count < editable.length} disabled={disabled || editable.length === 0}
          onChange={checked => onToggle(editable, checked)} />
        {group.name} <span className="text-sm font-normal text-ink-muted">Chọn tất cả ({count}/{editable.length})</span>
      </label>
      <div className="grid gap-2 p-3 md:grid-cols-2">{group.permissions.map(permission =>
        <label key={permission.id} className="flex items-start gap-2 rounded-control p-2 hover:bg-subtle">
          <input type="checkbox" checked={selected.includes(permission.id)} disabled={disabled || !permission.active}
            onChange={event => onToggle([permission.id], event.target.checked)} className="accent-brand-600 mt-1" />
          <span><span className="block font-medium">{permission.name}</span>
            <span className="block text-sm text-ink-muted">{permission.code}{!permission.active && ' · Đã vô hiệu hóa'}</span></span>
        </label>)}</div>
    </section>
  })}</div>
}
