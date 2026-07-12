<script setup lang="ts">
// 知识库管理页，提供文档导入任务、手动知识导入和评估知识 ID 参考。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Delete, Download, Refresh, RefreshLeft, Search, UploadFilled } from '@element-plus/icons-vue'
import { knowledgeApi } from '@/api'
import type {
  DocumentImportStatus,
  DocumentImportTask,
  KnowledgeImportResult,
  KnowledgeReferenceItem,
  KnowledgeItem,
  ScopeType,
  TrustLevel,
} from '@/api/knowledge'
import { useAppStore } from '@/stores/app'
import { formatDateTime } from '@/utils/format'

const store = useAppStore()
const trustOptions: TrustLevel[] = ['HIGH', 'MEDIUM', 'LOW']
const scopeOptions: ScopeType[] = ['GLOBAL', 'TENANT', 'PROJECT', 'USER', 'CONVERSATION']

const documentSaving = ref(false)
const taskLoading = ref(false)
const importSaving = ref(false)
const libraryLoading = ref(false)
const knowledgeLoading = ref(false)
const governanceLoading = ref(false)
const governanceActionLoading = ref(false)
const selectedFile = ref<File | null>(null)
const taskId = ref('')
const currentTask = ref<DocumentImportTask | null>(null)
const importResult = ref<KnowledgeImportResult | null>(null)
const documentLibraries = ref<DocumentImportTask[]>([])
const selectedLibrary = ref<DocumentImportTask | null>(null)
const libraryKnowledge = ref<KnowledgeReferenceItem[]>([])
const selectedGovernanceKnowledge = ref<KnowledgeItem | null>(null)
const knowledgePage = ref(1)
const knowledgePageSize = 10
const knowledgeTotal = ref(0)
const operatorId = ref(localStorage.getItem('trust-rag.operator-id') || 'admin')

const documentForm = reactive({
  title: '',
  sourceUrl: '',
  sourceType: 'document',
  trustLevel: 'HIGH' as TrustLevel,
  scopeType: 'GLOBAL' as ScopeType,
  userId: '',
  conversationId: '',
  projectId: store.projectId,
  tenantId: store.tenantId,
})

const manualForm = reactive({
  title: '',
  content: '',
  sourceType: 'manual',
  sourceRef: '',
  trustLevel: 'HIGH' as TrustLevel,
  scopeType: 'GLOBAL' as ScopeType,
  userId: '',
  conversationId: '',
  projectId: store.projectId,
  tenantId: store.tenantId,
})

const governanceForm = reactive({
  lookupId: '',
  targetKnowledgeId: '',
  sourceKnowledgeIds: '',
})

const fileName = computed(() => selectedFile.value?.name || '选择 PDF、Word、Markdown、HTML 等知识文档')
const libraryScopeItems = computed(() => {
  const task = selectedLibrary.value
  if (!task) return []
  return [
    ['scope_type', task.scopeType],
    ['tenant_id', task.tenantId],
    ['project_id', task.projectId],
    ['user_id', task.userId],
    ['conversation_id', task.conversationId],
    ['trust_level', task.trustLevel],
    ['status', task.status],
  ]
})

onMounted(() => {
  loadDocumentLibraries()
})

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

async function uploadDocument() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要导入的文档')
    return
  }
  documentSaving.value = true
  try {
    const task = await knowledgeApi.uploadDocument({
      file: selectedFile.value,
      ...documentForm,
    })
    currentTask.value = task
    taskId.value = task.taskId
    ElMessage.success('文档导入任务已提交')
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    documentSaving.value = false
  }
}

async function loadTask() {
  if (!taskId.value.trim()) {
    ElMessage.warning('请输入任务 ID')
    return
  }
  taskLoading.value = true
  try {
    currentTask.value = await knowledgeApi.getDocumentTask(taskId.value.trim())
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    taskLoading.value = false
  }
}

async function retryTask() {
  if (!taskId.value.trim()) {
    ElMessage.warning('请输入任务 ID')
    return
  }
  taskLoading.value = true
  try {
    currentTask.value = await knowledgeApi.retryDocumentTask(taskId.value.trim())
    ElMessage.success('已重新提交导入任务')
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    taskLoading.value = false
  }
}

async function loadDocumentLibraries() {
  libraryLoading.value = true
  try {
    documentLibraries.value = await knowledgeApi.listDocumentTasks({ limit: 100, offset: 0 })
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    libraryLoading.value = false
  }
}

async function selectDocumentLibrary(task: DocumentImportTask) {
  selectedLibrary.value = task
  knowledgePage.value = 1
  await loadLibraryKnowledge()
}

async function loadLibraryKnowledge() {
  if (!selectedLibrary.value) {
    return
  }
  knowledgeLoading.value = true
  try {
    const response = await knowledgeApi.listDocumentKnowledge(selectedLibrary.value.taskId, {
      limit: knowledgePageSize,
      offset: (knowledgePage.value - 1) * knowledgePageSize,
    })
    selectedLibrary.value = response.task
    libraryKnowledge.value = response.items
    knowledgeTotal.value = response.totalCount
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    knowledgeLoading.value = false
  }
}

async function changeKnowledgePage(page: number) {
  knowledgePage.value = page
  await loadLibraryKnowledge()
}

async function importKnowledge() {
  if (!manualForm.title.trim() || !manualForm.content.trim()) {
    ElMessage.warning('请输入知识标题和正文')
    return
  }
  importSaving.value = true
  try {
    importResult.value = await knowledgeApi.importKnowledge({
      ...manualForm,
      title: manualForm.title.trim(),
      content: manualForm.content.trim(),
    })
    ElMessage.success('知识已提交入库')
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    importSaving.value = false
  }
}

async function lookupGovernanceKnowledge() {
  const id = Number(governanceForm.lookupId)
  if (!Number.isInteger(id) || id <= 0) {
    ElMessage.warning('请输入有效的 knowledge_id')
    return
  }
  governanceLoading.value = true
  try {
    const item = await knowledgeApi.getKnowledge(id)
    selectGovernanceKnowledge(item)
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    governanceLoading.value = false
  }
}

function selectGovernanceKnowledge(item: KnowledgeItem) {
  selectedGovernanceKnowledge.value = item
  governanceForm.lookupId = String(item.id)
}

async function downgradeSelectedKnowledge() {
  const item = selectedGovernanceKnowledge.value
  if (!item || !ensureOperator()) return
  const reason = await promptReason('降级知识', '发现知识有误，需要降低可信度')
  if (!reason) return
  await runGovernanceAction(async () => {
    const changed = await knowledgeApi.downgradeKnowledge(item.id, lifecyclePayload(reason))
    selectedGovernanceKnowledge.value = changed
    ElMessage.success('知识已降级')
  })
}

async function rollbackSelectedKnowledge() {
  const item = selectedGovernanceKnowledge.value
  if (!item || !ensureOperator()) return
  const reason = await promptReason('回滚知识', '恢复到上一治理状态')
  if (!reason) return
  await runGovernanceAction(async () => {
    const changed = await knowledgeApi.rollbackKnowledge(item.id, lifecyclePayload(reason))
    selectedGovernanceKnowledge.value = changed
    ElMessage.success('知识已回滚')
  })
}

async function deleteSelectedKnowledge() {
  const item = selectedGovernanceKnowledge.value
  if (!item || !ensureOperator()) return
  const reason = await promptReason('删除知识', '确认这条知识错误，退出检索')
  if (!reason) return
  await runGovernanceAction(async () => {
    const changed = await knowledgeApi.deleteKnowledge(item.id, lifecyclePayload(reason))
    selectedGovernanceKnowledge.value = changed
    ElMessage.success('知识已删除并退出检索')
  })
}

async function mergeKnowledgeItems() {
  if (!ensureOperator()) return
  const targetKnowledgeId = Number(governanceForm.targetKnowledgeId)
  const sourceKnowledgeIds = parseKnowledgeIds(governanceForm.sourceKnowledgeIds)
  if (!Number.isInteger(targetKnowledgeId) || targetKnowledgeId <= 0) {
    ElMessage.warning('请输入有效的目标 knowledge_id')
    return
  }
  if (!sourceKnowledgeIds.length) {
    ElMessage.warning('请输入至少一个源 knowledge_id')
    return
  }
  if (sourceKnowledgeIds.includes(targetKnowledgeId)) {
    ElMessage.warning('源知识不能包含目标知识')
    return
  }
  await runGovernanceAction(async () => {
    const target = await knowledgeApi.mergeKnowledge({
      operatorId: operatorId.value.trim(),
      targetKnowledgeId,
      sourceKnowledgeIds,
    })
    selectGovernanceKnowledge(target)
    ElMessage.success('知识已合并，源知识已退出检索')
  })
}

async function runGovernanceAction(action: () => Promise<void>) {
  governanceActionLoading.value = true
  try {
    await action()
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    governanceActionLoading.value = false
  }
}

async function promptReason(title: string, defaultReason: string) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入操作原因，便于后续治理追踪。',
      title,
      {
        inputType: 'textarea',
        inputValue: defaultReason,
        inputValidator: value => Boolean(value?.trim()) || '请输入操作原因',
        confirmButtonText: '确认',
        cancelButtonText: '取消',
      },
    )
    return value.trim()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error(message(reason))
    return null
  }
}

function lifecyclePayload(reason: string) {
  return {
    operatorId: operatorId.value.trim(),
    reason,
  }
}

function ensureOperator() {
  if (!operatorId.value.trim()) {
    ElMessage.warning('请输入操作人 ID')
    return false
  }
  localStorage.setItem('trust-rag.operator-id', operatorId.value.trim())
  return true
}

function parseKnowledgeIds(value: string) {
  return value
    .split(/[,\s，]+/)
    .map(item => Number(item.trim()))
    .filter(item => Number.isInteger(item) && item > 0)
}

function statusType(status: DocumentImportStatus) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PARTIAL') return 'warning'
  return 'info'
}

function scopeText(item: KnowledgeItem) {
  const values = [item.scopeType, item.tenantId, item.projectId, item.userId, item.conversationId]
    .filter(Boolean)
  return values.join(' / ')
}

function libraryName(task: DocumentImportTask) {
  return task.title || task.originalFilename || task.sourceUri || task.taskId
}

async function copyExpectedKnowledgeIds(item: KnowledgeReferenceItem) {
  await navigator.clipboard.writeText(item.expectedKnowledgeIds)
  ElMessage.success('已复制 knowledge_id')
}

async function exportKnowledgeCsv() {
  if (!selectedLibrary.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  let exportItems: KnowledgeReferenceItem[] = []
  try {
    const firstPage = await knowledgeApi.listDocumentKnowledge(selectedLibrary.value.taskId, {
      limit: 1000,
      offset: 0,
    })
    exportItems = [...firstPage.items]
    for (let offset = exportItems.length; offset < firstPage.totalCount; offset += 1000) {
      const nextPage = await knowledgeApi.listDocumentKnowledge(selectedLibrary.value.taskId, {
        limit: 1000,
        offset,
      })
      if (!nextPage.items.length) break
      exportItems.push(...nextPage.items)
    }
  } catch (reason) {
    ElMessage.error(message(reason))
    return
  }
  if (!exportItems.length) {
    ElMessage.warning('当前知识库暂无可导出的知识')
    return
  }
  const columns: Array<keyof KnowledgeReferenceItem> = [
    'knowledgeId',
    'expectedKnowledgeIds',
    'title',
    'content',
    'scopeType',
    'tenantId',
    'projectId',
    'userId',
    'conversationId',
    'trustLevel',
    'status',
    'sourceType',
    'sourceRef',
    'sourceTitle',
    'sourceUrl',
    'pageNumber',
    'sectionPath',
    'documentId',
    'chunkIndex',
    'createdAt',
    'updatedAt',
  ]
  const headers = [
    'knowledge_id',
    'expected_knowledge_ids',
    'title',
    'content',
    'scope_type',
    'tenant_id',
    'project_id',
    'user_id',
    'conversation_id',
    'trust_level',
    'status',
    'source_type',
    'source_ref',
    'source_title',
    'source_url',
    'page_number',
    'section_path',
    'document_id',
    'chunk_index',
    'created_at',
    'updated_at',
  ]
  const rows = exportItems.map(item =>
    columns.map(column => csvCell(item[column])).join(','),
  )
  const csv = `\uFEFF${headers.join(',')}\n${rows.join('\n')}`
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `knowledge-reference-${selectedLibrary.value.taskId}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

function csvCell(value: unknown) {
  if (value === null || value === undefined) return ''
  const text = String(value)
  if (/[",\r\n]/.test(text)) {
    return `"${text.replaceAll('"', '""')}"`
  }
  return text
}

function message(reason: unknown) {
  return reason instanceof Error ? reason.message : '操作失败'
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <span class="eyebrow">KNOWLEDGE</span>
        <h1>知识库导入</h1>
        <p>把可信文档或人工确认的知识写入 RAG 知识池。</p>
      </div>
    </div>

    <div class="knowledge-grid">
      <div class="content-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">DOCUMENTS</span>
            <h2>文档上传</h2>
          </div>
        </div>

        <label class="file-drop block-gap">
          <el-icon size="28"><UploadFilled /></el-icon>
          <strong>{{ fileName }}</strong>
          <span>上传后会异步解析、切片、向量化并写入检索索引。</span>
          <input type="file" @change="onFileChange" />
        </label>

        <el-form class="block-gap" label-position="top">
          <div class="form-grid">
            <el-form-item label="标题">
              <el-input v-model="documentForm.title" placeholder="例如：产品手册" clearable />
            </el-form-item>
            <el-form-item label="来源类型">
              <el-input v-model="documentForm.sourceType" placeholder="document" clearable />
            </el-form-item>
            <el-form-item label="信任级别">
              <el-select v-model="documentForm.trustLevel">
                <el-option v-for="item in trustOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="作用域">
              <el-select v-model="documentForm.scopeType">
                <el-option v-for="item in scopeOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="租户 ID">
              <el-input v-model="documentForm.tenantId" clearable />
            </el-form-item>
            <el-form-item label="项目 ID">
              <el-input v-model="documentForm.projectId" clearable />
            </el-form-item>
          </div>
          <el-form-item label="来源 URL">
            <el-input v-model="documentForm.sourceUrl" placeholder="可选，用于追踪来源" clearable />
          </el-form-item>
          <div class="heading-actions">
            <el-button type="primary" :icon="UploadFilled" :loading="documentSaving" @click="uploadDocument">
              提交文档
            </el-button>
          </div>
        </el-form>
      </div>

      <div class="content-panel">
        <div class="section-heading compact-heading">
          <div>
            <span class="eyebrow">MANUAL</span>
            <h2>手动导入知识</h2>
          </div>
        </div>

        <el-form class="block-gap" label-position="top">
          <div class="form-grid">
            <el-form-item label="知识标题">
              <el-input v-model="manualForm.title" placeholder="例如：部署说明" clearable />
            </el-form-item>
            <el-form-item label="来源类型">
              <el-input v-model="manualForm.sourceType" placeholder="manual" clearable />
            </el-form-item>
            <el-form-item label="信任级别">
              <el-select v-model="manualForm.trustLevel">
                <el-option v-for="item in trustOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="作用域">
              <el-select v-model="manualForm.scopeType">
                <el-option v-for="item in scopeOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="租户 ID">
              <el-input v-model="manualForm.tenantId" clearable />
            </el-form-item>
            <el-form-item label="项目 ID">
              <el-input v-model="manualForm.projectId" clearable />
            </el-form-item>
          </div>
          <el-form-item label="来源引用">
            <el-input v-model="manualForm.sourceRef" placeholder="manual://note 或 ticket://123" clearable />
          </el-form-item>
          <el-form-item label="知识正文">
            <el-input
              v-model="manualForm.content"
              type="textarea"
              :rows="7"
              maxlength="6000"
              show-word-limit
              placeholder="输入已经确认可信的知识内容"
            />
          </el-form-item>
          <div class="heading-actions">
            <el-button type="primary" :loading="importSaving" @click="importKnowledge">
              导入知识
            </el-button>
          </div>
        </el-form>

        <el-alert
          v-if="importResult"
          class="block-gap"
          type="success"
          :closable="false"
          :title="`导入 ${importResult.importedCount} 条，重复 ${importResult.duplicateCount} 条，失败 ${importResult.failedCount} 条`"
        >
          <template #default>
            <span>知识 ID：{{ importResult.knowledgeIds.join(', ') || '无' }}</span>
          </template>
        </el-alert>
      </div>
    </div>

    <div class="content-panel block-gap">
      <div class="section-heading compact-heading">
        <div>
          <span class="eyebrow">TASKS</span>
          <h2>任务查询</h2>
        </div>
        <div class="task-actions">
          <el-input v-model="taskId" placeholder="输入文档导入 taskId" clearable />
          <el-button :icon="Search" :loading="taskLoading" @click="loadTask">查询</el-button>
          <el-button :icon="Refresh" :loading="taskLoading" @click="retryTask">重试</el-button>
        </div>
      </div>

      <el-descriptions v-if="currentTask" class="block-gap" :column="3" border>
        <el-descriptions-item label="任务 ID">{{ currentTask.taskId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(currentTask.status)">{{ currentTask.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文件">{{ currentTask.originalFilename || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文档数">{{ currentTask.totalDocuments ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="切片数">{{ currentTask.totalSections ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="导入数">{{ currentTask.importedCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="重复数">{{ currentTask.duplicateCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="失败数">{{ currentTask.failedCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(currentTask.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="currentTask.errorMessage" label="错误" :span="3">
          {{ currentTask.errorMessage }}
        </el-descriptions-item>
      </el-descriptions>
      <div v-else class="quiet block-gap">提交文档后会自动填入任务 ID，也可以手动输入历史 taskId 查询进度。</div>
    </div>

    <div class="content-panel block-gap table-panel">
      <div class="section-heading compact-heading">
        <div>
          <span class="eyebrow">GOVERNANCE</span>
          <h2>知识治理操作</h2>
          <p class="quiet">按 knowledge_id 查询已入库知识，并执行降级、回滚、合并或删除。</p>
        </div>
        <div class="review-actions">
          <el-input v-model="operatorId" placeholder="操作人 ID" clearable />
        </div>
      </div>

      <div class="toolbar toolbar--wide block-gap">
        <el-input v-model="governanceForm.lookupId" placeholder="knowledge_id" clearable />
        <el-button type="primary" :icon="Search" :loading="governanceLoading" @click="lookupGovernanceKnowledge">
          查询
        </el-button>
      </div>

      <el-descriptions v-if="selectedGovernanceKnowledge" class="block-gap" :column="3" border>
        <el-descriptions-item label="knowledge_id">{{ selectedGovernanceKnowledge.id }}</el-descriptions-item>
        <el-descriptions-item label="信任级别">{{ selectedGovernanceKnowledge.trustLevel }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedGovernanceKnowledge.status }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ selectedGovernanceKnowledge.title }}</el-descriptions-item>
        <el-descriptions-item label="Scope">{{ scopeText(selectedGovernanceKnowledge) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源" :span="3">
          {{ selectedGovernanceKnowledge.sourceType || '-' }} /
          {{ selectedGovernanceKnowledge.sourceRef || '暂无来源引用' }}
        </el-descriptions-item>
        <el-descriptions-item label="内容" :span="3">
          {{ selectedGovernanceKnowledge.content }}
        </el-descriptions-item>
        <el-descriptions-item v-if="selectedGovernanceKnowledge.rejectReason" label="退出原因" :span="3">
          {{ selectedGovernanceKnowledge.rejectReason }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="heading-actions block-gap">
        <el-button
          type="warning"
          :icon="RefreshLeft"
          :disabled="!selectedGovernanceKnowledge"
          :loading="governanceActionLoading"
          @click="downgradeSelectedKnowledge"
        >
          降级
        </el-button>
        <el-button
          :icon="Refresh"
          :disabled="!selectedGovernanceKnowledge"
          :loading="governanceActionLoading"
          @click="rollbackSelectedKnowledge"
        >
          回滚
        </el-button>
        <el-button
          type="danger"
          :icon="Delete"
          :disabled="!selectedGovernanceKnowledge"
          :loading="governanceActionLoading"
          @click="deleteSelectedKnowledge"
        >
          删除
        </el-button>
      </div>

      <el-form class="block-gap" label-position="top">
        <div class="form-grid form-grid--3">
          <el-form-item label="目标 knowledge_id">
            <el-input v-model="governanceForm.targetKnowledgeId" placeholder="保留的正确知识 ID" clearable />
          </el-form-item>
          <el-form-item label="源 knowledge_id">
            <el-input v-model="governanceForm.sourceKnowledgeIds" placeholder="多个 ID 用逗号或空格分隔" clearable />
          </el-form-item>
          <el-form-item label="合并">
            <el-button
              type="primary"
              :icon="Check"
              :loading="governanceActionLoading"
              @click="mergeKnowledgeItems"
            >
              合并到目标知识
            </el-button>
          </el-form-item>
        </div>
      </el-form>

    </div>

    <div class="content-panel block-gap table-panel">
      <div class="section-heading compact-heading">
        <div>
          <span class="eyebrow">EVAL REFERENCE</span>
          <h2>评估知识 ID</h2>
          <p class="quiet">按上传的知识库查看每条知识及 knowledge_id，导出的 CSV 可辅助填写测试用例 expected_knowledge_ids。</p>
        </div>
        <div class="review-actions">
          <el-button :icon="Refresh" :loading="libraryLoading" @click="loadDocumentLibraries">刷新知识库</el-button>
          <el-button
            type="primary"
            :icon="Download"
            :disabled="!libraryKnowledge.length"
            @click="exportKnowledgeCsv"
          >
            导出评估参考 CSV
          </el-button>
        </div>
      </div>

      <div class="knowledge-reference-layout block-gap">
        <el-table
          v-loading="libraryLoading"
          class="knowledge-library-table"
          :data="documentLibraries"
          empty-text="暂无上传知识库"
          highlight-current-row
          @row-click="selectDocumentLibrary"
        >
          <el-table-column label="知识库" min-width="220">
            <template #default="{ row }">
              <strong>{{ libraryName(row) }}</strong>
              <small class="table-note">{{ row.taskId }}</small>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="知识数" width="90">
            <template #default="{ row }">{{ row.importedCount ?? 0 }}</template>
          </el-table-column>
        </el-table>

        <div class="knowledge-reference-detail">
          <el-empty v-if="!selectedLibrary" description="请选择左侧上传知识库" :image-size="80" />
          <template v-else>
            <el-descriptions :column="4" border>
              <el-descriptions-item
                v-for="[label, value] in libraryScopeItems"
                :key="label"
                :label="label"
              >
                {{ value || '-' }}
              </el-descriptions-item>
            </el-descriptions>

            <el-table
              v-loading="knowledgeLoading"
              class="block-gap"
              :data="libraryKnowledge"
              empty-text="这个知识库下暂无知识块"
              row-key="knowledgeId"
            >
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div class="knowledge-full-content">{{ row.content }}</div>
                </template>
              </el-table-column>
              <el-table-column label="knowledge_id" width="130">
                <template #default="{ row }">
                  <el-tag type="success">{{ row.knowledgeId }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="知识内容" min-width="360">
                <template #default="{ row }">
                  <strong>{{ row.title || '-' }}</strong>
                  <small class="table-note">{{ row.contentPreview }}</small>
                </template>
              </el-table-column>
              <el-table-column label="项目" width="110">
                <template #default="{ row }">{{ row.projectId || '-' }}</template>
              </el-table-column>
              <el-table-column label="document_id" width="180">
                <template #default="{ row }">{{ row.documentId || '-' }}</template>
              </el-table-column>
              <el-table-column label="chunk" width="90">
                <template #default="{ row }">{{ row.chunkIndex ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="150">
                <template #default="{ row }">
                  <el-tag type="info">{{ row.trustLevel || '-' }}</el-tag>
                  <el-tag class="collapse-tag" type="success">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="copyExpectedKnowledgeIds(row)">复制 ID</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="knowledgeTotal > knowledgePageSize" class="pagination-row">
              <el-pagination
                background
                layout="prev, pager, next, total"
                :current-page="knowledgePage"
                :page-size="knowledgePageSize"
                :total="knowledgeTotal"
                @current-change="changeKnowledgePage"
              />
            </div>
          </template>
        </div>
      </div>
    </div>

  </section>
</template>
