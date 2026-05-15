<template>
  <div class="chart-card">
    <h3>{{ $t('dashboard.tagHeatmap') }}</h3>
    <div ref="chartRef" style="height: 250px"></div>
    <el-empty v-if="!data.length" :description="$t('dashboard.noTagData')" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts'

const { t } = useI18n()
const props = defineProps<{ data: any[] }>()
const chartRef = ref<HTMLElement>()

function renderChart() {
  if (!chartRef.value || !props.data.length) return
  const sorted = [...props.data].sort((a, b) => b.count - a.count).slice(0, 20)
  const instance = echarts.init(chartRef.value)
  instance.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: sorted.map(d => d.tagName || t('tag.unknown')), axisLabel: { fontSize: 12 } },
    series: [{
      type: 'bar',
      data: sorted.map(d => ({
        value: d.count,
        itemStyle: { color: d.tagColor || '#409eff' }
      })),
      label: { show: true, position: 'right' }
    }]
  })
  window.addEventListener('resize', () => instance.resize())
}

onMounted(renderChart)
watch(() => props.data, renderChart, { deep: true })
</script>

<style scoped>
.chart-card { background: var(--el-bg-color); border-radius: 8px; padding: 16px; box-shadow: 0 2px 8px var(--el-box-shadow-lighter); }
.chart-card h3 { margin: 0 0 12px; font-size: 15px; color: var(--el-text-color-primary); }
</style>
