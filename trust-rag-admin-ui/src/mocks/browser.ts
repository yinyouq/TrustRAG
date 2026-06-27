/**
 * 浏览器端 Mock Worker 启动入口，仅在本地 mock 模式下使用。
 */
import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

export const worker = setupWorker(...handlers)
