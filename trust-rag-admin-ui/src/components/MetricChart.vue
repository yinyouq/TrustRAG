<script setup lang="ts">
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'

defineProps<{ option: EChartsOption; height?: string }>()

use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
</script>

<template>
  <VChart :option="option" autoresize :style="{ height: height || '320px', width: '100%' }" />
</template>
