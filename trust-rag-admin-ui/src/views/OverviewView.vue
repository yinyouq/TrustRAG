<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, DataAnalysis } from '@element-plus/icons-vue'
import { evaluationApi } from '@/api'
import type { EvalReport, EvalRun } from '@/api/types'
import MetricCard from '@/components/MetricCard.vue'
import RunStatusTag from '@/components/RunStatusTag.vue'
import { formatDateTime, formatDuration, formatPercent } from '@/utils/format'

const loading = ref(true)
const runs = ref<EvalRun[]>([])
const report = ref<EvalReport | null>(null)
const error = ref('')

const activeRuns = computed(() => runs.value.filter(run => ['PENDING', 'RUNNING'].includes(run.status)).length)
const latestRun = computed(() => runs.value.find(run => run.status === 'SUCCEEDED') ?? null)
const generationEnabled = computed(() => report.value?.avgFaithfulness !== null && report.value?.avgFaithfulness !== undefined)
const qualityScore = computed(() => {
  if (!report.value) return '--'
  const values = [report.value.avgRecallAt10, report.value.avgMrr]
  if (generationEnabled.value) {
    values.push(
      report.value.avgFaithfulness,
      report.value.avgAnswerCorrectness,
      report.value.avgAnswerRelevance,
    )
  }
  const present = values.filter((value): value is number => value !== null)
  if (!present.length) return '--'
  return (present.reduce((sum, value) => sum + value, 0) / present.length * 100).toFixed(1)
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    runs.value = await evaluationApi.listRuns(null, 8)
    const successful = runs.value.find(run => run.status === 'SUCCEEDED')
    report.value = successful ? await evaluationApi.getReport(successful.id) : null
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '加载评估概览失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section v-loading="loading" class="overview-page">
    <div class="hero-row">
      <div>
        <span class="eyebrow">QUALITY WORKSPACE</span>
        <h1>今天的 RAG 质量如何？</h1>
        <p>用可复现的检索、生成与知识治理指标回答这个问题。</p>
      </div>
      <RouterLink class="primary-link" to="/eval/runs">
        运行一次评估 <el-icon><ArrowRight /></el-icon>
      </RouterLink>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="block-gap" />

    <div v-if="!loading && !latestRun" class="empty-panel">
      <el-icon :size="34"><DataAnalysis /></el-icon>
      <h2>尚无评估结果</h2>
      <p>创建测试集并运行第一轮基线评估，质量趋势才有可靠起点。</p>
      <RouterLink class="text-link" to="/eval/datasets">从测试集开始</RouterLink>
    </div>

    <template v-else-if="report">
      <div class="quality-grid">
        <article class="quality-score-card">
          <span>{{ generationEnabled ? '综合可信度' : '检索质量' }}</span>
          <strong>{{ qualityScore }}</strong>
          <p v-if="generationEnabled">检索与生成指标的当前均值</p>
          <p v-else>生成质量未启用，仅基于 Recall@10 与 MRR</p>
        </article>
        <article class="attention-card">
          <span>需要关注</span>
          <strong>{{ report.failedCount }} 个失败样例</strong>
          <p>{{ report.successCount }} / {{ report.totalCount }} 条样例成功完成</p>
        </article>
      </div>

      <div class="metric-grid">
        <MetricCard label="Recall@10" :value="formatPercent(report.avgRecallAt10)" note="Top 10 标准知识召回" />
        <MetricCard label="MRR" :value="formatPercent(report.avgMrr)" note="首个相关结果排名" />
        <MetricCard
          label="Faithfulness"
          :value="formatPercent(report.avgFaithfulness)"
          :note="generationEnabled ? '回答上下文忠实度' : '生成质量未启用'"
        />
        <MetricCard
          label="幻觉率"
          :value="formatPercent(report.avgHallucinationScore)"
          :tone="report.avgHallucinationScore !== null && report.avgHallucinationScore > 0.2 ? 'danger' : 'normal'"
          note="1 - Faithfulness"
        />
      </div>
    </template>

    <section v-if="runs.length" class="content-panel block-gap">
      <div class="section-heading">
        <div><span class="eyebrow">RECENT ACTIVITY</span><h2>最近评估活动</h2></div>
        <span class="quiet">{{ activeRuns }} 个任务正在执行</span>
      </div>
      <div class="activity-list">
        <div v-for="run in runs.slice(0, 5)" :key="run.id" class="activity-row">
          <div><strong>{{ run.runName || `评估任务 #${run.id}` }}</strong><small>{{ formatDateTime(run.createdAt) }}</small></div>
          <span>{{ run.successCount }} / {{ run.totalCount }}</span>
          <span>{{ formatDuration(run.finishedAt && run.startedAt ? new Date(run.finishedAt).getTime() - new Date(run.startedAt).getTime() : null) }}</span>
          <RunStatusTag :status="run.status" />
        </div>
      </div>
    </section>
  </section>
</template>
