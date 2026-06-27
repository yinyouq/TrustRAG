/**
 * 前端测试用 Mock 服务，拦截 API 请求并返回固定数据。
 */
import { setupServer } from 'msw/node'

export const server = setupServer()
