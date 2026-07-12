<script setup lang="ts">
// 知识审核页，集中处理低可信晋升和中可信人工终审。
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Promotion as PromotionIcon, Refresh } from '@element-plus/icons-vue'
import { knowledgeApi } from '@/api'
import type { KnowledgeItem, KnowledgeStatus, PromotionRunResponse, PromotionTask, TrustLevel } from '@/api/knowledge'
import { formatDateTime } from '@/utils/format'

const lowPageSize = 10
const abnormalStatuses: KnowledgeStatus[] = ['REJECTED', 'CONFLICT', 'MERGE_PENDING', 'EXPIRED', 'INDEX_FAILED']
const diagnosticTrustLevels: TrustLevel[] = ['LOW', 'MEDIUM']
const lowPage = ref(1)
const lowHasNext = ref(false)
const lowLoading = ref(false)
const diagnosticsLoading = ref(false)
const lowKnowledge = ref<KnowledgeItem[]>([])
const promotionTasks = ref<PromotionTask[]>([])
const abnormalKnowledge = ref<KnowledgeItem[]>([])
const reviewLoading = ref(false)
const promotionRunning = ref(false)
const reviewSavingId = ref<number | null>(null)
const reviewCandidates = ref<KnowledgeItem[]>([])
const promotionResult = ref<PromotionRunResponse | null>(null)
const reviewerId = ref(localStorage.getItem('trust-rag.reviewer-id') || 'admin')
const diagnosticKnowledgeId = ref('')

const lowPaginationTotal = computed(() =>
  (lowPage.value - 1) * lowPageSize
  + lowKnowledge.value.length
  + (lowHasNext.value ? 1 : 0),
)

onMounted(() => {
  loadLowKnowledge()
  loadReviewCandidates()
  loadPromotionDiagnostics()
})

async function runPromotionTask() {
  promotionRunning.value = true
  try {
    promotionResult.value = await knowledgeApi.runPromotion(50)
    ElMessage.success(
      `Promotion 已执行：创建 ${promotionResult.value.createdTasks} 个任务，处理 ${promotionResult.value.processedTasks} 个任务`,
    )
    lowPage.value = 1
    await Promise.all([loadLowKnowledge(), loadReviewCandidates(), loadPromotionDiagnostics()])
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    promotionRunning.value = false
  }
}

async function loadLowKnowledge() {
  lowLoading.value = true
  try {
    const rows = await knowledgeApi.listKnowledge({
      trustLevel: 'LOW',
      limit: lowPageSize + 1,
      offset: (lowPage.value - 1) * lowPageSize,
    })
    lowHasNext.value = rows.length > lowPageSize
    lowKnowledge.value = rows.slice(0, lowPageSize)
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    lowLoading.value = false
  }
}

async function changeLowPage(page: number) {
  lowPage.value = page
  await loadLowKnowledge()
}

async function loadPromotionDiagnostics() {
  diagnosticsLoading.value = true
  try {
    const knowledgeId = parsedDiagnosticKnowledgeId()
    const [tasks, abnormal] = await Promise.all([
      knowledgeApi.listPromotionTasks({
        taskType: 'LOW_TO_MEDIUM',
        knowledgeId,
        limit: 10,
        offset: 0,
      }),
      loadAbnormalKnowledge(knowledgeId),
    ])
    promotionTasks.value = tasks
    abnormalKnowledge.value = abnormal
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    diagnosticsLoading.value = false
  }
}

async function loadAbnormalKnowledge(knowledgeId: number | null) {
  if (knowledgeId) {
    try {
      const item = await knowledgeApi.getKnowledge(knowledgeId)
      return abnormalStatuses.includes(item.status) ? [item] : []
    } catch {
      return []
    }
  }

  const groups = await Promise.all(
    diagnosticTrustLevels.flatMap(trustLevel =>
      abnormalStatuses.map(status =>
        knowledgeApi.listKnowledge({ trustLevel, status, limit: 10, offset: 0 }),
      ),
    ),
  )
  const unique = new Map<number, KnowledgeItem>()
  groups.flat().forEach(item => unique.set(item.id, item))
  return [...unique.values()]
    .sort((left, right) => timestamp(right) - timestamp(left))
    .slice(0, 30)
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

function scopeText(item: KnowledgeItem) {
  return [item.scopeType, item.tenantId, item.projectId, item.userId, item.conversationId]
    .filter(Boolean)
    .join(' / ')
}

function trustTagType(item: KnowledgeItem) {
  if (item.trustLevel === 'HIGH') return 'success'
  if (item.trustLevel === 'MEDIUM') return 'warning'
  return 'info'
}

function statusTagType(item: KnowledgeItem) {
  if (
    item.status === 'REJECTED'
    || item.status === 'CONFLICT'
    || item.status === 'EXPIRED'
    || item.status === 'MERGE_PENDING'
    || item.status === 'INDEX_FAILED'
  ) return 'danger'
  if (item.status === 'LOW_PENDING' || item.status === 'PROMOTION_PENDING' || item.status === 'PROMOTION_RUNNING') {
    return 'warning'
  }
  if (item.status === 'HIGH_ENABLED' || item.status === 'MEDIUM_ENABLED' || item.status === 'LOW_ENABLED') {
    return 'success'
  }
  return 'info'
}

function taskStatusType(task: PromotionTask) {
  if (task.status === 'SUCCESS') return 'success'
  if (task.status === 'FAILED') return 'danger'
  if (task.status === 'RUNNING' || task.status === 'PENDING') return 'warning'
  return 'info'
}

function parsedDiagnosticKnowledgeId() {
  const id = Number(diagnosticKnowledgeId.value)
  return Number.isInteger(id) && id > 0 ? id : null
}

function timestamp(item: KnowledgeItem) {
  return Date.parse(item.updatedAt || item.createdAt || '') || 0
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
        <span class="eyebrow">KNOWLEDGE REVIEW</span>
        <h1>知识审核</h1>
        <p>处理低可信候选的晋升任务，并完成人工终审。</p>
      </div>
      <div class="heading-actions">
        <el-button
          type="primary"
          :icon="PromotionIcon"
          :loading="promotionRunning"
          @click="runPromotionTask"
        >
          执行 Promotion 晋升任务
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="promotionResult"
      class="block-gap"
      type="success"
      :closable="false"
      :title="`上次执行：创建 ${promotionResult.createdTasks} 个任务，处理 ${promotionResult.processedTasks} 个任务`"
    />

    <div class="content-panel block-gap table-panel">
      <div class="section-heading compact-heading">
        <div>
          <span class="eyebrow">PROMOTION TRACE</span>
          <h2>晋升任务记录 / 异常结果</h2>
          <p class="quiet">追踪 LOW_TO_MEDIUM 任务，以及未进入人工审核的晋升结果。</p>
        </div>
        <div class="task-actions">
          <el-input v-model="diagnosticKnowledgeId" placeholder="knowledge_id 可选" clearable />
          <el-button :icon="Refresh" :loading="diagnosticsLoading" @click="loadPromotionDiagnostics">刷新</el-button>
        </div>
      </div>

      <div class="subsection-heading">
        <div>
          <strong>晋升任务记录</strong>
          <small>展示最近 10 条 LOW_TO_MEDIUM 任务。</small>
        </div>
      </div>
      <el-table
        v-loading="diagnosticsLoading"
        :data="promotionTasks"
        empty-text="暂无晋升任务记录"
        row-key="id"
      >
        <el-table-column label="task_id" width="100">
          <template #default="{ row }">{{ row.id }}</template>
        </el-table-column>
        <el-table-column label="knowledge_id" width="130">
          <template #default="{ row }">
            <el-tag type="info">{{ row.knowledgeId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="150">
          <template #default="{ row }">{{ row.taskType }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重试" width="80">
          <template #default="{ row }">{{ row.retryCount }}</template>
        </el-table-column>
        <el-table-column label="错误" min-width="260">
          <template #default="{ row }">
            <span>{{ row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="subsection-heading">
        <div>
          <strong>晋升异常结果</strong>
          <small>包含 REJECTED、CONFLICT、MERGE_PENDING、EXPIRED、INDEX_FAILED。</small>
        </div>
      </div>
      <el-table
        v-loading="diagnosticsLoading"
        :data="abnormalKnowledge"
        empty-text="暂无晋升异常结果"
        row-key="id"
      >
        <el-table-column label="knowledge_id" width="130">
          <template #default="{ row }">
            <el-tag type="info">{{ row.id }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="知识" min-width="320">
          <template #default="{ row }">
            <strong>{{ row.title }}</strong>
            <small class="table-note">{{ row.content }}</small>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="190">
          <template #default="{ row }">
            <el-tag :type="trustTagType(row)">{{ row.trustLevel }}</el-tag>
            <el-tag class="collapse-tag" :type="statusTagType(row)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原因" min-width="220">
          <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
        </el-table-column>
        <el-table-column label="来源" min-width="180">
          <template #default="{ row }">
            {{ row.sourceType || '-' }}
            <small class="table-note">{{ row.sourceRef || '暂无来源引用' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div class="content-panel block-gap table-panel">
      <div class="section-heading compact-heading">
        <div>
          <span class="eyebrow">LOW TRUST</span>
          <h2>低可信知识</h2>
          <p class="quiet">展示低可信知识池中的候选，每页 10 条。</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Refresh" :loading="lowLoading" @click="loadLowKnowledge">刷新</el-button>
        </div>
      </div>

      <el-table
        v-loading="lowLoading"
        class="block-gap"
        :data="lowKnowledge"
        empty-text="暂无低可信知识"
        row-key="id"
      >
        <el-table-column label="knowledge_id" width="130">
          <template #default="{ row }">
            <el-tag type="info">{{ row.id }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="知识" min-width="320">
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
        <el-table-column label="状态" width="190">
          <template #default="{ row }">
            <el-tag :type="trustTagType(row)">{{ row.trustLevel }}</el-tag>
            <el-tag class="collapse-tag" :type="statusTagType(row)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="lowPage"
          :page-size="lowPageSize"
          :total="lowPaginationTotal"
          @current-change="changeLowPage"
        />
      </div>
    </div>

    <div class="content-panel block-gap table-panel">
      <div class="section-heading compact-heading">
        <div>
          <span class="eyebrow">HUMAN REVIEW</span>
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
        row-key="id"
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
            <el-tag :type="trustTagType(row)">{{ row.trustLevel }}</el-tag>
            <el-tag class="collapse-tag" :type="statusTagType(row)">{{ row.status }}</el-tag>
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
