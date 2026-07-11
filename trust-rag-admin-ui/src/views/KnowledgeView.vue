<script setup lang="ts">
// 知识库管理页，提供文档导入任务、手动知识导入和评估知识 ID 参考。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Download, Refresh, Search, UploadFilled } from '@element-plus/icons-vue'
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
const reviewLoading = ref(false)
const libraryLoading = ref(false)
const knowledgeLoading = ref(false)
const reviewSavingId = ref<number | null>(null)
const selectedFile = ref<File | null>(null)
const taskId = ref('')
const currentTask = ref<DocumentImportTask | null>(null)
const importResult = ref<KnowledgeImportResult | null>(null)
const reviewCandidates = ref<KnowledgeItem[]>([])
const documentLibraries = ref<DocumentImportTask[]>([])
const selectedLibrary = ref<DocumentImportTask | null>(null)
const libraryKnowledge = ref<KnowledgeReferenceItem[]>([])
const knowledgePage = ref(1)
const knowledgePageSize = 10
const knowledgeTotal = ref(0)
const reviewerId = ref(localStorage.getItem('trust-rag.reviewer-id') || 'admin')

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
  loadReviewCandidates()
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

async function loadReviewCandidates() {
  reviewLoading.value = true
  try {
    reviewCandidates.value = await knowledgeApi.listReviewCandidates({
      status: 'HUMAN_REVIEW_PENDING',
      trustLevel: 'MEDIUM',
      limit: 50,
      offset: 0,
    })
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    reviewLoading.value = false
  }
}

async function approveCandidate(item: KnowledgeItem) {
  if (!reviewerId.value.trim()) {
    ElMessage.warning('请输入审核人 ID')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      '可填写审核说明，也可以在通过前修改标题或正文。',
      '通过为高可信',
      {
        inputValue: '来源和内容已确认',
        confirmButtonText: '通过',
        cancelButtonText: '取消',
      },
    )
    persistReviewer()
    reviewSavingId.value = item.id
    await knowledgeApi.approveHigh(item.id, {
      reviewerId: reviewerId.value.trim(),
      comment: value || null,
    })
    ElMessage.success('已通过审核并进入高可信池')
    await loadReviewCandidates()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error(message(reason))
  } finally {
    reviewSavingId.value = null
  }
}

async function rejectCandidate(item: KnowledgeItem) {
  if (!reviewerId.value.trim()) {
    ElMessage.warning('请输入审核人 ID')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入驳回原因，便于后续治理追踪。',
      '驳回知识',
      {
        inputType: 'textarea',
        inputValidator: value => Boolean(value?.trim()) || '请输入驳回原因',
        confirmButtonText: '驳回',
        cancelButtonText: '取消',
      },
    )
    persistReviewer()
    reviewSavingId.value = item.id
    await knowledgeApi.rejectCandidate(item.id, {
      reviewerId: reviewerId.value.trim(),
      comment: value.trim(),
    })
    ElMessage.success('已驳回')
    await loadReviewCandidates()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error(message(reason))
  } finally {
    reviewSavingId.value = null
  }
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

function persistReviewer() {
  localStorage.setItem('trust-rag.reviewer-id', reviewerId.value.trim())
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

    <div class="content-panel block-gap table-panel">
      <div class="section-heading compact-heading">
        <div>
          <span class="eyebrow">REVIEW</span>
          <h2>人工审核</h2>
          <p class="quiet">审核 HUMAN_REVIEW_PENDING 的中可信知识，通过后会进入高可信池。</p>
        </div>
        <div class="review-actions">
          <el-input v-model="reviewerId" placeholder="审核人 ID" clearable />
          <el-button :icon="Refresh" :loading="reviewLoading" @click="loadReviewCandidates">刷新</el-button>
        </div>
      </div>

      <el-table
        v-loading="reviewLoading"
        class="block-gap"
        :data="reviewCandidates"
        empty-text="暂无待审核知识"
      >
        <el-table-column label="知识" min-width="310">
          <template #default="{ row }">
            <strong>{{ row.title }}</strong>
            <small class="table-note">{{ row.content }}</small>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="180">
          <template #default="{ row }">
            {{ row.sourceType || '-' }}
            <small class="table-note">{{ row.sourceRef || '暂无来源引用' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="Scope" min-width="180">
          <template #default="{ row }">{{ scopeText(row) || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="170">
          <template #default="{ row }">
            <el-tag type="warning">{{ row.trustLevel }}</el-tag>
            <el-tag class="collapse-tag" type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="Check"
              :loading="reviewSavingId === row.id"
              @click="approveCandidate(row)"
            >
              通过为高可信
            </el-button>
            <el-button
              link
              type="danger"
              :icon="Close"
              :loading="reviewSavingId === row.id"
              @click="rejectCandidate(row)"
            >
              驳回
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>
