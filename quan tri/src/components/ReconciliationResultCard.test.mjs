import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile, writeFile, mkdtemp, rm } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import ts from 'typescript'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

test('renders a six-product invoice with matches and mismatches entirely in Vietnamese, preserving API values', async () => {
  const cacheRoot = fileURLToPath(new URL('../../node_modules/', import.meta.url))
  const temporary = await mkdtemp(path.join(cacheRoot, '.reconciliation-render-'))
  try {
    const source = await readFile(new URL('./ReconciliationResultCard.tsx', import.meta.url), 'utf8')
    const helper = pathToFileURL(fileURLToPath(new URL('../utils/reconciliationPresentation.ts', import.meta.url))).href
    const output = ts.transpileModule(source, { compilerOptions: { jsx: ts.JsxEmit.ReactJSX, module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
      .replace(/(['"])\.\.\/utils\/reconciliationPresentation\1/g, JSON.stringify(helper))
    const modulePath = path.join(temporary, 'card.mjs')
    await writeFile(modulePath, output)
    const { default: Card } = await import(pathToFileURL(modulePath).href)
    const items = []
    for (let n = 1; n <= 6; n++) for (const suffix of ['AMOUNT', 'VAT']) {
      const type = `ITEM_${n}_${suffix}`
      items.push({ id: items.length + 1, type, expectedAmount: 1000 * n, actualAmount: 1000 * n + (n === 4 ? 10 : 0), differenceAmount: n === 4 ? 10 : 0, status: n === 4 ? 'MISMATCH' : n === 2 ? 'PASS' : 'MATCH', comment: `${type}: ${n === 4 ? 'FAIL' : 'PASS'}; rounding=HALF_UP scale=2` })
    }
    for (const type of ['SUBTOTAL', 'VAT_TOTAL', 'TOTAL']) items.push({ id: items.length + 1, type, expectedAmount: 21000, actualAmount: 21010, differenceAmount: 10, status: 'MISMATCH', comment: `${type}: rounding=HALF_UP scale=2` })
    const before = JSON.stringify(items)
    const html = renderToStaticMarkup(React.createElement('div', null, ...items.map(item => React.createElement(Card, { item, key: item.id }))))
    for (let n = 1; n <= 6; n++) {
      assert.ok(html.includes(`Thành tiền sản phẩm ${n}`))
      assert.ok(html.includes(`Thuế GTGT sản phẩm ${n}`))
    }
    for (const label of ['Khớp', 'Sai lệch', 'Đạt', 'Không đạt', 'Giá trị hóa đơn', 'Giá trị tính lại', 'Chênh lệch', 'Tổng tiền trước thuế', 'Tổng thuế GTGT', 'Tổng tiền thanh toán', 'Làm tròn đến 2 chữ số thập phân']) assert.ok(html.includes(label), label)
    assert.doesNotMatch(html, /ITEM_\d+_(AMOUNT|VAT)|\b(SUBTOTAL|VAT_TOTAL|TOTAL|MATCH|MISMATCH|PASS|FAIL)\b|rounding=|HALF_UP|scale=2|Giá trị gốc/)
    assert.equal((html.match(/<section /g) ?? []).length, 15)
    assert.ok(html.includes('1.000 đ'))
    assert.equal(JSON.stringify(items), before)
    const missing = renderToStaticMarkup(React.createElement(Card, { item: { id: 99, type: 'FUTURE_RULE', status: 'FUTURE_STATUS', expectedAmount: null, actualAmount: null, differenceAmount: null, comment: null } }))
    assert.ok(missing.includes('Kiểm tra đối soát bổ sung')); assert.ok(missing.includes('Chưa xác định')); assert.ok(missing.includes('—')); assert.doesNotMatch(missing, /FUTURE_RULE|FUTURE_STATUS/)
  } finally {
    // Delete only the exact temporary directory created by this test under node_modules.
    if (!path.resolve(temporary).startsWith(path.resolve(cacheRoot) + path.sep)) throw new Error('Invalid cleanup path')
    await rm(temporary, { recursive: true, force: true })
  }
})
