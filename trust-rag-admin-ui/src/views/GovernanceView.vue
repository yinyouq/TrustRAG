<script setup lang="ts">
// 治理指标页，展示候选批准率、污染率和缺口解决趋势。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { evaluationApi } from '@/api'
import type { EvalGovernanceSnapshot } from '@/api/types'
import MetricCard from '@/components/MetricCard.vue'
import MetricChart from '@/components/MetricChart.vue'
import { useAppStore } from '@/stores/app'
import { formatPercent } from '@/utils/format'

const store = useAppStore()
const loading = ref(false)
const capturing = ref(false)
const summary = ref<EvalGovernanceSnapshot | null>(null)
const trend = ref<EvalGovernanceSnapshot[]>([])
const filters = reactive({ tenantId: store.tenantId, projectId: store.projectId })
const dateRange = ref<[string, string] | null>(null)

const chartOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis', valueFormatter: value => `${Number(value).toFixed(1)}%` },
  legend: { top: 0 },
  grid: { left: 45, right: 24, top: 54, bottom: 36 },
  xAxis: { type: 'category', data: trend.value.map(item => item.snapshotDate) },
  yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
  series: [
    series('候选通过率', 'candidateApprovalRate'),
    series('知识复用率', 'knowledgeReuseRate'),
    series('污染率', 'contaminationRate'),
    series('隐私泄漏率', 'privacyLeakageRate'),
    series('缺口解决率', 'gapResolveRate'),
  ],
}))

onMounted(load)

function series(name: string, key: keyof EvalGovernanceSnapshot) {
  return {
    name,
    type: 'line' as const,
    smooth: true,
    connectNulls: false,
    data: trend.value.map(item => {
      const value = item[key]
      return typeof value === 'number' ? value * 100 : null
    }),
  }
}

async function load() {
  loading.value = true
  try {
    const scope = { tenantId: filters.tenantId || null, projectId: filters.projectId || null }
    ;[summary.value, trend.value] = await Promise.all([
      evaluationApi.getGovernanceSummary(scope),
      evaluationApi.getGovernanceTrend({
        ...scope,
        from: dateRange.value?.[0] ?? null,
        to: dateRange.value?.[1] ?? null,
      }),
    ])
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '治理指标加载失败')
  } finally {
    loading.value = false
  }
}

async function capture() {
  capturing.value = true
  try {
    await evaluationApi.captureGovernanceSnapshot({
      tenantId: filters.tenantId || null,
      projectId: filters.projectId || null,
    })
    ElMessage.success('治理快照已生成')
    await load()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '快照生成失败')
  } finally {
    capturing.value = false
  }
}
</script>

<template>
  <section v-loading="loading">
    <div class="page-heading"><div><span class="eyebrow">KNOWLEDGE GOVERNANCE</span><h1>知识治理看板</h1><p>判断知识飞轮是否健康，而不只看单次问答质量。</p></div><el-button type="primary" :loading="capturing" @click="capture">生成当前快照</el-button></div>
    <div class="toolbar content-panel toolbar--wide">
      <el-input v-model="filters.tenantId" placeholder="租户 ID" clearable />
      <el-input v-model="filters.projectId" placeholder="项目 ID" clearable />
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-button @click="load">应用筛选</el-button>
    </div>
    <div v-if="summary" class="metric-grid governance-metrics">
      <MetricCard label="候选通过率" :value="formatPercent(summary.candidateApprovalRate)" :note="`${summary.approvedCandidateCount} / ${summary.totalCandidateCount} 个候选`" />
      <MetricCard label="知识复用率" :value="formatPercent(summary.knowledgeReuseRate)" />
      <MetricCard label="污染率" :value="formatPercent(summary.contaminationRate)" :tone="summary.contaminationRate !== null && summary.contaminationRate > .05 ? 'danger' : 'normal'" note="当前为低可信知识使用率近似" />
      <MetricCard label="隐私泄漏率" :value="formatPercent(summary.privacyLeakageRate)" :tone="summary.privacyLeakageRate ? 'danger' : 'normal'" />
      <MetricCard label="缺口解决率" :value="formatPercent(summary.gapResolveRate)" />
    </div>
    <div class="content-panel block-gap"><div class="section-heading"><div><span class="eyebrow">DAILY TREND</span><h2>治理指标趋势</h2></div><span class="quiet">{{ trend.length }} 个快照</span></div><MetricChart v-if="trend.length" :option="chartOption" height="380px" /><el-empty v-else description="尚无治理快照" /></div>
  </section>
</template>
