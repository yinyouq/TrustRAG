<script setup lang="ts">
// 运行状态标签组件，统一展示评估任务状态。
import { computed } from 'vue'
import type { EvalRunStatus } from '@/api/types'

const props = defineProps<{ status: EvalRunStatus }>()

const labels: Record<EvalRunStatus, string> = {
  PENDING: '等待中',
  RUNNING: '运行中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  CANCELED: '已取消',
}
const tagType = computed(() => ({
  PENDING: 'info',
  RUNNING: 'warning',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  CANCELED: 'info',
}[props.status] as 'info' | 'warning' | 'success' | 'danger'))
</script>

<template>
  <el-tag :type="tagType" effect="light" round>{{ labels[status] }}</el-tag>
</template>
