/**
 * Vite 构建配置，定义 Vue 插件、路径别名和测试环境。
 */
import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/trust-rag': process.env.VITE_PROXY_TARGET || 'http://localhost:9099',
      '/api': process.env.VITE_PROXY_TARGET || 'http://localhost:9099',
    },
  },
  build: {
    outDir: process.env.VITE_OUT_DIR || 'dist',
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/tests/setup.ts'],
    restoreMocks: true,
  },
})
