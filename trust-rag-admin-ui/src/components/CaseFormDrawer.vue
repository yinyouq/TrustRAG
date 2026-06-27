<script setup lang="ts">
// 测试用例表单抽屉，复用创建和编辑用例的输入逻辑。
import { reactive, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import type { EvalCase, EvalCasePayload, ExpectedKnowledge, ExpectedKnowledgePayload } from '@/api/types'
import { validateCaseForm } from '@/utils/formValidation'

const props = withDefaults(defineProps<{
  modelValue: boolean
  datasetId: number | null
  caseData?: EvalCase | null
  expectedKnowledge?: ExpectedKnowledge[]
  saving?: boolean
}>(), {
  caseData: null,
  expectedKnowledge: () => [],
  saving: false,
})
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: EvalCasePayload]
}>()

const form = reactive({
  datasetId: null as number | null,
  question: '',
  expectedAnswer: '',
  tenantId: '',
  projectId: '',
  userId: '',
  conversationId: '',
  tagsText: '',
  difficulty: null as string | null,
  enabled: true,
  expectedKnowledge: [] as ExpectedKnowledgePayload[],
})
const errors = reactive<Record<string, string>>({})

watch(() => props.modelValue, (visible) => {
  if (!visible) return
  Object.assign(form, {
    datasetId: props.caseData?.datasetId ?? props.datasetId,
    question: props.caseData?.question ?? '',
    expectedAnswer: props.caseData?.expectedAnswer ?? '',
    tenantId: props.caseData?.tenantId ?? '',
    projectId: props.caseData?.projectId ?? '',
    userId: props.caseData?.userId ?? '',
    conversationId: props.caseData?.conversationId ?? '',
    tagsText: props.caseData?.tags.join(' | ') ?? '',
    difficulty: props.caseData?.difficulty?.toUpperCase() ?? null,
    enabled: props.caseData?.enabled ?? true,
    expectedKnowledge: props.expectedKnowledge.map(item => ({
      knowledgeId: item.knowledgeId,
      relevanceGrade: item.relevanceGrade,
    })),
  })
  clearErrors()
})

function addKnowledge() {
  form.expectedKnowledge.push({ knowledgeId: 0, relevanceGrade: 1 })
}

function submit() {
  clearErrors()
  Object.assign(errors, validateCaseForm(form))
  if (Object.keys(errors).length || form.datasetId === null) return
  emit('submit', {
    datasetId: form.datasetId,
    question: form.question.trim(),
    expectedAnswer: form.expectedAnswer.trim() || null,
    tenantId: form.tenantId.trim() || null,
    projectId: form.projectId.trim() || null,
    userId: form.userId.trim() || null,
    conversationId: form.conversationId.trim() || null,
    tags: form.tagsText.split('|').map(item => item.trim()).filter(Boolean),
    difficulty: form.difficulty,
    enabled: form.enabled,
    expectedKnowledge: form.expectedKnowledge,
  })
}

function clearErrors() {
  Object.keys(errors).forEach(key => delete errors[key])
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="caseData ? '编辑测试样例' : '添加测试样例'"
    size="min(680px, 96vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="评估问题" required :error="errors.question">
        <el-input v-model="form.question" type="textarea" :rows="3" placeholder="输入用户实际会提出的问题" />
      </el-form-item>
      <el-form-item label="标准答案">
        <el-input v-model="form.expectedAnswer" type="textarea" :rows="4" placeholder="用于 Answer Correctness，可留空" />
      </el-form-item>
      <div class="form-grid form-grid--3">
        <el-form-item label="难度">
          <el-select v-model="form.difficulty" clearable placeholder="未设置">
            <el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tagsText" placeholder="rag | hybrid" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.enabled" active-text="启用" /></el-form-item>
      </div>
      <div class="form-grid">
        <el-form-item label="租户 ID"><el-input v-model="form.tenantId" clearable /></el-form-item>
        <el-form-item label="项目 ID"><el-input v-model="form.projectId" clearable /></el-form-item>
        <el-form-item label="用户 ID"><el-input v-model="form.userId" clearable /></el-form-item>
        <el-form-item label="会话 ID"><el-input v-model="form.conversationId" clearable /></el-form-item>
      </div>

      <div class="subsection-heading">
        <div><strong>Expected knowledge</strong><small>用于确定性检索指标计算</small></div>
        <el-button :icon="Plus" @click="addKnowledge">添加知识</el-button>
      </div>
      <el-alert v-if="errors.expectedKnowledge" :title="errors.expectedKnowledge" type="error" :closable="false" />
      <div v-for="(item, index) in form.expectedKnowledge" :key="index" class="knowledge-row">
        <el-input-number v-model="item.knowledgeId" :min="0" :controls="false" placeholder="知识 ID" />
        <el-select v-model="item.relevanceGrade" aria-label="相关度">
          <el-option v-for="grade in [0, 1, 2, 3]" :key="grade" :label="`相关度 ${grade}`" :value="grade" />
        </el-select>
        <el-button text type="danger" :icon="Delete" @click="form.expectedKnowledge.splice(index, 1)" />
      </div>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存样例</el-button>
    </template>
  </el-drawer>
</template>
