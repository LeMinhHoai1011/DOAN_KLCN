import test from 'node:test'
import assert from 'node:assert/strict'
import {
  approvalBlockReason,
  reconciliationRoundingNote,
  reconciliationCalculation,
  reconciliationDifferenceCalculation,
  hasInsufficientReconciliationData,
  latestReconciliationRun,
  reconciliationDecision,
  withoutErrorCode,
  reconciliationMoney,
  reconciliationStatusLabel,
  reconciliationRuleLabel,
  reconciliationRuleDescription,
  reconciliationCommentLabel,
} from './reconciliationPresentation.ts'

test('unknown reconciliation amounts render as em dash instead of zero', () => {
  assert.equal(reconciliationMoney(null), '—')
  assert.equal(reconciliationMoney(undefined), '—')
  assert.equal(reconciliationMoney(0), '0')
})

test('maps every amount and VAT rule for six products and arbitrary row numbers', () => {
  for (const n of [1, 2, 3, 4, 5, 6, 27, 123]) {
    assert.equal(reconciliationRuleLabel(`ITEM_${n}_AMOUNT`), `Thành tiền sản phẩm ${n}`)
    assert.equal(reconciliationRuleLabel(`ITEM_${n}_VAT`), `Thuế GTGT sản phẩm ${n}`)
  }
  assert.equal(reconciliationRuleLabel('SUBTOTAL'), 'Tổng tiền trước thuế')
  assert.equal(reconciliationRuleLabel('VAT_TOTAL'), 'Tổng thuế GTGT')
  assert.equal(reconciliationRuleLabel('TOTAL'), 'Tổng tiền thanh toán')
})

test('unknown and missing codes use Vietnamese fallbacks without exposing codes', () => {
  for (const code of ['UNKNOWN_123', 'FUTURE_CHECK', null, undefined, '']) {
    assert.equal(reconciliationRuleLabel(code), 'Kiểm tra đối soát bổ sung')
    assert.equal(reconciliationRuleDescription(code), 'Kiểm tra tính nhất quán của dữ liệu hóa đơn/chứng từ.')
  }
  assert.equal(reconciliationStatusLabel('FUTURE_STATUS'), 'Chưa xác định')
})

test('all requested statuses are presentation aliases only', () => {
  assert.equal(reconciliationStatusLabel('MATCH'), 'Khớp')
  assert.equal(reconciliationStatusLabel('MISMATCH'), 'Sai lệch')
  assert.equal(reconciliationStatusLabel('PASS'), 'Đạt')
  assert.equal(reconciliationStatusLabel('FAIL'), 'Không đạt')
})

test('each rule uses its approved Vietnamese explanation', () => {
  assert.equal(reconciliationRuleDescription('ITEM_6_AMOUNT'), 'Kiểm tra thành tiền bằng số lượng nhân với đơn giá.')
  assert.equal(reconciliationRuleDescription('ITEM_6_VAT'), 'Kiểm tra tiền thuế GTGT theo thành tiền và thuế suất.')
  assert.equal(reconciliationRuleDescription('SUBTOTAL'), 'So sánh tổng thành tiền các sản phẩm với tổng tiền trước thuế.')
  assert.equal(reconciliationRuleDescription('VAT_TOTAL'), 'So sánh tổng thuế GTGT của các sản phẩm với tổng thuế trên hóa đơn, hỗ trợ nhiều mức thuế suất.')
  assert.equal(reconciliationRuleDescription('TOTAL'), 'Kiểm tra tổng tiền thanh toán bằng tổng tiền trước thuế cộng thuế GTGT.')
})

test('technical generated comments become readable notes, never raw field names', () => {
  const comment = 'ITEM_1_AMOUNT: INSUFFICIENT_DATA; Thành tiền dòng = số lượng × đơn giá; cần quantity, unitPrice và amount; rounding=HALF_UP scale=2'
  assert.equal(reconciliationCommentLabel(comment), 'Chưa đủ dữ liệu để đối soát. Làm tròn đến 2 chữ số thập phân.')
  assert.equal(reconciliationCommentLabel(null), null)
  assert.equal(reconciliationCommentLabel(''), null)
  assert.equal(reconciliationCommentLabel('rounding=HALF_UP scale=2'), 'Làm tròn đến 2 chữ số thập phân')
})

test('manual comments remain readable while embedded rule/status codes are translated', () => {
  assert.equal(reconciliationCommentLabel('ITEM_6_VAT: Kế toán đã kiểm tra lại, cần bổ sung tiền thuế.'), 'Thuế GTGT sản phẩm 6: Kế toán đã kiểm tra lại, cần bổ sung tiền thuế.')
  assert.equal(reconciliationCommentLabel('TOTAL: Manual decision; PASS'), 'Tổng tiền thanh toán: Quyết định kiểm tra thủ công; Đạt')
  assert.equal(reconciliationCommentLabel('FUTURE_CHECK: FAIL'), 'Nội dung đối soát bổ sung: Không đạt')
})

test('only the newest run is shown; legacy rows without a batch stay together', () => {
  const row = (id, batchId) => ({ id, batchId })
  assert.deepEqual(latestReconciliationRun([row(3, 2), row(4, 2), row(1, 1)]).map(item => item.id), [3, 4])
  assert.deepEqual(latestReconciliationRun([]), [])
  assert.equal(latestReconciliationRun([row(1, null), row(2, null)]).length, 2)
})

test('decision actions depend on the current status', () => {
  assert.equal(reconciliationDecision('MATCHED'), null)
  assert.equal(reconciliationDecision('ACCEPTED'), 'REOPEN')
  for (const status of ['MISMATCH', 'INSUFFICIENT_DATA', 'NEED_REVIEW']) assert.equal(reconciliationDecision(status), 'ACCEPT')
  assert.equal(reconciliationStatusLabel('ACCEPTED'), 'Đã chấp nhận')
})

test('approval explanation mirrors the backend gate', () => {
  const state = (reconciliation, reconciliationStale = false) => ({ reconciliation, reconciliationStale })
  assert.equal(approvalBlockReason(state('MATCHED'), true), null)
  assert.equal(approvalBlockReason(state('ACCEPTED'), true), null)
  assert.equal(approvalBlockReason(state('NOT_RUN'), false), null)
  assert.equal(approvalBlockReason(null, true), null)
  assert.match(approvalBlockReason(state('NOT_RUN'), true), /Cần chạy đối soát/)
  assert.match(approvalBlockReason(state('WARNING', true), true), /chạy lại/)
  assert.match(approvalBlockReason(state('MISMATCH'), true), /chưa xử lý/)
  assert.match(approvalBlockReason(state('WARNING'), true), /chưa xử lý/)
})

test('machine codes are removed from server messages', () => {
  assert.equal(withoutErrorCode('RECONCILIATION_REQUIRED: Cần chạy đối soát'), 'Cần chạy đối soát')
  assert.equal(withoutErrorCode('Lý do: thiếu dữ liệu'), 'Lý do: thiếu dữ liệu')
})

test('distinguishes matched mismatch and insufficient data', () => {
  assert.equal(reconciliationStatusLabel('MATCHED'), 'Khớp')
  assert.equal(reconciliationStatusLabel('MISMATCH'), 'Sai lệch')
  assert.equal(reconciliationStatusLabel('INSUFFICIENT_DATA'), 'Chưa đủ dữ liệu')
  assert.equal(hasInsufficientReconciliationData(['MATCHED', 'INSUFFICIENT_DATA']), true)
})

test('reconciliation calculation explains the invoice from the screenshot and hides stale formulas', () => {
  const invoice = { subtotal: 3008239, vatAmount: 240659, items: [{ quantity: 12.63, unitPrice: 238182, taxRate: 0.08, taxAmount: 240659, amount: 3008239 }] }
  const check = (type, expectedAmount, actualAmount) => ({ type, expectedAmount, actualAmount, differenceAmount: Math.round((actualAmount - expectedAmount) * 100) / 100 })
  const amount = check('ITEM_1_AMOUNT', 3008239, 3008239)
  assert.equal(reconciliationCalculation(amount, invoice), '12,63 × 238.182 = 3.008.238,66 → làm tròn 3.008.239 đ')
  assert.equal(reconciliationDifferenceCalculation(amount), '3.008.239 − 3.008.239 = 0 đ')
  const decimals = check('ITEM_1_AMOUNT', 100.5, 101)
  assert.match(reconciliationRoundingNote({ ...decimals, status: 'MATCHED' }), /dưới 1 đ/)
  assert.equal(reconciliationRoundingNote({ ...decimals, status: 'MISMATCH' }), null)
  assert.equal(reconciliationRoundingNote({ ...amount, status: 'MATCHED' }), null)
  assert.equal(reconciliationCommentLabel('Thành tiền dòng = số lượng × đơn giá; rounding=HALF_UP scale=0'), 'Làm tròn đến đồng: phần lẻ từ 0,5 trở lên được làm tròn lên.')
  assert.equal(reconciliationCalculation(check('ITEM_1_VAT', 240659, 240659), invoice), '3.008.239 × 8% = 240.659,12 → làm tròn 240.659 đ')
  assert.equal(reconciliationCalculation(check('TOTAL', 3248898, 3248898), invoice), '3.008.239 + 240.659 = 3.248.898 đ')
  assert.equal(reconciliationCalculation(check('SUBTOTAL', 3008239, 3008239), invoice), '3.008.239 = 3.008.239 đ')
  assert.equal(reconciliationCalculation(check('ITEM_1_AMOUNT', 3008239, 999), invoice), null)
  assert.equal(reconciliationCalculation(amount, null), null)
  assert.equal(reconciliationCalculation(check('FUTURE_RULE', 1, 1), invoice), null)
})
