<script setup lang="ts">
// 指标卡片组件，用统一样式展示单个评估指标。
withDefaults(defineProps<{
  label: string
  value: string
  note?: string
  tone?: 'normal' | 'good' | 'warning' | 'danger'
  clickable?: boolean
  expanded?: boolean
}>(), {
  note: '',
  tone: 'normal',
  clickable: false,
  expanded: false,
})
const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <article
    class="metric-card"
    :class="[`metric-card--${tone}`, { 'metric-card--clickable': clickable }]"
    :role="clickable ? 'button' : undefined"
    :tabindex="clickable ? 0 : undefined"
    :aria-expanded="clickable ? expanded : undefined"
    @click="clickable && emit('click')"
    @keydown.enter.prevent="clickable && emit('click')"
    @keydown.space.prevent="clickable && emit('click')"
  >
    <span class="metric-label">{{ label }}</span>
    <strong class="metric-value">{{ value }}</strong>
    <small v-if="note">{{ note }}</small>
  </article>
</template>
