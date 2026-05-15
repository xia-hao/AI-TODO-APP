<template>
  <div class="welcome-screen">
    <div class="welcome-icon">
      <span class="icon-text">AI</span>
    </div>
    <h2 class="welcome-title">有什么我可以帮你的？</h2>
    <p class="welcome-subtitle">我是你的 AI 任务助手，可以帮你管理 Todo</p>
    <div class="suggestion-chips">
      <div v-for="(chip, index) in suggestions" :key="index" class="chip" @click="$emit('select', chip.text)">
        <span class="chip-icon">{{ chip.icon }}</span>
        <span class="chip-text">{{ chip.text }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineEmits<{ select: [text: string] }>()
const suggestions = [
  { icon: '📋', text: '帮我创建任务' },
  { icon: '📊', text: '查看任务统计' },
  { icon: '📝', text: '生成日报' },
  { icon: '👥', text: '团队任务概况' },
]
</script>

<style scoped>
.welcome-screen { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; padding: 32px 24px; text-align: center; }
.welcome-icon { width: 56px; height: 56px; border-radius: 14px; background: linear-gradient(135deg, var(--el-color-primary), #8b5cf6); display: flex; align-items: center; justify-content: center; margin-bottom: 16px; box-shadow: 0 4px 12px rgba(99,102,241,0.25); }
.icon-text { color: #fff; font-size: 22px; font-weight: 700; }
.welcome-title { margin: 0 0 4px; font-size: 18px; color: var(--el-text-color-primary); user-select: text !important; }
.welcome-subtitle { margin: 0 0 24px; font-size: 14px; color: var(--el-text-color-secondary); user-select: text !important; }
.suggestion-chips { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; max-width: 400px; }
.chip { display: flex; align-items: center; gap: 6px; padding: 10px 14px; border: 1px solid var(--el-border-color); border-radius: 10px; cursor: pointer; transition: all 0.15s; background: var(--el-bg-color); font-size: 13px; }
.chip:hover { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.chip-icon { font-size: 16px; }
.chip-text { color: var(--el-text-color-primary); }
</style>
