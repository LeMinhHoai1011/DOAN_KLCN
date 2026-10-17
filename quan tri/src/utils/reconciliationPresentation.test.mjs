import test from 'node:test'
import assert from 'node:assert/strict'
import {
  hasInsufficientReconciliationData,
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

test('distinguishes matched mismatch and insufficient data', () => {
  assert.equal(reconciliationStatusLabel('MATCHED'), 'Khớp')
  assert.equal(reconciliationStatusLabel('MISMATCH'), 'Sai lệch')
  assert.equal(reconciliationStatusLabel('INSUFFICIENT_DATA'), 'Chưa đủ dữ liệu')
  assert.equal(hasInsufficientReconciliationData(['MATCHED', 'INSUFFICIENT_DATA']), true)
})
