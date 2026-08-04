/**
 * 指标格式化工具。
 *
 * 后端指标大多以 0..1 的小数返回，页面统一在这里转换为百分比或可读时长。
 */
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

/** 将容器内存字节数格式化为便于比较的二进制单位。 */
export function formatBytes(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '--'
  }
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
  let amount = Math.max(0, value)
  let unitIndex = 0
  while (amount >= 1024 && unitIndex < units.length - 1) {
    amount /= 1024
    unitIndex += 1
  }
  const digits = unitIndex === 0 ? 0 : amount >= 100 ? 0 : 1
  return `${amount.toFixed(digits)} ${units[unitIndex]}`
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
