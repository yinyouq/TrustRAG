<script setup lang="ts">
// CSV 导入弹窗，负责上传评估用例并展示导入结果。
import { ref, watch } from 'vue'
import type { EvalCaseCsvImportResult } from '@/api/types'

const props = withDefaults(defineProps<{
  modelValue: boolean
  importing?: boolean
  result?: EvalCaseCsvImportResult | null
}>(), {
  importing: false,
  result: null,
})
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [file: File]
}>()
const file = ref<File | null>(null)

watch(() => props.modelValue, (visible) => {
  if (visible) file.value = null
})

function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="导入 CSV 测试样例"
    width="min(620px, 94vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="quiet">字段：question、expected_answer、expected_knowledge_ids、tags、difficulty。知识 ID 与标签使用 | 分隔。</p>
    <label class="file-drop">
      <input type="file" accept=".csv,text/csv" @change="selectFile" />
      <strong>{{ file?.name || '选择 UTF-8 CSV 文件' }}</strong>
      <span>合法行会被保留，错误行会单独返回原因。</span>
    </label>
    <el-alert
      v-if="result"
      :title="`共 ${result.totalRows} 行，成功 ${result.importedRows} 行，失败 ${result.failedRows} 行`"
      :type="result.failedRows ? 'warning' : 'success'"
      :closable="false"
      class="block-gap"
    />
    <el-table v-if="result?.errors.length" :data="result.errors" size="small" max-height="220">
      <el-table-column prop="rowNumber" label="行号" width="80" />
      <el-table-column prop="message" label="错误原因" />
    </el-table>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :disabled="!file" :loading="importing" @click="file && emit('submit', file)">开始导入</el-button>
    </template>
  </el-dialog>
</template>
