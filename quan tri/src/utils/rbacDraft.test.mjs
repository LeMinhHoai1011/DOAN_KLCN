import test from 'node:test'
import assert from 'node:assert/strict'
import { sameSet, toggleValues, isAssignableRole, isCreatableAccountRole, isSystemRole } from './rbacDraft.ts'

test('permission drafts accumulate independent changes and group toggles preserve other groups', () => {
  const baseline = [1, 5]
  const draft = toggleValues(toggleValues(baseline, [2, 3], true), [1], false)
  assert.deepEqual(baseline, [1, 5])
  assert.ok(sameSet(draft, [5, 2, 3]))
  assert.ok(sameSet(toggleValues(draft, [2, 3], false), [5]))
  assert.ok(sameSet(toggleValues(draft, [2, 3], true), draft))
  assert.equal(sameSet(draft, baseline), false)
})
test('set comparison supports unordered multi-role values without changing inputs', () => {
  assert.ok(sameSet([1, 2], [2, 1]))
  assert.ok(sameSet([1, 1], [1]))
  assert.equal(sameSet([1], [1, 2]), false)
})
test('account UI only offers one active accountant or employee role', () => {
  assert.equal(isAssignableRole({ code: 'USER', active: true }), false)
  assert.equal(isAssignableRole({ code: 'CUSTOM', active: false }), false)
  assert.equal(isAssignableRole({ code: 'CUSTOM', active: true }), false)
  assert.equal(isAssignableRole({ code: 'ADMIN', active: true }), false)
  assert.equal(isCreatableAccountRole({ code: 'CUSTOM', active: true }), false)
  assert.equal(isCreatableAccountRole({ code: 'USER', active: true }), false)
  assert.equal(isCreatableAccountRole({ code: 'ADMIN', active: true }), false)
  for (const code of ['ACCOUNTANT', 'EMPLOYEE']) {
    assert.equal(isCreatableAccountRole({ code, active: true }), true)
    assert.equal(isCreatableAccountRole({ code, active: false }), false)
    assert.equal(isSystemRole(code), true)
  }
  assert.equal(isSystemRole('USER'), true)
  assert.equal(isSystemRole('CUSTOM'), false)
})
