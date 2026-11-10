import test from 'node:test'
import assert from 'node:assert/strict'
import { hasItemDiscount } from './itemDiscount.ts'

test('shows the discount column only when a row has a discount', () => {
  assert.equal(hasItemDiscount([{ discountAmount: null }, {}]), false)
  assert.equal(hasItemDiscount([{ discountAmount: 0 }]), false)
  assert.equal(hasItemDiscount([{ discountAmount: null }, { discountAmount: 177778 }]), true)
})
