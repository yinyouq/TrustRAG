export function formatPercent(value: number | null | undefined): string {
  return value === null || value === undefined ? '--' : `${(value * 100).toFixed(1)}%`
}

export function formatSignedPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '--'
  }
  const formatted = `${(value * 100).toFixed(1)}%`
  return value > 0 ? `+${formatted}` : formatted
}

export function formatDuration(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '--'
  }
  if (value < 1_000) {
    return `${Math.round(value)} ms`
  }
  if (value < 60_000) {
    return `${(value / 1_000).toFixed(1)} s`
  }
  const minutes = Math.floor(value / 60_000)
  const seconds = Math.round((value % 60_000) / 1_000)
  return `${minutes}m ${seconds}s`
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '--'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
