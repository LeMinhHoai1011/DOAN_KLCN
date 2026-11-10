export type RegionDraftView = {
  extractedText: string; value: string; loading: boolean; saving: boolean; error: string
}

/** Lets the accountant check and edit the text read from a region before it is applied. */
export default function RegionReviewPanel({ draft, dateField, applyLabel, onChange, onApply }: {
  draft: RegionDraftView | null; dateField: boolean; applyLabel: string
  onChange: (value: string) => void; onApply: () => void
}) {
  if (!draft) return null
  const invalidDate = dateField && draft.value.trim() !== '' && !/^\d{4}-\d{2}-\d{2}$/.test(draft.value.trim())
  return <div className="rounded-card border-hairline bg-surface space-y-2 border p-3">
    <div>
      <p className="text-xs font-semibold text-ink-soft">Chữ đọc được trong vùng{draft.loading ? ' (đang đọc…)' : ''}</p>
      <pre className="mt-1 max-h-24 overflow-auto whitespace-pre-wrap break-words rounded-control bg-canvas p-2 text-xs text-ink">
        {draft.extractedText || 'Không có chữ OCR trong vùng này. Hãy kéo lại hoặc nhập tay.'}
      </pre>
    </div>
    <label className="block text-xs font-semibold text-ink-soft">Giá trị sẽ điền
      <input value={draft.value} onChange={event => onChange(event.target.value)} className="si-input mt-1"
        placeholder={dateField ? 'yyyy-mm-dd' : undefined} aria-invalid={invalidDate || undefined} />
    </label>
    {invalidDate && <p className="text-xs text-review">Chưa nhận ra ngày. Hãy sửa theo dạng yyyy-mm-dd, ví dụ 2025-03-05.</p>}
    <div className="flex items-center gap-2">
      <button type="button" onClick={onApply} disabled={!draft.value.trim() || draft.saving || invalidDate}
        className="si-btn-primary text-sm">{draft.saving ? 'Đang lưu…' : applyLabel}</button>
      <span className="text-xs text-ink-muted">Kéo khung khác để chọn lại.</span>
    </div>
    {draft.error && <p role="alert" className="text-xs font-semibold text-failed">{draft.error}</p>}
  </div>
}
