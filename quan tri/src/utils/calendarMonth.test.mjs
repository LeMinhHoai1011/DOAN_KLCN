import test from 'node:test'
import assert from 'node:assert/strict'
import { monthGrid, shiftMonth, todayInVietnam } from './calendarMonth.ts'

test('weeks start on Monday and the grid covers the whole month', () => {
  const october = monthGrid(2026, 10) // 1/10/2026 is a Thursday
  assert.equal(october.length, 42)
  assert.equal(october.findIndex(cell => cell.iso === '2026-10-01'), 3)
  assert.equal(october[0].iso, '2026-09-28')
  assert.equal(october.filter(cell => cell.inMonth).length, 31)
})

test('leap February and month/year rollover', () => {
  assert.equal(monthGrid(2024, 2).filter(cell => cell.inMonth).length, 29)
  assert.deepEqual(shiftMonth(2026, 12, 1), { year: 2027, month: 1 })
  assert.deepEqual(shiftMonth(2026, 1, -1), { year: 2025, month: 12 })
})

test('today follows Vietnam time even late in the UTC day', () => {
  assert.equal(todayInVietnam(new Date('2026-10-31T18:30:00Z')), '2026-11-01')
  assert.equal(todayInVietnam(new Date('2026-10-31T16:00:00Z')), '2026-10-31')
})
