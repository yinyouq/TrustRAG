/**
 * 验证 format 的前端行为和边界场景。
 */
import { describe, expect, it } from 'vitest'
import { formatDuration, formatPercent, formatSignedPercent } from './format'

describe('metric formatters', () => {
  it('distinguishes missing metrics from zero', () => {
    expect(formatPercent(null)).toBe('--')
    expect(formatPercent(undefined)).toBe('--')
    expect(formatPercent(0)).toBe('0.0%')
    expect(formatPercent(0.864)).toBe('86.4%')
  })

  it('formats durations for milliseconds seconds and minutes', () => {
    expect(formatDuration(null)).toBe('--')
    expect(formatDuration(650)).toBe('650 ms')
    expect(formatDuration(1_250)).toBe('1.3 s')
    expect(formatDuration(65_000)).toBe('1m 5s')
  })

  it('formats signed percentage deltas', () => {
    expect(formatSignedPercent(0.042)).toBe('+4.2%')
    expect(formatSignedPercent(-0.03)).toBe('-3.0%')
    expect(formatSignedPercent(null)).toBe('--')
  })
})
