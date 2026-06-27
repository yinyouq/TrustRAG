<script setup lang="ts">
// 评估运行页，创建任务、观察进度并支持取消运行。
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, RefreshRight, VideoPause } from '@element-plus/icons-vue'
import { evaluationApi } from '@/api'
import type { BeforeAfterGroup, EvalDataset, EvalRun, EvalRunPayload, EvalRunType } from '@/api/types'
import RunStatusTag from '@/components/RunStatusTag.vue'
import { createRunPoller } from '@/composables/useRunPolling'
import { formatDateTime, formatDuration } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const datasets = ref<EvalDataset[]>([])
const runs = ref<EvalRun[]>([])
const loading = ref(false)
const creating = ref(false)
const dialogVisible = ref(false)
const selectedDatasetId = ref<number | null>(Number(route.query.datasetId) || null)
const form = reactive({
  datasetId: selectedDatasetId.value,
  runName: '',
  runType: 'MANUAL' as EvalRunType,
  beforeAfterGroup: 'NORMAL' as BeforeAfterGroup,
  createdBy: '',
})
const hasActiveRuns = computed(() => runs.value.some(run => run.status === 'PENDING' || run.status === 'RUNNING'))
const visibleRuns = computed(() => selectedDatasetId.value
  ? runs.value.filter(run => run.datasetId === selectedDatasetId.value)
  : runs.value)
const poller = createRunPoller(loadRuns, () => hasActiveRuns.value, 4_000)

onMounted(async () => {
  try {
    datasets.value = await evaluationApi.listDatasets({ includeDisabled: true })
  } catch (reason) {
    ElMessage.error(message(reason))
  }
  poller.start()
})
onUnmounted(() => poller.stop())

async function loadRuns() {
  loading.value = true
  try {
    runs.value = await evaluationApi.listRuns(null, 200)
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    datasetId: selectedDatasetId.value ?? datasets.value[0]?.id ?? null,
    runName: '', runType: 'MANUAL', beforeAfterGroup: 'NORMAL', createdBy: '',
  })
  dialogVisible.value = true
}

async function createRun() {
  if (!form.datasetId) return ElMessage.warning('请选择测试集')
  creating.value = true
  try {
    const payload: EvalRunPayload = {
      datasetId: form.datasetId,
      runName: form.runName.trim() || null,
      runType: form.runType,
      beforeAfterGroup: form.beforeAfterGroup,
      createdBy: form.createdBy.trim() || null,
      async: true,
    }
    await evaluationApi.createRun(payload)
    dialogVisible.value = false
    ElMessage.success('评估任务已创建')
    poller.stop()
    poller.start()
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    creating.value = false
  }
}

async function cancel(run: EvalRun) {
  try {
    await evaluationApi.cancelRun(run.id)
    ElMessage.success('已发送取消请求')
    await loadRuns()
  } catch (reason) {
    ElMessage.error(message(reason))
  }
}

function progress(run: EvalRun) {
  return run.totalCount ? Math.round((run.successCount + run.failedCount) / run.totalCount * 100) : 0
}

function elapsed(run: EvalRun) {
  const start = run.startedAt ? new Date(run.startedAt).getTime() : null
  const end = run.finishedAt ? new Date(run.finishedAt).getTime() : Date.now()
  return start ? formatDuration(end - start) : '--'
}

function message(reason: unknown) { return reason instanceof Error ? reason.message : '操作失败' }
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="eyebrow">EVALUATION RUNS</span><h1>评估任务</h1><p>运行、观察并终止可复现的评估作业。</p></div>
      <div class="heading-actions"><el-button :icon="RefreshRight" @click="loadRuns">刷新</el-button><el-button type="primary" :icon="Plus" @click="openCreate">新建评估</el-button></div>
    </div>
    <div class="toolbar content-panel">
      <el-select v-model="selectedDatasetId" placeholder="全部测试集" clearable filterable>
        <el-option v-for="dataset in datasets" :key="dataset.id" :label="dataset.name" :value="dataset.id" />
      </el-select>
      <span class="quiet">{{ hasActiveRuns ? '活跃任务将每 4 秒自动刷新' : '当前没有活跃任务' }}</span>
    </div>
    <div class="content-panel block-gap table-panel">
      <el-table v-loading="loading" :data="visibleRuns" empty-text="暂无评估任务">
        <el-table-column label="任务" min-width="230"><template #default="{ row }"><strong>{{ row.runName || `评估任务 #${row.id}` }}</strong><small class="table-note">{{ formatDateTime(row.createdAt) }} · {{ row.runType }}</small></template></el-table-column>
        <el-table-column label="分组" width="100"><template #default="{ row }"><el-tag effect="plain">{{ row.beforeAfterGroup }}</el-tag></template></el-table-column>
        <el-table-column label="进度" min-width="220"><template #default="{ row }"><el-progress :percentage="progress(row)" :status="row.status === 'FAILED' ? 'exception' : row.status === 'SUCCEEDED' ? 'success' : undefined" /><small class="table-note">成功 {{ row.successCount }} · 失败 {{ row.failedCount }} · 共 {{ row.totalCount }}</small></template></el-table-column>
        <el-table-column label="耗时" width="100"><template #default="{ row }">{{ elapsed(row) }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><RunStatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="180" fixed="right"><template #default="{ row }"><el-button v-if="['PENDING','RUNNING'].includes(row.status)" link type="danger" :icon="VideoPause" @click="cancel(row)">取消</el-button><el-button v-if="['SUCCEEDED','FAILED'].includes(row.status)" link type="primary" @click="router.push({ path: '/eval/reports', query: { runId: row.id } })">查看报告</el-button></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" title="创建评估任务" width="min(560px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="测试集" required><el-select v-model="form.datasetId" filterable><el-option v-for="dataset in datasets" :key="dataset.id" :label="dataset.name" :value="dataset.id" /></el-select></el-form-item>
        <el-form-item label="任务名称"><el-input v-model="form.runName" placeholder="例如：Hybrid Search v2 回归" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="任务类型"><el-select v-model="form.runType"><el-option v-for="type in ['MANUAL','SCHEDULED','BEFORE_AFTER','REGRESSION']" :key="type" :label="type" :value="type" /></el-select></el-form-item>
          <el-form-item label="Before / After"><el-select v-model="form.beforeAfterGroup"><el-option v-for="group in ['NORMAL','BEFORE','AFTER']" :key="group" :label="group" :value="group" /></el-select></el-form-item>
        </div>
        <el-form-item label="创建人"><el-input v-model="form.createdBy" clearable /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="creating" @click="createRun">开始评估</el-button></template>
    </el-dialog>
  </section>
</template>
