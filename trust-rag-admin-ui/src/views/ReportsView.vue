<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { evaluationApi } from '@/api'
import type { EvalReport, EvalResult, EvalRun } from '@/api/types'
import MetricCard from '@/components/MetricCard.vue'
import MetricChart from '@/components/MetricChart.vue'
import ResultDetailDrawer from '@/components/ResultDetailDrawer.vue'
import { formatDuration, formatPercent } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const runs = ref<EvalRun[]>([])
const selectedRunId = ref<number | null>(Number(route.query.runId) || null)
const report = ref<EvalReport | null>(null)
const results = ref<EvalResult[]>([])
const selectedResult = ref<EvalResult | null>(null)
const detailVisible = ref(false)
const loading = ref(false)

const chartOption = computed<EChartsOption>(() => {
  if (!report.value) return {}
  const entries = [
    ['Recall@5', report.value.avgRecallAt5], ['Recall@10', report.value.avgRecallAt10],
    ['Precision@5', report.value.avgPrecisionAt5], ['Precision@10', report.value.avgPrecisionAt10],
    ['MRR', report.value.avgMrr], ['NDCG@10', report.value.avgNdcgAt10],
    ['Faithfulness', report.value.avgFaithfulness], ['Correctness', report.value.avgAnswerCorrectness],
    ['Relevance', report.value.avgAnswerRelevance],
  ] as Array<[string, number | null]>
  return {
    tooltip: { trigger: 'axis', valueFormatter: value => `${Number(value).toFixed(1)}%` },
    grid: { left: 45, right: 20, top: 20, bottom: 70 },
    xAxis: { type: 'category', data: entries.map(([name]) => name), axisLabel: { rotate: 32 } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
    series: [{ type: 'bar', data: entries.map(([, value]) => value === null ? null : value * 100), itemStyle: { color: '#46725c', borderRadius: [5, 5, 0, 0] } }],
  }
})

onMounted(async () => {
  runs.value = (await evaluationApi.listRuns(null, 200)).filter(run => ['SUCCEEDED', 'FAILED'].includes(run.status))
  if (!selectedRunId.value && runs.value.length) selectedRunId.value = runs.value[0].id
})
watch(selectedRunId, value => {
  if (value) {
    void router.replace({ query: { runId: value } })
    void loadReport(value)
  }
}, { immediate: true })

async function loadReport(runId: number) {
  loading.value = true
  try {
    [report.value, results.value] = await Promise.all([
      evaluationApi.getReport(runId),
      evaluationApi.listResults(runId),
    ])
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '报告加载失败')
  } finally {
    loading.value = false
  }
}

function openDetail(result: EvalResult) {
  selectedResult.value = result
  detailVisible.value = true
}
</script>

<template>
  <section v-loading="loading">
    <div class="page-heading">
      <div><span class="eyebrow">REPORTS</span><h1>评估报告</h1><p>同时查看确定性检索指标与 LLM Judge 生成指标。</p></div>
      <el-select v-model="selectedRunId" placeholder="选择评估任务" filterable class="run-selector"><el-option v-for="run in runs" :key="run.id" :label="run.runName || `任务 #${run.id}`" :value="run.id" /></el-select>
    </div>

    <template v-if="report">
      <div class="metric-grid report-metrics">
        <MetricCard label="Recall@10" :value="formatPercent(report.avgRecallAt10)" />
        <MetricCard label="MRR" :value="formatPercent(report.avgMrr)" />
        <MetricCard label="Faithfulness" :value="formatPercent(report.avgFaithfulness)" :note="report.avgFaithfulness === null ? 'Judge 未启用' : ''" />
        <MetricCard label="Answer Correctness" :value="formatPercent(report.avgAnswerCorrectness)" />
        <MetricCard label="Answer Relevance" :value="formatPercent(report.avgAnswerRelevance)" />
        <MetricCard label="幻觉率" :value="formatPercent(report.avgHallucinationScore)" :tone="report.avgHallucinationScore !== null && report.avgHallucinationScore > .2 ? 'danger' : 'normal'" />
        <MetricCard label="平均耗时" :value="formatDuration(report.avgLatencyMs)" />
        <MetricCard label="P90 耗时" :value="formatDuration(report.p90LatencyMs)" />
      </div>
      <div class="content-panel block-gap"><div class="section-heading"><div><span class="eyebrow">METRICS</span><h2>指标分布</h2></div><span class="quiet">成功 {{ report.successCount }} / {{ report.totalCount }}</span></div><MetricChart :option="chartOption" /></div>
      <div class="content-panel block-gap table-panel">
        <div class="section-heading"><div><span class="eyebrow">CASE DETAILS</span><h2>逐题明细</h2></div><span class="quiet">点击行查看回答与 Judge 原始输出</span></div>
        <el-table :data="results" class="block-gap" empty-text="暂无逐题结果" @row-click="openDetail">
          <el-table-column prop="question" label="问题" min-width="300" show-overflow-tooltip />
          <el-table-column label="Recall@10" width="110"><template #default="{ row }">{{ formatPercent(row.recallAt10) }}</template></el-table-column>
          <el-table-column label="MRR" width="100"><template #default="{ row }">{{ formatPercent(row.mrr) }}</template></el-table-column>
          <el-table-column label="Faithfulness" width="130"><template #default="{ row }">{{ formatPercent(row.faithfulness) }}</template></el-table-column>
          <el-table-column label="Correctness" width="120"><template #default="{ row }">{{ formatPercent(row.answerCorrectness) }}</template></el-table-column>
          <el-table-column label="耗时" width="100"><template #default="{ row }">{{ formatDuration(row.latencyMs) }}</template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'SUCCEEDED' ? 'success' : 'danger'">{{ row.status === 'SUCCEEDED' ? '成功' : '失败' }}</el-tag></template></el-table-column>
        </el-table>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="请选择一个已完成的评估任务" />
    <ResultDetailDrawer v-model="detailVisible" :result="selectedResult" />
  </section>
</template>
