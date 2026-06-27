/**
 * 测试环境初始化，配置组件测试所需的全局依赖。
 */
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './testServer'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
