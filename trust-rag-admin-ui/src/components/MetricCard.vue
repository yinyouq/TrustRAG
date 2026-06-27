<script setup lang="ts">
// 指标卡片组件，用统一样式展示单个评估指标。
withDefaults(defineProps<{
  label: string
  value: string
  note?: string
  tone?: 'normal' | 'good' | 'warning' | 'danger'
}>(), {
  note: '',
  tone: 'normal',
})
</script>

<template>
  <article class="metric-card" :class="`metric-card--${tone}`">
    <span class="metric-label">{{ label }}</span>
    <strong class="metric-value">{{ value }}</strong>
    <small v-if="note">{{ note }}</small>
  </article>
</template>
