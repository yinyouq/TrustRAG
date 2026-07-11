<script setup lang="ts">
// EvaluationLayout 页面布局组件，承载评估台导航和公共页面框架。
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Setting } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const store = useAppStore()
const settingsVisible = ref(false)
const tenantId = ref(store.tenantId)
const projectId = ref(store.projectId)
const token = ref(store.token)

const navigation = [
  { to: '/eval', label: '概览' },
  { to: '/eval/datasets', label: '测试集' },
  { to: '/eval/cases', label: '测试样例' },
  { to: '/eval/runs', label: '评估任务' },
  { to: '/eval/reports', label: '评估报告' },
  { to: '/eval/compare', label: '对比分析' },
  { to: '/eval/governance', label: '知识治理' },
  { to: '/eval/knowledge', label: '知识库' },
]

const activePath = computed(() => route.path)

function saveSettings() {
  store.setScope(tenantId.value, projectId.value)
  store.setToken(token.value)
  settingsVisible.value = false
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="brand" to="/eval">
        <span class="brand-mark">TR</span>
        <span><strong>TrustRAG</strong><small>Evaluation workspace</small></span>
      </RouterLink>
      <nav class="main-nav" aria-label="评估模块导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          :class="{ active: activePath === item.to }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
      <el-button circle text aria-label="环境设置" @click="settingsVisible = true">
        <el-icon><Setting /></el-icon>
      </el-button>
    </header>

    <main class="page-container">
      <RouterView />
    </main>

    <el-drawer v-model="settingsVisible" title="环境与 Scope" size="380px">
      <el-form label-position="top">
        <el-form-item label="租户 ID"><el-input v-model="tenantId" clearable /></el-form-item>
        <el-form-item label="项目 ID"><el-input v-model="projectId" clearable /></el-form-item>
        <el-form-item label="Bearer Token">
          <el-input v-model="token" type="password" show-password clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSettings">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>
