import { useEffect, useState } from 'react'
import { evaluationService, type AiChange, type DocumentAiChanges } from '../services/evaluationService'
import { aiChangeKindLabel, aiChangeLabel, aiChangeSummary, aiChangeValue } from '../utils/aiChangePresentation'

export const AiChangeTable = ({ changes }: { changes: AiChange[] }) => <div className="overflow-x-auto"><table className="w-full text-left text-sm">
  <thead><tr className="text-xs text-ink-muted"><th className="py-1 pr-3 font-medium">Trường</th><th className="pr-3 font-medium">Lỗi</th><th className="pr-3 font-medium">AI trả về</th><th className="font-medium">Kế toán chốt</th></tr></thead>
  <tbody>{changes.map(change => <tr key={`${change.field}-${change.row}`} className="border-t border-hairline align-top">
    <td className="py-1.5 pr-3">{aiChangeLabel(change)}</td><td className="pr-3 text-review">{aiChangeKindLabel[change.kind]}</td>
    <td className="break-words pr-3 text-ink-muted">{aiChangeValue(change.aiValue)}</td><td className="break-words font-medium">{aiChangeValue(change.finalValue)}</td>
  </tr>)}</tbody>
</table></div>

/** What the accountant changed on this document; hidden until there is an AI result to compare with. */
export default function AiChangesCard({ documentId, refreshKey }: { documentId: number; refreshKey: string }) {
  const [data, setData] = useState<DocumentAiChanges | null>(null)
  useEffect(() => {
    let active = true
    evaluationService.documentAiChanges(documentId).then(next => { if (active) setData(next) }).catch(() => { if (active) setData(null) })
    return () => { active = false }
  }, [documentId, refreshKey])
  if (!data?.hasAiResult) return null
  return <section className="si-card p-4">
    <h3 className="si-section-title mb-2">Kế toán đã thay đổi so với AI</h3>
    <p className="text-sm text-ink-muted">{aiChangeSummary(data.compared, data.correct, data.changes.length, data.approved)}</p>
    {data.changes.length > 0 && <div className="mt-3"><AiChangeTable changes={data.changes} /></div>}
  </section>
}
