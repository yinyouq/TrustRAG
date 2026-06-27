/**
 * 评估任务轮询组合函数，负责活动任务的自动刷新。
 */
export interface RunPoller {
  start: () => void
  stop: () => void
}

/**
 * 评估任务轮询器。
 *
 * 每次请求完成后才安排下一次轮询，避免慢请求堆叠；当 shouldContinue 返回 false 时自动停下。
 */
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
      // 使用 setTimeout 而不是 setInterval，确保上一轮加载结束后才进入下一轮。
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
