<script setup lang="ts">
// 测试用例管理页，维护问题、期望答案和标准知识。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Upload } from '@element-plus/icons-vue'
import { evaluationApi } from '@/api'
import type { EvalCase, EvalCaseCsvImportResult, EvalCasePayload, EvalDataset, ExpectedKnowledge } from '@/api/types'
import CaseFormDrawer from '@/components/CaseFormDrawer.vue'
import CsvImportDialog from '@/components/CsvImportDialog.vue'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const datasets = ref<EvalDataset[]>([])
const cases = ref<EvalCase[]>([])
const selectedDatasetId = ref<number | null>(Number(route.query.datasetId) || null)
const difficulty = ref('')
const keyword = ref('')
const drawerVisible = ref(false)
const csvVisible = ref(false)
const editing = ref<EvalCase | null>(null)
const expectedKnowledge = ref<ExpectedKnowledge[]>([])
const importResult = ref<EvalCaseCsvImportResult | null>(null)

const visibleCases = computed(() => cases.value.filter(item => {
  const difficultyMatches = !difficulty.value || item.difficulty?.toUpperCase() === difficulty.value
  const keywordMatches = !keyword.value
    || item.question.toLowerCase().includes(keyword.value.toLowerCase())
    || item.tags.some(tag => tag.toLowerCase().includes(keyword.value.toLowerCase()))
  return difficultyMatches && keywordMatches
}))

onMounted(async () => {
  datasets.value = await evaluationApi.listDatasets({ includeDisabled: true })
  if (!selectedDatasetId.value && datasets.value.length) selectedDatasetId.value = datasets.value[0].id
})
watch(selectedDatasetId, (value) => {
  if (value) {
    void router.replace({ query: { ...route.query, datasetId: value } })
    void loadCases()
  } else cases.value = []
}, { immediate: true })

async function loadCases() {
  if (!selectedDatasetId.value) return
  loading.value = true
  try {
    cases.value = await evaluationApi.listCases(selectedDatasetId.value, { onlyEnabled: false, limit: 500 })
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  expectedKnowledge.value = []
  drawerVisible.value = true
}

async function openEdit(item: EvalCase) {
  editing.value = item
  expectedKnowledge.value = await evaluationApi.getExpectedKnowledge(item.id)
  drawerVisible.value = true
}

async function save(payload: EvalCasePayload) {
  saving.value = true
  try {
    if (editing.value) await evaluationApi.updateCase(editing.value.id, payload)
    else await evaluationApi.createCase(payload)
    drawerVisible.value = false
    ElMessage.success('测试样例已保存')
    await loadCases()
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    saving.value = false
  }
}

async function remove(item: EvalCase) {
  try {
    await ElMessageBox.confirm('删除这个测试样例？已有评估结果的样例不能删除。', '确认删除', { type: 'warning' })
    await evaluationApi.deleteCase(item.id)
    await loadCases()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error(message(reason))
  }
}

async function importCsv(file: File) {
  if (!selectedDatasetId.value) return
  importing.value = true
  try {
    importResult.value = await evaluationApi.importCases(selectedDatasetId.value, file)
    ElMessage.success(`成功导入 ${importResult.value.importedRows} 条样例`)
    await loadCases()
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    importing.value = false
  }
}

function message(reason: unknown) {
  return reason instanceof Error ? reason.message : '操作失败'
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="eyebrow">EVALUATION CASES</span><h1>测试样例</h1><p>维护标准答案、Expected knowledge 与真实 Scope。</p></div>
      <div class="heading-actions">
        <el-button :icon="Upload" :disabled="!selectedDatasetId" @click="importResult = null; csvVisible = true">CSV 导入</el-button>
        <el-button type="primary" :icon="Plus" :disabled="!selectedDatasetId" @click="openCreate">添加样例</el-button>
      </div>
    </div>
    <div class="toolbar content-panel toolbar--wide">
      <el-select v-model="selectedDatasetId" placeholder="选择测试集" filterable>
        <el-option v-for="dataset in datasets" :key="dataset.id" :label="dataset.name" :value="dataset.id" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索问题或标签" clearable />
      <el-select v-model="difficulty" placeholder="全部难度" clearable>
        <el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" />
      </el-select>
      <span class="quiet">{{ visibleCases.length }} 条样例</span>
    </div>
    <div class="content-panel block-gap table-panel">
      <el-table v-loading="loading" :data="visibleCases" empty-text="当前测试集没有样例">
        <el-table-column label="问题" min-width="340"><template #default="{ row }"><strong>{{ row.question }}</strong><small class="table-note">{{ row.expectedAnswer || '未设置标准答案' }}</small></template></el-table-column>
        <el-table-column label="标签" min-width="160"><template #default="{ row }"><el-tag v-for="tag in row.tags" :key="tag" size="small" class="tag-gap">{{ tag }}</el-tag><span v-if="!row.tags.length">--</span></template></el-table-column>
        <el-table-column prop="difficulty" label="难度" width="90" />
        <el-table-column label="状态" width="90"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
        <el-table-column label="更新时间" width="150"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }"><el-button link :icon="Edit" @click="openEdit(row)">编辑</el-button><el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button></template></el-table-column>
      </el-table>
    </div>
    <CaseFormDrawer v-model="drawerVisible" :dataset-id="selectedDatasetId" :case-data="editing" :expected-knowledge="expectedKnowledge" :saving="saving" @submit="save" />
    <CsvImportDialog v-model="csvVisible" :importing="importing" :result="importResult" @submit="importCsv" />
  </section>
</template>
