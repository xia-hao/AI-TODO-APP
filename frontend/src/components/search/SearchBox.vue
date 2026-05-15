<template>
  <div class="search-box">
    <el-input
      v-model="query"
      :placeholder="$t('search.placeholder')"
      :prefix-icon="Search"
      clearable
      @input="onInput"
      @focus="onFocus"
      @keydown.escape="visible = false"
      @keydown.enter="onEnter"
    />
    <div v-if="visible && results.length" class="search-dropdown" @click.self="visible = false">
      <div
        v-for="(item, idx) in results" :key="item.id"
        class="search-item"
        :class="{ active: idx === activeIdx }"
        @click="goTo(item)"
        @mouseenter="activeIdx = idx"
      >
        <div class="item-text" v-html="highlight(item.text)" />
        <div class="item-meta">
          <span v-if="item.projectName" class="project-tag">{{ item.projectName }}</span>
          <el-tag v-if="item.completed" size="small" type="success">{{ $t('search.completed') }}</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { todosApi } from '@/api/todos'

const router = useRouter()
const query = ref('')
const results = ref<any[]>([])
const visible = ref(false)
const activeIdx = ref(-1)

let timer: ReturnType<typeof setTimeout>

function onInput() {
  clearTimeout(timer)
  if (!query.value.trim()) { results.value = []; visible.value = false; return }
  timer = setTimeout(async () => {
    try {
      const { data } = await todosApi.search(query.value)
      results.value = data.data
      visible.value = true
      activeIdx.value = -1
    } catch { results.value = [] }
  }, 300)
}

function onFocus() {
  if (results.value.length) visible.value = true
}

function onEnter() {
  if (activeIdx.value >= 0 && results.value[activeIdx.value]) {
    goTo(results.value[activeIdx.value])
  }
}

function escapeHtml(str: string) {
  const div = document.createElement('div')
  div.appendChild(document.createTextNode(str))
  return div.innerHTML
}

function highlight(text: string) {
  const escaped = escapeHtml(text)
  if (!query.value) return escaped
  const re = new RegExp(`(${query.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return escaped.replace(re, '<mark>$1</mark>')
}

function goTo(item: any) {
  visible.value = false
  query.value = ''
  results.value = []
  if (item.projectId) {
    router.push({ path: `/projects/${item.projectId}`, query: { todo: item.id } })
  }
}

function handleKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    const input = document.querySelector('.search-box input') as HTMLInputElement
    input?.focus()
  }
  if (visible.value) {
    if (e.key === 'ArrowDown') { e.preventDefault(); activeIdx.value = Math.min(activeIdx.value + 1, results.value.length - 1) }
    if (e.key === 'ArrowUp') { e.preventDefault(); activeIdx.value = Math.max(activeIdx.value - 1, 0) }
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))
</script>

<style scoped>
.search-box { position: relative; width: 280px; }
.search-dropdown { position: absolute; top: 100%; left: 0; right: 0; z-index: 2000;
  background: var(--el-bg-color); border: 1px solid var(--el-border-color-light); border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1); max-height: 400px; overflow-y: auto; margin-top: 4px; }
.search-item { padding: 10px 12px; cursor: pointer; border-bottom: 1px solid var(--el-border-color-lighter); }
.search-item:hover, .search-item.active { background: var(--el-fill-color-light); }
.item-text { font-size: 14px; margin-bottom: 4px; }
.item-text :deep(mark) { background: #ffd54f; padding: 0 2px; }
.item-meta { font-size: 12px; color: var(--el-text-color-secondary); display: flex; gap: 8px; align-items: center; }
.project-tag { color: var(--el-color-primary); }
</style>
