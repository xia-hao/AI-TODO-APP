<template>
  <div class="dashboard">
    <h2>{{ $t('app.dashboard') }}</h2>
    <StatsCards v-if="overview" :data="overview" />
    <el-divider />
    <h3>{{ $t('dashboard.trend') }}</h3>
    <div ref="chart" style="height:300px"></div>
    <el-divider />
    <h3>{{ $t('dashboard.upcoming') }}</h3>
    <el-table :data="upcoming" style="width:100%">
      <el-table-column prop="text" :label="$t('dashboard.taskLabel')" />
      <el-table-column prop="dueDate" :label="$t('dashboard.dueDateLabel')" />
      <el-table-column :label="$t('dashboard.priorityLabel')">
        <template #default="{ row }">
          <el-tag :type="row.priority === 'high' ? 'danger' : row.priority === 'medium' ? 'warning' : 'primary'">
            {{ ({high: $t('todo.high'), medium: $t('todo.medium'), low: $t('todo.low')} as any)[row.priority] }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-divider />
    <div class="chart-grid">
      <ProjectRanking :data="projectStats" />
      <AssigneeStats :data="assigneeStats" />
      <TagHeatmap :data="tagStats" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import StatsCards from '@/components/dashboard/StatsCards.vue'
import ProjectRanking from '@/components/dashboard/ProjectRanking.vue'
import AssigneeStats from '@/components/dashboard/AssigneeStats.vue'
import TagHeatmap from '@/components/dashboard/TagHeatmap.vue'
import { dashboardApi } from '@/api/dashboard'

const overview = ref<any>(null)
const upcoming = ref<any[]>([])
const projectStats = ref<any[]>([])
const assigneeStats = ref<any[]>([])
const tagStats = ref<any[]>([])
const chart = ref<HTMLElement>()

onMounted(async () => {
  const [ov, trends, up, ps, as, ts] = await Promise.all([
    dashboardApi.overview(),
    dashboardApi.trends(),
    dashboardApi.upcoming(),
    dashboardApi.projectStats(),
    dashboardApi.assigneeStats(),
    dashboardApi.tagStats()
  ])
  overview.value = ov.data.data
  upcoming.value = up.data.data
  projectStats.value = ps.data.data
  assigneeStats.value = as.data.data
  tagStats.value = ts.data.data

  if (chart.value) {
    const instance = echarts.init(chart.value)
    const data = trends.data.data
    instance.setOption({
      xAxis: { type: 'category', data: data.map((d: any) => d.date) },
      yAxis: { type: 'value' },
      series: [{ data: data.map((d: any) => d.count), type: 'line', smooth: true, areaStyle: {} }]
    })
    window.addEventListener('resize', () => instance.resize())
  }
})
</script>

<style scoped>
.dashboard { padding: 20px; }
.chart-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }
h2 { margin-bottom: 16px; }
h3 { margin-bottom: 12px; }
</style>
