// Run against Vite with mocked APIs; never reads or writes the application database.
// PLAYWRIGHT_MODULE may point to an existing Playwright ESM module outside node_modules.
import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright')
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || 'msedge', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const failures = []
page.on('pageerror', error => failures.push(error.message))
const admin = { id: 1, username: 'admin', fullName: 'Admin', email: 'admin@example.test', role: 'ADMIN', roles: ['ADMIN'], status: 'ACTIVE' }
let roles = [
  { id: 1, code: 'ADMIN', name: 'Administrator', active: true, description: '', permissions: ['DOCUMENT_VIEW'] },
  { id: 2, code: 'EMPLOYEE', name: 'Employee', active: true, description: '', permissions: ['DOCUMENT_VIEW'] },
  { id: 3, code: 'CUSTOM', name: 'Custom role', active: true, description: '', permissions: ['DOCUMENT_VIEW'] },
  { id: 4, code: 'USER', name: 'Historical user', active: true, description: '', permissions: ['DOCUMENT_VIEW'] },
  { id: 5, code: 'INACTIVE', name: 'Inactive role', active: false, description: '', permissions: [] },
  { id: 6, code: 'ACCOUNTANT', name: 'Accountant', active: true, description: '', permissions: ['DOCUMENT_VIEW'] },
]
const permissions = ['VIEW', 'CREATE', 'DOWNLOAD'].map((verb, index) => ({ id: index + 1, code: `DOCUMENT_${verb}`, name: `${verb} documents`, active: true }))
const users = [admin, { id: 2, username: 'staff', fullName: 'Staff', email: 'staff@example.test', role: 'EMPLOYEE', roles: ['EMPLOYEE', 'CUSTOM'], status: 'ACTIVE' }]
let assigned = [2, 3]
const permissionWrites = [], assignmentWrites = []
let failSave = false, delaySave = false, releaseSave
const reply = (route, value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
await page.addInitScript(user => { localStorage.setItem('token', 'mock-only'); localStorage.setItem('user', JSON.stringify(user)) }, admin)
await page.route('**/api/v1/**', async route => {
  const req = route.request(), path = new URL(req.url()).pathname, method = req.method()
  if (path === '/api/v1/users/me') return reply(route, admin)
  if (path === '/api/v1/roles' && method === 'GET') return reply(route, roles)
  if (path === '/api/v1/permissions') return reply(route, permissions)
  if (path === '/api/v1/permissions/groups') return reply(route, [{ id: 1, code: 'DOCUMENT', name: 'Documents', active: true, permissions }])
  if (/\/roles\/\d+\/permissions$/.test(path) && method === 'PUT') {
    const payload = req.postDataJSON(); permissionWrites.push(payload)
    if (delaySave) await new Promise(resolve => { releaseSave = resolve })
    if (failSave) return reply(route, { message: 'Dữ liệu phân quyền đã thay đổi. Hãy tải lại trước khi lưu.' }, 409)
    const id = Number(path.split('/')[4])
    roles = roles.map(r => r.id === id ? { ...r, permissions: permissions.filter(p => payload.permissionIds.includes(p.id)).map(p => p.code) } : r)
    return reply(route, roles.find(r => r.id === id))
  }
  if (path === '/api/v1/roles' && method === 'POST') {
    const role = { ...req.postDataJSON(), id: 10, permissions: [] }; roles.push(role); return reply(route, role, 201)
  }
  if (/\/roles\/\d+$/.test(path) && method === 'PUT') {
    const id = Number(path.split('/').at(-1)); roles = roles.map(r => r.id === id ? { ...r, ...req.postDataJSON() } : r)
    return reply(route, roles.find(r => r.id === id))
  }
  if (/\/roles\/\d+\/copy$/.test(path) && method === 'POST') {
    const source = roles.find(r => r.id === Number(path.split('/')[4]))
    const copy = { ...req.postDataJSON(), id: 11, permissions: [...source.permissions] }; roles.push(copy); return reply(route, copy, 201)
  }
  if (path === '/api/v1/users' && method === 'GET') return reply(route, users)
  if (/\/users\/\d+\/access$/.test(path)) {
    const id = Number(path.split('/')[4]); const ids = id === 1 ? [1] : assigned
    return reply(route, { assignedRoles: roles.filter(r => ids.includes(r.id)), permissions: ['DOCUMENT_VIEW'], adminBypass: id === 1 })
  }
  if (/\/users\/\d+\/roles$/.test(path) && method === 'PUT') {
    const payload = req.postDataJSON(); assignmentWrites.push(payload)
    if (delaySave) await new Promise(resolve => { releaseSave = resolve })
    if (failSave) return reply(route, { message: 'Không thể cập nhật vai trò.' }, 403)
    assigned = payload.roleIds; return reply(route, { ...users[1], roles: roles.filter(r => assigned.includes(r.id)).map(r => r.code) })
  }
  return reply(route, [])
})
const button = name => name === 'Hủy' ? page.getByRole('button', { name, exact: true }).first() : page.getByRole('button', { name, exact: true })
const check = name => page.getByRole('checkbox', { name: new RegExp(`^${name} documents`) })
const waitForNotice = text => page.getByRole('status').filter({ hasText: text }).waitFor()
try {
  await page.goto(`${process.env.UI_BASE_URL || 'http://127.0.0.1:5173'}/admin/roles`)
  await page.getByRole('button', { name: /^Custom role/ }).click()
  await check('CREATE').check(); await check('DOWNLOAD').check()
  assert.equal(permissionWrites.length, 0, 'ticks never write immediately')
  await button('Hủy').click()
  assert.equal(await check('CREATE').isChecked(), false)
  assert.equal(permissionWrites.length, 0)
  await page.getByRole('checkbox', { name: /^Documents Chọn tất cả/ }).check()
  assert.equal(await check('DOWNLOAD').isChecked(), true)
  delaySave = true
  await button('Lưu thay đổi').click()
  await page.waitForFunction(() => document.querySelector('[aria-busy="true"]'))
  assert.equal(await check('CREATE').isDisabled(), true)
  assert.equal(await button('Đang xử lý…').isDisabled(), true)
  while (!releaseSave) await new Promise(resolve => setTimeout(resolve, 10))
  releaseSave(); delaySave = false
  await waitForNotice('Đã lưu thay đổi quyền')
  assert.equal(permissionWrites.length, 1)
  assert.deepEqual(permissionWrites[0].expectedPermissionIds, [1])
  assert.deepEqual(new Set(permissionWrites[0].permissionIds), new Set([1, 2, 3]))
  failSave = true
  await check('CREATE').uncheck(); await button('Lưu thay đổi').click()
  await page.getByText('Dữ liệu phân quyền đã thay đổi. Hãy tải lại trước khi lưu.', { exact: true }).waitFor()
  assert.equal(await check('CREATE').isChecked(), false, 'rejected save retains draft')
  assert.equal(await page.getByRole('status').count(), 0, 'no success on rejection')
  await button('Hủy').click(); failSave = false
  await page.getByRole('button', { name: /^Historical user/ }).click()
  assert.equal(await check('VIEW').isDisabled(), true)
  await page.getByRole('button', { name: /^Administrator/ }).click(); await button('Chỉnh sửa vai trò').click()
  assert.equal(await page.getByLabel('Mã vai trò', { exact: true }).isDisabled(), true)
  assert.equal(await page.getByRole('checkbox', { name: 'Hoạt động', exact: true }).isDisabled(), true)
  await button('Hủy').click()
  await check('CREATE').check()
  const writesBeforeConfirmation = permissionWrites.length
  let confirmationShown = false
  page.once('dialog', async dialog => { confirmationShown = true; await dialog.dismiss() })
  await button('Lưu thay đổi').click()
  assert.equal(confirmationShown, true)
  assert.equal(permissionWrites.length, writesBeforeConfirmation, 'cancelled ADMIN confirmation never writes')
  await button('Hủy').click()
  await button('Thêm vai trò').click()
  await page.getByLabel('Mã vai trò', { exact: true }).fill('NEW_ROLE')
  await page.getByLabel('Tên vai trò', { exact: true }).fill('New role')
  await button('Lưu thông tin').click(); await waitForNotice('Đã lưu thông tin vai trò')
  await check('VIEW').check(); await button('Lưu thay đổi').click(); await waitForNotice('Đã lưu thay đổi quyền')
  await button('Sao chép vai trò').click()
  await page.getByLabel('Mã vai trò', { exact: true }).fill('COPIED_ROLE')
  await button('Lưu thông tin').click(); await waitForNotice('Đã lưu thông tin vai trò')
  assert.equal(roles.find(r => r.code === 'COPIED_ROLE').permissions[0], 'DOCUMENT_VIEW')
  await mkdir('artifacts/rbac-review', { recursive: true })
  await page.screenshot({ path: 'artifacts/rbac-review/roles.png', fullPage: true })

  await page.goto(`${process.env.UI_BASE_URL || 'http://127.0.0.1:5173'}/admin/users`)
  const staff = page.getByRole('row').filter({ hasText: '@staff' })
  await staff.getByText('Custom role (CUSTOM)', { exact: true }).waitFor()
  await staff.getByText('Quyền hiệu lực (chỉ đọc)', { exact: true }).click()
  await staff.getByText('DOCUMENT_VIEW', { exact: true }).waitFor()
  await staff.getByRole('button', { name: 'Cấu hình vai trò', exact: true }).click()
  assert.equal(await page.getByRole('radio').count(), 2)
  assert.equal(await page.getByRole('radio', { name: /Administrator|Historical user|Inactive role|Custom role/ }).count(), 0)
  assert.equal(await button('Lưu vai trò').isDisabled(), true, 'existing multiple roles require an explicit choice')
  await page.getByRole('radio', { name: /^Employee/ }).check()
  await page.getByRole('radio', { name: /^Accountant/ }).check()
  assert.equal(await page.getByRole('radio', { name: /^Employee/ }).isChecked(), false)
  assert.equal(assignmentWrites.length, 0)
  await button('Hủy').click(); assert.equal(assignmentWrites.length, 0)
  await staff.getByRole('button', { name: 'Cấu hình vai trò', exact: true }).click()
  await page.getByRole('radio', { name: /^Accountant/ }).check()
  failSave = true; await button('Lưu vai trò').click()
  await page.getByText('Bạn không có quyền thực hiện thao tác này.', { exact: true }).waitFor()
  assert.equal(await page.getByRole('radio', { name: /^Accountant/ }).isChecked(), true)
  assert.equal(await page.getByRole('status').count(), 0)
  failSave = false
  delaySave = true; releaseSave = undefined
  await button('Lưu vai trò').click()
  await page.waitForFunction(() => document.querySelector('[aria-busy="true"]'))
  assert.equal(await page.getByRole('radio', { name: /^Accountant/ }).isDisabled(), true)
  assert.equal(await button('Hủy').isDisabled(), true)
  while (!releaseSave) await new Promise(resolve => setTimeout(resolve, 10))
  releaseSave(); delaySave = false
  await waitForNotice('Đã cập nhật vai trò tài khoản')
  assert.deepEqual(new Set(assignmentWrites.at(-1).expectedRoleIds), new Set([2, 3]))
  assert.deepEqual(assignmentWrites.at(-1).roleIds, [6])
  await button('Thêm người dùng').click()
  const options = await page.getByLabel('Vai trò ban đầu', { exact: true }).locator('option').allTextContents()
  assert.ok(options.every(text => !/ADMIN|CUSTOM|USER|INACTIVE|NEW_ROLE/.test(text)))
  await button('Hủy').click()
  await page.screenshot({ path: 'artifacts/rbac-review/users.png', fullPage: true })
  assert.deepEqual(failures, [])
  console.log('PASS: role drafts, group selection, cancel, batched save, busy lock, conflict, protected roles, create/copy, single accountant/employee selection, readonly access, API failure, no ADMIN choice')
} finally { await browser.close() }
