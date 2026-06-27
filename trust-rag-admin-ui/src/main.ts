/**
 * 前端应用入口，挂载 Vue、路由、状态管理和 Element Plus。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/index.css'

async function bootstrap() {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCKS === 'true') {
    const { worker } = await import('./mocks/browser')
    await worker.start({ onUnhandledRequest: 'bypass' })
  }

  createApp(App)
    .use(createPinia())
    .use(router)
    .use(ElementPlus)
    .mount('#app')
}

void bootstrap()
