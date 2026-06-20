export interface RunPoller {
  start: () => void
  stop: () => void
}

export function createRunPoller(
  load: () => Promise<void>,
  shouldContinue: () => boolean,
  intervalMs = 5_000,
): RunPoller {
  let timer: ReturnType<typeof setTimeout> | null = null
  let stopped = true

  const tick = async () => {
    timer = null
    if (stopped) return
    await load()
    if (!stopped && shouldContinue()) {
      timer = setTimeout(() => void tick(), intervalMs)
    }
  }

  return {
    start() {
      if (!stopped) return
      stopped = false
      void tick()
    },
    stop() {
      stopped = true
      if (timer) clearTimeout(timer)
      timer = null
    },
  }
}
