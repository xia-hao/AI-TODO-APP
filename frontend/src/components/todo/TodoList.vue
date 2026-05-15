<template>
  <div>
    <div v-if="todos.loading" class="loading-wrap">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="todos.filteredTodos.length === 0" class="empty">
      <el-empty :description="$t('app.empty')" />
    </div>
    <div v-else ref="listRef" class="todo-list">
      <TodoItem v-for="todo in todos.filteredTodos" :key="todo.id" :todo="todo" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import Sortable from 'sortablejs'
import TodoItem from './TodoItem.vue'
import { useTodosStore } from '@/stores/todos'

const todos = useTodosStore()
const listRef = ref<HTMLElement>()
let sortable: Sortable | null = null
let rafId: number | null = null
let currentClientY: number | null = null

function onPointerMove(e: PointerEvent) {
  currentClientY = e.clientY
}

function rafLoop(scrollEl: HTMLElement) {
  const rect = scrollEl.getBoundingClientRect()
  const stickyEl = scrollEl.querySelector('.sticky-top') as HTMLElement | null
  const stickyHeight = stickyEl ? stickyEl.getBoundingClientRect().height : 0
  const sensitivity = 80
  const maxSpeed = 5

  const topTrigger = rect.top + stickyHeight + sensitivity
  const bottomTrigger = rect.bottom - sensitivity

  let delta = 0
  if (currentClientY !== null) {
    if (currentClientY < topTrigger) {
      delta = -Math.min(maxSpeed, ((topTrigger - currentClientY) / sensitivity) * maxSpeed)
    } else if (currentClientY > bottomTrigger) {
      delta = Math.min(maxSpeed, ((currentClientY - bottomTrigger) / sensitivity) * maxSpeed)
    }
  }

  if (delta !== 0) scrollEl.scrollTop += delta
  rafId = requestAnimationFrame(() => rafLoop(scrollEl))
}

function initSortable() {
  if (!listRef.value) return
  sortable?.destroy()
  const scrollEl = listRef.value.closest('.el-main') as HTMLElement | null

  sortable = Sortable.create(listRef.value, {
    handle: '.drag-handle',
    animation: 150,
    scroll: false,
    forceFallback: true,
    onStart() {
      currentClientY = null
      document.addEventListener('pointermove', onPointerMove)
      if (scrollEl) rafId = requestAnimationFrame(() => rafLoop(scrollEl))
    },
    onEnd({ oldIndex, newIndex }) {
      document.removeEventListener('pointermove', onPointerMove)
      if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
      if (oldIndex === undefined || newIndex === undefined || oldIndex === newIndex) return
      const newOrder = [...todos.filteredTodos]
      const [moved] = newOrder.splice(oldIndex, 1)
      newOrder.splice(newIndex, 0, moved)
      todos.reorder(newOrder)
    }
  })
}

onMounted(initSortable)
watch(() => todos.filteredTodos.length, () => { setTimeout(initSortable, 50) })
onUnmounted(() => {
  sortable?.destroy()
  if (rafId !== null) cancelAnimationFrame(rafId)
})
</script>

<style scoped>
.loading-wrap { padding: 16px; }
.empty { padding: 40px 0; }
</style>
