export type ReadOutcome = 'success' | 'empty' | 'error'

export const listReadOutcome = (status: number, itemCount: number): ReadOutcome => {
  if (status !== 200) return 'error'
  return itemCount > 0 ? 'success' : 'empty'
}

export const classificationReadOutcome = (status: number): ReadOutcome => {
  if (status === 200) return 'success'
  if (status === 404) return 'empty'
  return 'error'
}
