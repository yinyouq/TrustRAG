<script setup lang="ts">
// 检索基础设施内存页，只展示 Milvus 与 OpenSearch 的容器指标。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { infrastructureApi } from '@/api'
import type { InfrastructureMemorySnapshot } from '@/api/types'
import MetricCard from '@/components/MetricCard.vue'
import MetricChart from '@/components/MetricChart.vue'
import { formatBytes, formatDateTime } from '@/utils/format'

const loading = ref(false)
const snapshot = ref<InfrastructureMemorySnapshot | null>(null)
const windowMinutes = ref(60)
let refreshTimer: number | undefined

const chartOption = computed<EChartsOption>(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: value => formatBytes(Number(value)),
  },
  grid: { left: 62, right: 26, top: 30, bottom: 42 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: snapshot.value?.workingSetTrend.map(point => formatDateTime(point.timestamp)) ?? [],
  },
  yAxis: {
    type: 'value',
    axisLabel: { formatter: value => formatBytes(Number(value)) },
  },
  series: [{
    name: 'RAG 检索引擎 Working Set',
    type: 'line',
    smooth: true,
    showSymbol: false,
    areaStyle: { color: 'rgba(49, 94, 73, .12)' },
    lineStyle: { color: '#315e49', width: 2 },
    data: snapshot.value?.workingSetTrend.map(point => point.workingSetBytes) ?? [],
  }],
}))

const statusType = computed(() => {
  switch (snapshot.value?.status) {
    case 'AVAILABLE': return 'success'
    case 'PARTIAL': return 'warning'
    case 'DISABLED': return 'info'
    default: return 'danger'
  }
})

onMounted(() => {
  load()
  refreshTimer = window.setInterval(() => load(true), 15_000)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})

async function load(silent = false) {
  if (!silent) {
    loading.value = true
  }
  try {
    snapshot.value = await infrastructureApi.getMemorySnapshot(windowMinutes.value)
  } catch (reason) {
    if (!silent) {
      ElMessage.error(reason instanceof Error ? reason.message : '基础设施内存加载失败')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section v-loading="loading">
    <div class="page-heading">
      <div>
        <span class="eyebrow">RETRIEVAL INFRASTRUCTURE</span>
        <h1>基础设施运行内存</h1>
        <p>仅统计 Milvus 与 OpenSearch 容器，不包含宿主 Java、MySQL、etcd、MinIO 或模型服务。</p>
      </div>
      <div class="heading-actions">
        <el-select v-model="windowMinutes" aria-label="峰值统计窗口" @change="load()">
          <el-option :value="15" label="最近 15 分钟" />
          <el-option :value="60" label="最近 1 小时" />
          <el-option :value="360" label="最近 6 小时" />
          <el-option :value="1440" label="最近 24 小时" />
        </el-select>
        <el-button @click="load()">刷新</el-button>
      </div>
    </div>

    <template v-if="snapshot">
      <div class="memory-status content-panel" :class="`memory-status--${snapshot.status.toLowerCase()}`">
        <el-tag :type="statusType">{{ snapshot.status }}</el-tag>
        <span>{{ snapshot.message }}</span>
        <small>采样时间：{{ formatDateTime(snapshot.sampledAt) }}；页面每 15 秒自动刷新。</small>
      </div>

      <div class="metric-grid memory-metrics">
        <MetricCard label="当前检索引擎内存" :value="formatBytes(snapshot.workingSetBytes)" note="Milvus + OpenSearch Working Set" />
        <MetricCard :label="`${snapshot.windowMinutes} 分钟峰值`" :value="formatBytes(snapshot.peakWorkingSetBytes)" note="同一时刻两个引擎之和" />
        <MetricCard label="当前 RSS" :value="formatBytes(snapshot.rssBytes)" note="用于诊断进程常驻页" />
        <MetricCard label="纳入服务" :value="`${snapshot.services.filter(item => item.available).length} / 2`" note="Milvus、OpenSearch" :tone="snapshot.status === 'AVAILABLE' ? 'good' : 'warning'" />
      </div>

      <div class="content-panel block-gap">
        <div class="section-heading">
          <div><span class="eyebrow">WORKING SET TREND</span><h2>检索引擎内存趋势</h2></div>
          <span class="quiet">统计窗口：最近 {{ snapshot.windowMinutes }} 分钟</span>
        </div>
        <MetricChart v-if="snapshot.workingSetTrend.length" :option="chartOption" height="340px" />
        <el-empty v-else description="当前窗口尚无可用的 Working Set 时序数据" />
      </div>

      <div class="content-panel block-gap table-panel">
        <div class="section-heading">
          <div><span class="eyebrow">SERVICE BREAKDOWN</span><h2>检索服务明细</h2></div>
          <span class="quiet">JVM Heap 等内部指标只用于排障，不计入总值</span>
        </div>
        <el-table :data="snapshot.services" empty-text="暂无服务指标">
          <el-table-column prop="displayName" label="服务" min-width="160" />
          <el-table-column label="当前 Working Set" min-width="180">
            <template #default="{ row }">{{ row.available ? formatBytes(row.workingSetBytes) : '--' }}</template>
          </el-table-column>
          <el-table-column :label="`${snapshot.windowMinutes} 分钟峰值`" min-width="170">
            <template #default="{ row }">{{ row.available ? formatBytes(row.peakWorkingSetBytes) : '--' }}</template>
          </el-table-column>
          <el-table-column label="RSS" min-width="140">
            <template #default="{ row }">{{ row.available ? formatBytes(row.rssBytes) : '--' }}</template>
          </el-table-column>
          <el-table-column label="采集状态" min-width="120">
            <template #default="{ row }"><el-tag :type="row.available ? 'success' : 'warning'">{{ row.available ? '已采集' : '未采集' }}</el-tag></template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </section>
</template>
