<template>
  <div class="chart-card">
    <h3>{{ $t('dashboard.assigneeStats') }}</h3>
    <div ref="chartRef" style="height: 250px"></div>
    <el-empty v-if="!data.length" :description="$t('dashboard.noAssigneeData')" />
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
  const instance = echarts.init(chartRef.value)
  instance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: props.data.map(d => ({ name: d.displayName || t('dashboard.userFallback') + d.userId, value: d.count })),
      label: { show: true, formatter: '{b}: {c}' },
      itemStyle: { borderRadius: 4 }
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
