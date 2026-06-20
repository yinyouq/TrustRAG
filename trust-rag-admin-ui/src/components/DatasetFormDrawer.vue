<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { EvalDataset, EvalDatasetPayload } from '@/api/types'
import { validateDatasetForm } from '@/utils/formValidation'

const props = withDefaults(defineProps<{
  modelValue: boolean
  dataset?: EvalDataset | null
  tenantId?: string
  projectId?: string
  saving?: boolean
}>(), {
  dataset: null,
  tenantId: '',
  projectId: '',
  saving: false,
})
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: EvalDatasetPayload]
}>()

const form = reactive({
  name: '',
  description: '',
  tenantId: '',
  projectId: '',
  createdBy: '',
  enabled: true,
})
const errors = reactive<Record<string, string>>({})

watch(() => props.modelValue, (visible) => {
  if (!visible) return
  Object.assign(form, {
    name: props.dataset?.name ?? '',
    description: props.dataset?.description ?? '',
    tenantId: props.dataset?.tenantId ?? props.tenantId,
    projectId: props.dataset?.projectId ?? props.projectId,
    createdBy: props.dataset?.createdBy ?? '',
    enabled: props.dataset?.enabled ?? true,
  })
  clearErrors()
})

function submit() {
  clearErrors()
  Object.assign(errors, validateDatasetForm(form))
  if (Object.keys(errors).length) return
  emit('submit', {
    name: form.name.trim(),
    description: form.description.trim() || null,
    tenantId: form.tenantId.trim() || null,
    projectId: form.projectId.trim() || null,
    createdBy: form.createdBy.trim() || null,
    enabled: form.enabled,
  })
}

function clearErrors() {
  Object.keys(errors).forEach(key => delete errors[key])
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="dataset ? '编辑测试集' : '新建测试集'"
    size="min(520px, 94vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="名称" required :error="errors.name">
        <el-input v-model="form.name" placeholder="例如：Hybrid Search 回归集" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="4" placeholder="这组问题用于验证什么？" />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="租户 ID"><el-input v-model="form.tenantId" clearable /></el-form-item>
        <el-form-item label="项目 ID"><el-input v-model="form.projectId" clearable /></el-form-item>
      </div>
      <el-form-item v-if="!dataset" label="创建人"><el-input v-model="form.createdBy" clearable /></el-form-item>
      <el-form-item label="状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ dataset ? '保存' : '创建' }}</el-button>
    </template>
  </el-drawer>
</template>
