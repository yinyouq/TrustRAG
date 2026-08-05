<script setup lang="ts">
// 逐题结果详情抽屉，展示答案、指标和 LLM Judge 诊断。
import { ref, watch } from 'vue'
import { evaluationApi } from '@/api'
import type { EvalJudgeDetail, EvalResult } from '@/api/types'
import { formatDuration, formatPercent } from '@/utils/format'

const props = withDefaults(defineProps<{
  modelValue: boolean
  result?: EvalResult | null
}>(), { result: null })
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const judgeDetails = ref<EvalJudgeDetail[]>([])
const loading = ref(false)

watch([() => props.modelValue, () => props.result?.id], async ([visible]) => {
  if (!visible || !props.result) return
  loading.value = true
  try {
    // 详情抽屉打开时再加载 Judge 诊断，避免列表页一次性拉取大文本 Prompt。
    judgeDetails.value = await evaluationApi.listJudgeDetails(props.result.id)
  } finally {
    loading.value = false
  }
})

const judgeLabels: Record<string, string> = {
  FAITHFULNESS: 'Faithfulness',
  ANSWER_CORRECTNESS: 'Answer Correctness',
  ANSWER_RELEVANCE: 'Answer Relevance',
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="逐题评估诊断"
    size="min(760px, 96vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="result">
      <div class="detail-block"><span>问题</span><p>{{ result.question }}</p></div>
      <div class="answer-grid">
        <div class="detail-block"><span>标准答案</span><p>{{ result.expectedAnswer || '未设置' }}</p></div>
        <div class="detail-block"><span>实际答案</span><p>{{ result.answer || result.errorMessage || '未生成' }}</p></div>
      </div>
      <div class="mini-metrics">
        <div><span>Recall@10</span><strong>{{ formatPercent(result.recallAt10) }}</strong></div>
        <div><span>MRR</span><strong>{{ formatPercent(result.mrr) }}</strong></div>
        <div><span>Faithfulness</span><strong>{{ formatPercent(result.faithfulness) }}</strong></div>
        <div><span>耗时</span><strong>{{ formatDuration(result.latencyMs) }}</strong></div>
      </div>
      <section v-loading="loading" class="block-gap">
        <div class="subsection-heading"><div><strong>LLM Judge 详情</strong><small>保存的 Prompt、原因与原始输出</small></div></div>
        <el-empty v-if="!loading && !judgeDetails.length" description="Judge 未启用或未保存详情" :image-size="70" />
        <el-collapse v-else>
          <el-collapse-item v-for="detail in judgeDetails" :key="detail.id" :name="detail.id">
            <template #title>
              <strong>{{ judgeLabels[detail.judgeType] }}</strong>
              <el-tag :type="detail.passed ? 'success' : 'danger'" size="small" class="collapse-tag">
                {{ formatPercent(detail.score) }}
              </el-tag>
            </template>
            <p><strong>模型：</strong>{{ detail.model || '--' }}</p>
            <p><strong>Judge 耗时：</strong>{{ formatDuration(detail.judgeLatencyMs) }}（重试 {{ detail.retryCount }} 次）</p>
            <p><strong>原因：</strong>{{ detail.reason || '--' }}</p>
            <details v-if="detail.prompt"><summary>Judge Prompt</summary><pre>{{ detail.prompt }}</pre></details>
            <details v-if="detail.rawOutput"><summary>Judge 原始输出</summary><pre>{{ detail.rawOutput }}</pre></details>
            <el-alert v-if="!detail.prompt && !detail.rawOutput" title="当前配置未保存 Prompt 与原始输出" type="info" :closable="false" />
          </el-collapse-item>
        </el-collapse>
      </section>
    </template>
  </el-drawer>
</template>
