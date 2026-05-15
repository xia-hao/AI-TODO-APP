<template>
  <el-config-provider :locale="elLocale">
  <el-container class="app-layout">
    <!-- 移动端侧边栏遮罩和抽屉 -->
    <div v-if="isMobile && sidebarOpen" class="mobile-overlay" @click="sidebarOpen = false" />
    <el-aside :width="sidebarWidth" class="sidebar" :class="{ 'mobile-sidebar': isMobile, 'sidebar-open': sidebarOpen }">
      <div class="sidebar-logo">📝 {{ $t('app.title') }}</div>
      <el-menu :default-active="route.path" router @select="onMenuSelect">
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>{{ $t('app.dashboard') }}</span>
        </el-menu-item>
        <el-menu-item index="/recycle-bin">
          <el-icon><Delete /></el-icon>
          <span>{{ $t('app.recycleBin') }}</span>
        </el-menu-item>
        <el-menu-item index="/calendar">
          <el-icon><Calendar /></el-icon>
          <span>{{ $t('app.calendar') }}</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><Document /></el-icon>
          <span>{{ $t('app.reports') }}</span>
        </el-menu-item>
      </el-menu>
      <div class="project-section">
        <ProjectPanel />
      </div>
      <el-divider />
      <div class="team-section">
        <TeamPanel />
      </div>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <el-button v-if="isMobile" :icon="Operation" @click="sidebarOpen = !sidebarOpen" />
        <SearchBox />
        <div class="spacer" />
        <QuickAddDialog v-model="quickAddVisible" />
        <el-button class="quick-add-btn" @click="quickAddVisible = true">
          <el-icon><Plus /></el-icon>
          <span class="quick-text">{{ $t('app.quickAdd') }}</span>
        </el-button>
        <el-button :icon="MagicStick" circle @click="aiStore.togglePanel()" :title="'AI 助手'" :type="aiStore.showPanel ? 'primary' : 'default'" />
        <el-badge :value="notifyStore.unreadCount" :hidden="notifyStore.unreadCount === 0" class="notify-badge">
          <el-button :icon="Bell" circle @click="showNotifications = true" />
        </el-badge>
        <el-button :icon="isDark ? MoonNight : Sunny" circle @click="toggleDark()" :title="$t('app.theme')" />
        <el-dropdown @command="handleLangChange">
          <el-button circle>
            <span style="font-size:14px">{{ currentLang === 'zh-CN' ? $t('app.langZh') : $t('app.langEn') }}</span>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="zh-CN">{{ $t('app.chinese') }}</el-dropdown-item>
              <el-dropdown-item command="en-US">{{ $t('app.english') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-avatar size="small">{{ auth.user?.displayName?.charAt(0) }}</el-avatar>
            <span class="user-name">{{ auth.user?.displayName }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">{{ $t('app.logout') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main>
        <router-view />
      </el-main>
    </el-container>

    <!-- 通知面板 -->
    <NotificationPanel v-model="showNotifications" />

    <!-- AI 助手抽屉 -->
    <el-drawer
      v-model="aiStore.showPanel"
      title=""
      size="60%"
      :with-header="false"
      :destroy-on-close="false"
    >
      <AiChatPanel />
    </el-drawer>

    <!-- 任务详情抽屉 -->
    <TodoDetailDrawer v-model="detailVisible" :todo-id="detailTodoId" />
  </el-container>
  </el-config-provider>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { Plus, Bell, Operation, DataAnalysis, Delete, Calendar, Sunny, MoonNight, Document, MagicStick } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useTeamsStore } from '@/stores/teams'
import { useProjectsStore } from '@/stores/projects'
import { useNotificationsStore } from '@/stores/notifications'
import { useAiStore } from '@/stores/ai'
import TeamPanel from '@/components/team/TeamPanel.vue'
import ProjectPanel from '@/components/project/ProjectPanel.vue'
import QuickAddDialog from '@/components/common/QuickAddDialog.vue'
import NotificationPanel from '@/components/notification/NotificationPanel.vue'
import TodoDetailDrawer from '@/components/todo/TodoDetailDrawer.vue'
import SearchBox from '@/components/search/SearchBox.vue'
import AiChatPanel from '@/components/ai/AiChatPanel.vue'
import { useTheme } from '@/composables/useTheme'
import { useI18n } from 'vue-i18n'
import { setLanguage } from '@/i18n'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const teams = useTeamsStore()
const projects = useProjectsStore()
const notifyStore = useNotificationsStore()
const aiStore = useAiStore()

const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value < 768)
const sidebarOpen = ref(false)
const sidebarWidth = computed(() => isMobile.value ? '0px' : '320px')
const showNotifications = ref(false)
const quickAddVisible = ref(false)
const { isDark, toggleDark } = useTheme()
const { locale } = useI18n()
const currentLang = computed(() => locale.value)
const elLocale = computed(() => locale.value === 'en-US' ? en : zhCn)
function handleLangChange(lang: string) { setLanguage(lang); locale.value = lang as any }

// 任务详情抽屉（通过 query.todo 触发）
const detailVisible = ref(false)
const detailTodoId = ref<number | null>(null)

watch(() => route.query.todo, (id) => {
  if (id) {
    detailTodoId.value = Number(id)
    detailVisible.value = true
  } else {
    detailVisible.value = false
  }
}, { immediate: true })

function onResize() { windowWidth.value = window.innerWidth }
onMounted(() => {
  window.addEventListener('resize', onResize)
  if (!auth.user) auth.fetchMe()
  Promise.allSettled([teams.fetchTeams(), projects.fetchProjects()])
  notifyStore.initWebSocket()
  notifyStore.fetchUnread()
})
onUnmounted(() => window.removeEventListener('resize', onResize))

function onMenuSelect() { if (isMobile.value) sidebarOpen.value = false }
function handleCommand(cmd: string) {
  if (cmd === 'logout') { auth.logout(); router.push('/login') }
}
</script>

<style scoped>
.app-layout { height: 100vh; background: var(--el-bg-color); }
.sidebar { border-right: 1px solid var(--el-border-color-light); display: flex; flex-direction: column; overflow-y: auto; transition: width 0.3s; background: var(--el-bg-color); }
.sidebar-logo { padding: 16px; font-size: 18px; font-weight: 700; color: var(--el-color-primary); }
.app-header { display: flex; align-items: center; border-bottom: 1px solid var(--el-border-color-light); background: var(--el-bg-color); gap: 12px; }
.spacer { flex: 1; }
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; color: var(--el-text-color-primary); }
.quick-add-btn { display: flex; align-items: center; gap: 4px; }
.notify-badge { margin-right: 8px; }
.project-section { margin-bottom: 4px; }

/* 移动端样式 */
@media (max-width: 767px) {
  .sidebar { position: fixed; left: -220px; top: 0; bottom: 0; z-index: 2000; background: var(--el-bg-color); width: 220px !important; }
  .sidebar-open { left: 0; }
  .mobile-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.3); z-index: 1999; }
  .user-name { display: none; }
  .quick-text { display: none; }
}
</style>