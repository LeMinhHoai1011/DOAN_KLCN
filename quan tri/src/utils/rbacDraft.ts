export const sameSet = <T,>(left: readonly T[], right: readonly T[]) => {
  const a = new Set(left)
  const b = new Set(right)
  return a.size === b.size && [...a].every(value => b.has(value))
}

export const toggleValues = <T,>(current: readonly T[], values: readonly T[], checked: boolean): T[] => {
  const result = new Set(current)
  for (const value of values) {
    if (checked) result.add(value)
    else result.delete(value)
  }
  return [...result]
}

export const isAssignableRole = (role: { code: string; active: boolean }) =>
  role.active && ['ACCOUNTANT', 'EMPLOYEE'].includes(role.code)
export const isCreatableAccountRole = (role: { code: string; active: boolean }) =>
  isAssignableRole(role)
export const isSystemRole = (code: string) => ['ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER'].includes(code)
