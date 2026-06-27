<script setup lang="ts">
// ECharts 包装组件，统一管理评估台图表渲染。
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'

defineProps<{ option: EChartsOption; height?: string }>()

// 只注册评估台实际用到的 ECharts 能力，控制前端包体积。
use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
</script>

<template>
  <VChart :option="option" autoresize :style="{ height: height || '320px', width: '100%' }" />
</template>
