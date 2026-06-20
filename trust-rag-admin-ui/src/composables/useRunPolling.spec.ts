import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRunPoller } from './useRunPolling'

describe('run poller', () => {
  afterEach(() => vi.useRealTimers())

  it('polls while active work exists and stops at terminal state', async () => {
    vi.useFakeTimers()
    let active = true
    const load = vi.fn(async () => {
      if (load.mock.calls.length === 2) active = false
    })
    const poller = createRunPoller(load, () => active, 1_000)

    poller.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(load).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1_000)
    expect(load).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(5_000)
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('clears a scheduled poll when stopped', async () => {
    vi.useFakeTimers()
    const load = vi.fn(async () => undefined)
    const poller = createRunPoller(load, () => true, 1_000)

    poller.start()
    await vi.advanceTimersByTimeAsync(0)
    poller.stop()
    await vi.advanceTimersByTimeAsync(2_000)

    expect(load).toHaveBeenCalledTimes(1)
  })
})
