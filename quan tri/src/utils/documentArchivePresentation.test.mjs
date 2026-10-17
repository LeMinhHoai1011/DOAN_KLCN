import test from 'node:test'
import assert from 'node:assert/strict'
import { snapshotChanges, archiveActionLabel, archiveActorLabel, storageStatusLabel, archiveBytes, snapshotValueLabel } from './documentArchivePresentation.ts'
test('history diff preserves values and detects line edits, insertions and missing snapshots',()=>{
  const before={invoice:{totalAmount:100,items:[{productName:'A',amount:100}]}},after={invoice:{totalAmount:120,items:[{productName:'A',amount:120},{productName:'B',amount:1}]}}
  const original=JSON.stringify(before), changes=snapshotChanges(before,after)
  assert.ok(changes.some(c=>c.path==='invoice.totalAmount'&&c.before==='100'&&c.after==='120'))
  assert.ok(changes.some(c=>c.path==='invoice.items.1.productName'&&c.before==='—'&&c.after==='B'))
  assert.equal(JSON.stringify(before),original);assert.equal(snapshotChanges(before,before).length,0)
  assert.ok(snapshotChanges(null,after).some(c=>c.path==='invoice.totalAmount'&&c.after==='120'))
})
test('storage unavailable, missing object and absent checksum are distinct from an available file',()=>{
  assert.match(storageStatusLabel('MISSING'),/Không tìm thấy/);assert.match(storageStatusLabel('UNAVAILABLE'),/kết nối/)
  assert.match(storageStatusLabel('AVAILABLE'),/hiện có/);assert.equal(archiveBytes(null),'Chưa xác định');assert.equal(archiveBytes(0),'0 byte')
})
test('unknown historical actor and unknown events are not invented',()=>{
  assert.equal(archiveActorLabel(null),'Chưa ghi nhận người thực hiện');assert.match(archiveActionLabel('WORKFLOW_APPROVE'),/Phê duyệt/)
  assert.equal(archiveActionLabel('LEGACY_UNKNOWN_EVENT'),'Sự kiện xử lý chứng từ')
})
test('OCR layout is retained by API but omitted from the text diff table',()=>{
  assert.deepEqual(snapshotChanges({ocr:{layoutJson:'old'}},{ocr:{layoutJson:'new'}}),[])
})
test('history status labels are translated without altering original business field values',()=>{
  assert.equal(snapshotValueLabel('reconciliations.0.status','MISMATCH'),'Sai lệch')
  assert.equal(snapshotValueLabel('document.reviewStatus','APPROVED'),'Đã duyệt')
  assert.equal(snapshotValueLabel('invoice.invoiceNumber','MISMATCH'),'MISMATCH')
})
