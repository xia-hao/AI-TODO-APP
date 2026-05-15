<template>
  <el-drawer v-model="visible" :title="report?.title || $t('report.detail')" size="560px" @closed="handleClose">
    <div v-loading="loading" class="detail-body">
      <template v-if="report">
        <!-- 归属横幅 -->
        <div class="banner" :class="report.scope === 'SELF' ? 'banner-self' : 'banner-team'">
          <div class="banner-avatar">{{ bannerInitial }}</div>
          <div>
            <div class="banner-title">{{ report.scope === 'SELF' ? $t('report.personalReport') : $t('report.teamReport', { team: report.teamName }) }}</div>
            <div class="banner-sub">{{ report.type === 'DAILY' ? $t('report.daily') : $t('report.weekly') }}</div>
          </div>
          <div class="banner-date">{{ report.periodStart }} ~ {{ report.periodEnd }}</div>
        </div>

        <p class="preview-text">{{ report.preview }}</p>

        <!-- 概览统计卡片 -->
        <div class="stat-cards" v-if="summary">
          <div class="stat-card stat-card-done">
            <div class="stat-value">{{ summary.totalCompleted }}</div>
            <div class="stat-label">{{ $t('report.completed') }}</div>
          </div>
          <div class="stat-card stat-card-active">
            <div class="stat-value">{{ report.scope === 'TEAM' ? (summary.totalCreated || 0) : (summary.totalActive || 0) }}</div>
            <div class="stat-label">{{ report.scope === 'SELF' ? $t('report.incomplete') : $t('report.newTasks') }}</div>
          </div>
          <div class="stat-card stat-card-overdue">
            <div class="stat-value">{{ summary.totalOverdue }}</div>
            <div class="stat-label">{{ $t('report.overdueLabel') }}</div>
          </div>
          <div v-if="report.scope === 'SELF'" class="stat-card stat-card-total">
            <div class="stat-value">{{ (summary.totalCompleted || 0) + (summary.totalActive || 0) }}</div>
            <div class="stat-label">{{ $t('report.total') }}</div>
          </div>
          <div v-if="report.scope === 'SELF'" class="stat-card stat-card-rate">
            <div class="stat-value">{{ calcRate(summary) }}%</div>
            <div class="stat-label">{{ $t('report.completionRate') }}</div>
          </div>
          <div class="stat-card stat-card-total" v-if="summary.totalMembers">
            <div class="stat-value">{{ summary.totalMembers }}</div>
            <div class="stat-label">{{ $t('report.memberCount') }}</div>
          </div>
        </div>

        <!-- 执行人报告：完成任务列表 -->
        <div class="section" v-if="report.scope === 'SELF' && completedTasks?.length">
          <h3>{{ $t('report.completedTasks', { count: completedTasks.length }) }}</h3>
          <div class="task-list">
            <div class="task-item" v-for="t in completedTasks" :key="t.id">
              <span class="task-check">✓</span>
              <span class="task-text">{{ t.text }}</span>
              <span class="task-project">{{ t.projectName }}</span>
              <span class="task-time">{{ formatTime(t.completedAt) }}</span>
            </div>
          </div>
        </div>

        <!-- 执行人报告：逾期任务列表 -->
        <div class="section" v-if="report.scope === 'SELF' && overdueTasks?.length">
          <h3>{{ $t('report.overdueTasks', { count: overdueTasks.length }) }}</h3>
          <div class="task-list">
            <div class="task-item" v-for="t in overdueTasks" :key="t.id">
              <span class="task-check" style="color:#c62828">!</span>
              <span class="task-text">{{ t.text }}</span>
              <span class="task-project">{{ t.projectName }}</span>
              <span class="task-time">{{ $t('report.dueDateFormat', { date: formatDateShort(t.dueDate) }) }}</span>
            </div>
          </div>
        </div>

        <!-- 执行人报告：各项目完成情况 -->
        <div class="section" v-if="report.scope === 'SELF' && projectStats?.length">
          <h3>{{ $t('report.projectCompletion') }}</h3>
          <div class="project-list">
            <div class="project-item" v-for="p in projectStats" :key="p.projectId">
              <div class="project-info">
                <span class="project-name">{{ p.projectName }}</span>
                <span class="project-count">{{ p.completed }}/{{ p.total }}</span>
              </div>
              <div class="project-bar-wrap">
                <div class="project-bar" :style="{ width: (p.rate * 100) + '%' }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 领导报告：成员排行 -->
        <div class="section" v-if="report.scope === 'TEAM' && memberStats?.length">
          <h3>{{ $t('report.memberRanking') }}</h3>
          <div class="member-list">
            <div class="member-item" v-for="(m, i) in memberStats" :key="m.userId">
              <span class="member-rank">{{ ['🥇','🥈','🥉'][i] || (i + 1) }}</span>
              <span class="member-name">{{ m.displayName }}</span>
              <div class="member-bar-wrap">
                <div class="member-bar" :style="{ width: Math.round((m.completed / maxCompleted) * 80) + '%' }">
                  <span>{{ $t('report.completedCount', { count: m.completed }) }}</span>
                </div>
              </div>
              <span class="member-overdue" v-if="m.overdue">{{ $t('report.overdueCount', { count: m.overdue }) }}</span>
            </div>
          </div>
        </div>

        <!-- 领导报告：项目分布 -->
        <div class="section" v-if="report.scope === 'TEAM' && projectStats?.length">
          <h3>{{ $t('report.projectDistribution') }}</h3>
          <div class="project-list">
            <div class="project-item" v-for="p in projectStats" :key="p.projectId">
              <div class="project-info">
                <span class="project-name">{{ p.projectName }}</span>
                <span class="project-count">{{ p.completed }}/{{ p.total }}  {{ Math.round(p.rate * 100) }}%</span>
              </div>
              <div class="project-bar-wrap">
                <div class="project-bar" :style="{ width: (p.rate * 100) + '%' }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 领导报告：团队完成任务 -->
        <div class="section" v-if="report.scope === 'TEAM' && completedTasks?.length">
          <h3>{{ $t('report.teamCompletedTasks', { count: completedTasks.length }) }}</h3>
          <div class="task-list">
            <div class="task-item" v-for="t in completedTasks" :key="t.id">
              <span class="task-check">✓</span>
              <span class="task-text">{{ t.text }}</span>
              <span class="task-project">{{ t.assigneeName }}</span>
            </div>
          </div>
        </div>

      </template>
      <el-empty v-else-if="!loading" :description="$t('report.noData')" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { reportsApi } from '@/api/reports'
import { useAuthStore } from '@/stores/auth'
import type { Report } from '@/types'

const { t } = useI18n()

const props = defineProps<{ reportId: number }>()
const model = defineModel<boolean>()

const visible = ref(false)
const loading = ref(false)
const report = ref<Report | null>(null)

watch(() => props.reportId, (id) => {
  if (id > 0) {
    visible.value = true
    fetchDetail(id)
  }
})

watch(visible, (v) => {
  if (!v) model.value = false
})

watch(() => model.value, (v) => {
  if (v) visible.value = v
})

const auth = useAuthStore()
const bannerInitial = computed(() => {
  if (report.value?.scope === 'SELF') {
    return auth.user?.displayName?.[0] || t('report.me')
  }
  return report.value?.teamName?.[0] || '?'
})

function formatTime(iso: string | undefined): string {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

function formatDateShort(iso: string | undefined): string {
  if (!iso) return ''
  return iso.slice(5)
}

async function fetchDetail(id: number) {
  loading.value = true
  try {
    const { data } = await reportsApi.get(id)
    report.value = data.data
  } catch {
    ElMessage.error(t('report.loadFailed'))
    report.value = null
  } finally {
    loading.value = false
  }
}

function handleClose() {
  report.value = null
}

function calcRate(s: typeof summary.value) {
  const total = (s?.totalCompleted || 0) + (s?.totalActive || 0)
  if (total === 0) return 0
  return Math.round(((s?.totalCompleted || 0) / total) * 100)
}

const jsonData = computed(() => report.value?.jsonData)
const summary = computed(() => jsonData.value?.summary || null)
const completedTasks = computed(() => jsonData.value?.completedTasks || [])
const overdueTasks = computed(() => jsonData.value?.overdueTasks || [])
const memberStats = computed(() => jsonData.value?.memberStats || [])
const projectStats = computed(() => jsonData.value?.projectStats || [])
const maxCompleted = computed(() => Math.max(...memberStats.value.map(m => m.completed), 1))
</script>

<style scoped>
.detail-body { padding: 0 12px; }
.banner { display: flex; align-items: center; gap: 12px; padding: 16px; border-radius: 12px; color: white; margin-bottom: 16px; }
.banner-self { background: linear-gradient(135deg, #667eea, #764ba2); }
.banner-team { background: linear-gradient(135deg, #f093fb, #f5576c); }
.banner-avatar { width: 44px; height: 44px; border-radius: 50%; background: rgba(255,255,255,0.25); display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700; }
.banner-title { font-size: 18px; font-weight: 600; }
.banner-sub { font-size: 13px; opacity: 0.85; }
.banner-date { margin-left: auto; font-size: 13px; opacity: 0.85; }
.preview-text { color: #666; font-size: 14px; margin-bottom: 16px; }
.stat-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(100px, 1fr)); gap: 10px; margin-bottom: 24px; }
.stat-card { padding: 16px; border-radius: 12px; text-align: center; }
.stat-card-done { background: #e8f5e9; }
.stat-card-active { background: #fff3e0; }
.stat-card-overdue { background: #fce4ec; }
.stat-card-total { background: #e3f2fd; }
.stat-card-rate { background: #f3e5f5; }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-card-done .stat-value { color: #2e7d32; }
.stat-card-active .stat-value { color: #ef6c00; }
.stat-card-overdue .stat-value { color: #c62828; }
.stat-card-total .stat-value { color: #1565c0; }
.stat-card-rate .stat-value { color: #7b1fa2; }
.stat-label { font-size: 13px; color: #666; margin-top: 4px; }
.section { margin-bottom: 24px; }
.section h3 { font-size: 15px; font-weight: 600; margin-bottom: 10px; color: #303133; }
.task-list { border: 1px solid #ebeef5; border-radius: 8px; overflow: hidden; }
.task-item { display: flex; align-items: center; gap: 8px; padding: 10px 14px; border-bottom: 1px solid #f0f0f0; font-size: 13px; }
.task-item:last-child { border-bottom: none; }
.task-check { color: #2e7d32; font-weight: 700; width: 16px; }
.task-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-project { color: #909399; font-size: 12px; background: #f5f7fa; padding: 2px 8px; border-radius: 4px; white-space: nowrap; }
.task-time { color: #909399; font-size: 12px; white-space: nowrap; }
.member-list { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; }
.project-list { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; }
.project-item { margin-bottom: 12px; }
.project-item:last-child { margin-bottom: 0; }
.project-info { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: 4px; }
.project-name { font-weight: 600; color: #303133; }
.project-count { color: #909399; }
.project-bar-wrap { height: 8px; background: #f5f5f5; border-radius: 4px; overflow: hidden; }
.project-bar { height: 100%; background: linear-gradient(90deg, #667eea, #764ba2); border-radius: 4px; transition: width 0.3s; }
.member-item { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.member-item:last-child { margin-bottom: 0; }
.member-rank { font-size: 16px; width: 28px; text-align: center; }
.member-name { width: 60px; font-weight: 600; font-size: 14px; flex-shrink: 0; }
.member-bar-wrap { flex: 1; height: 24px; background: #f5f5f5; border-radius: 12px; overflow: hidden; }
.member-bar { height: 100%; background: linear-gradient(90deg, #66bb6a, #2e7d32); border-radius: 12px; display: flex; align-items: center; padding: 0 10px; color: white; font-size: 12px; font-weight: 600; white-space: nowrap; }
.member-overdue { color: #c62828; font-size: 12px; width: 60px; text-align: right; flex-shrink: 0; }
</style>
