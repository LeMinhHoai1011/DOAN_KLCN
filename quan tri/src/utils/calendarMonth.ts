/** Month grid for the date picker: 6 weeks × 7 days, weeks start on Monday (Vietnamese convention). */
export interface CalendarCell { iso: string; day: number; inMonth: boolean }

const pad = (value: number) => String(value).padStart(2, '0')
export const toIso = (year: number, month: number, day: number) => `${year}-${pad(month)}-${pad(day)}`

/** month is 1–12. Uses UTC arithmetic so the grid never shifts with the viewer's timezone. */
export const monthGrid = (year: number, month: number): CalendarCell[] => {
  const first = new Date(Date.UTC(year, month - 1, 1))
  const offset = (first.getUTCDay() + 6) % 7 // Monday = 0
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(Date.UTC(year, month - 1, 1 + index - offset))
    return { iso: toIso(date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate()), day: date.getUTCDate(), inMonth: date.getUTCMonth() === month - 1 }
  })
}

export const shiftMonth = (year: number, month: number, delta: number) => {
  const index = year * 12 + (month - 1) + delta
  return { year: Math.floor(index / 12), month: (index % 12) + 1 }
}

/** Today in Vietnam time, so the highlighted day matches the "Giờ Việt Nam" filters. */
export const todayInVietnam = (now = new Date()) => {
  const shifted = new Date(now.getTime() + 7 * 60 * 60 * 1000)
  return toIso(shifted.getUTCFullYear(), shifted.getUTCMonth() + 1, shifted.getUTCDate())
}

export const weekdayLabels = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
