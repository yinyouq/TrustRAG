/**
 * Vite 环境类型声明，保证 TypeScript 能识别导入的运行时类型。
 */
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_ADMIN_API_BASE_URL?: string
  readonly VITE_USE_MOCKS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
