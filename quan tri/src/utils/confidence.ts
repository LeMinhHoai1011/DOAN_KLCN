export const formatConfidence = (value: number | null | undefined): string => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 1) return '—'

  const percent = Math.round(value * 1000) / 10
  return `${Number.isInteger(percent) ? percent.toFixed(0) : percent.toFixed(1)}%`
}
