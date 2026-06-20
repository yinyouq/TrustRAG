<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { evaluationApi } from '@/api'
import type { EvalCompareReport, EvalDataset, EvalReport, EvalRun } from '@/api/types'
import MetricChart from '@/components/MetricChart.vue'
import { validateRunComparison } from '@/utils/comparison'
import { formatPercent, formatSignedPercent } from '@/utils/format'

const datasets = ref<EvalDataset[]>([])
const runs = ref<EvalRun[]>([])
const datasetId = ref<number | null>(null)
const beforeRunId = ref<number | null>(null)
const afterRunId = ref<number | null>(null)
const beforeReport = ref<EvalReport | null>(null)
const afterReport = ref<EvalReport | null>(null)
const compareReport = ref<EvalCompareReport | null>(null)
const loading = ref(false)

const eligibleRuns = computed(() => runs.value.filter(run =>
  run.datasetId === datasetId.value && run.status === 'SUCCEEDED'))
const beforeRuns = computed(() => eligibleRuns.value.filter(run => run.beforeAfterGroup === 'BEFORE'))
const afterRuns = computed(() => eligibleRuns.value.filter(run => run.beforeAfterGroup === 'AFTER'))
const beforeRun = computed(() => runs.value.find(run => run.id === beforeRunId.value) ?? null)
const afterRun = computed(() => runs.value.find(run => run.id === afterRunId.value) ?? null)

const metricRows = computed(() => {
  if (!beforeReport.value || !afterReport.value || !compareReport.value) return []
  return [
    { label: 'Recall@10', before: beforeReport.value.avgRecallAt10, after: afterReport.value.avgRecallAt10, delta: compareReport.value.recallAt10Delta },
    { label: 'MRR', before: beforeReport.value.avgMrr, after: afterReport.value.avgMrr, delta: compareReport.value.mrrDelta },
    { label: 'Faithfulness', before: beforeReport.value.avgFaithfulness, after: afterReport.value.avgFaithfulness, delta: compareReport.value.faithfulnessDelta },
    { label: 'Answer Correctness', before: beforeReport.value.avgAnswerCorrectness, after: afterReport.value.avgAnswerCorrectness, delta: compareReport.value.answerCorrectnessDelta },
    { label: '幻觉率', before: beforeReport.value.avgHallucinationScore, after: afterReport.value.avgHallucinationScore, delta: compareReport.value.hallucinationScoreDelta, inverse: true },
  ]
})

const chartOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis', valueFormatter: value => `${Number(value).toFixed(1)}%` },
  legend: { data: ['Before', 'After'] },
  grid: { left: 45, right: 20, top: 50, bottom: 40 },
  xAxis: { type: 'category', data: metricRows.value.map(row => row.label) },
  yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
  series: [
    { name: 'Before', type: 'bar', data: metricRows.value.map(row => row.before === null ? null : row.before * 100), itemStyle: { color: '#b8aa93' } },
    { name: 'After', type: 'bar', data: metricRows.value.map(row => row.after === null ? null : row.after * 100), itemStyle: { color: '#3d7056' } },
  ],
}))

onMounted(async () => {
  [datasets.value, runs.value] = await Promise.all([
    evaluationApi.listDatasets({ includeDisabled: true }),
    evaluationApi.listRuns(null, 500),
  ])
  const firstEligible = runs.value.find(run => ['BEFORE', 'AFTER'].includes(run.beforeAfterGroup))
  if (firstEligible) datasetId.value = firstEligible.datasetId
})
watch(datasetId, () => {
  beforeRunId.value = null
  afterRunId.value = null
  compareReport.value = null
})

async function compare() {
  const error = validateRunComparison(beforeRun.value, afterRun.value)
  if (error) return ElMessage.warning(error)
  loading.value = true
  try {
    [beforeReport.value, afterReport.value, compareReport.value] = await Promise.all([
      evaluationApi.getReport(beforeRunId.value!),
      evaluationApi.getReport(afterRunId.value!),
      evaluationApi.compareRuns(beforeRunId.value!, afterRunId.value!),
    ])
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '对比失败')
  } finally {
    loading.value = false
  }
}

function conclusion(value: string | null) {
  if (!value) return '后端未生成结论'
  if (value.includes('improved')) return 'After 版本整体提升，可以继续验证并考虑发布。'
  if (value.includes('regressed')) return 'After 版本整体回退，需要检查失败样例和配置差异。'
  return 'Before 与 After 整体变化不显著。'
}
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="eyebrow">BEFORE / AFTER</span><h1>效果对比</h1><p>证明知识或检索配置变化是否真的带来提升。</p></div></div>
    <div class="compare-picker content-panel">
      <el-form label-position="top">
        <div class="form-grid form-grid--3">
          <el-form-item label="测试集"><el-select v-model="datasetId" filterable><el-option v-for="dataset in datasets" :key="dataset.id" :label="dataset.name" :value="dataset.id" /></el-select></el-form-item>
          <el-form-item label="Before 任务"><el-select v-model="beforeRunId" filterable :disabled="!datasetId"><el-option v-for="run in beforeRuns" :key="run.id" :label="run.runName || `任务 #${run.id}`" :value="run.id" /></el-select></el-form-item>
          <el-form-item label="After 任务"><el-select v-model="afterRunId" filterable :disabled="!datasetId"><el-option v-for="run in afterRuns" :key="run.id" :label="run.runName || `任务 #${run.id}`" :value="run.id" /></el-select></el-form-item>
        </div>
      </el-form>
      <el-button type="primary" :loading="loading" @click="compare">生成对比报告</el-button>
    </div>
    <template v-if="compareReport && beforeReport && afterReport">
      <el-alert :title="conclusion(compareReport.conclusion)" type="success" :closable="false" show-icon class="block-gap" />
      <div class="compare-grid block-gap">
        <div class="content-panel"><div class="section-heading"><div><span class="eyebrow">VISUAL CHANGE</span><h2>指标前后变化</h2></div></div><MetricChart :option="chartOption" /></div>
        <div class="content-panel table-panel"><el-table :data="metricRows"><el-table-column prop="label" label="指标" min-width="150" /><el-table-column label="Before" width="100"><template #default="{ row }">{{ formatPercent(row.before) }}</template></el-table-column><el-table-column label="After" width="100"><template #default="{ row }">{{ formatPercent(row.after) }}</template></el-table-column><el-table-column label="变化" width="110"><template #default="{ row }"><strong :class="row.delta === null ? '' : (row.inverse ? row.delta < 0 : row.delta > 0) ? 'delta-good' : row.delta === 0 ? '' : 'delta-bad'">{{ formatSignedPercent(row.delta) }}</strong></template></el-table-column></el-table></div>
      </div>
    </template>
    <el-empty v-else description="选择同一测试集的 BEFORE 与 AFTER 任务开始对比" />
  </section>
</template>
