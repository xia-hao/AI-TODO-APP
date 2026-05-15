<template>
  <div class="chart-card">
    <h3>{{ $t('dashboard.projectRanking') }}</h3>
    <div ref="chartRef" style="height: 250px"></div>
    <el-empty v-if="!data.length" :description="$t('dashboard.noProjectData')" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import * as echarts from 'echarts'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const props = defineProps<{ data: any[] }>()
const chartRef = ref<HTMLElement>()

function renderChart() {
  if (!chartRef.value || !props.data.length) return
  const sorted = [...props.data].sort((a, b) => (b.rate || 0) - (a.rate || 0)).slice(0, 10)
  const instance = echarts.init(chartRef.value)
  instance.setOption({
    tooltip: { trigger: 'axis', formatter: (params: any) => {
      const d = params[0]
      return `${d.name}<br/>${t('dashboard.completionRate')} ${(d.value * 100).toFixed(0)}%<br/>${d.data.completed}/${d.data.total}`
    }},
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', max: 1, axisLabel: { formatter: (v: number) => (v * 100).toFixed(0) + '%' } },
    yAxis: { type: 'category', data: sorted.map(p => p.projectName), axisLabel: { fontSize: 12 } },
    series: [{
      type: 'bar',
      data: sorted.map(p => ({ value: p.rate || 0, data: p })),
      itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
        { offset: 0, color: '#409eff' }, { offset: 1, color: '#67c23a' }])
      },
      label: { show: true, position: 'right', formatter: (p: any) => (p.value * 100).toFixed(0) + '%' }
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
