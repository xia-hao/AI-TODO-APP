<template>
  <div class="subtask-list">
    <!-- 进度条 -->
    <div v-if="subtasks.length > 0" class="subtask-progress">
      <div class="progress-text">{{ $t('todo.subtaskCount', { done: doneCount, total: totalCount }) }}</div>
      <el-progress :percentage="percentage" :stroke-width="6" />
    </div>

    <!-- 添加按钮 -->
    <div class="subtask-actions">
      <el-button type="primary" size="small" @click="openAddDialog">
        + {{ $t('todo.addSubtask') }}
      </el-button>
    </div>

    <!-- 空状态 -->
    <div v-if="subtasks.length === 0 && !loading" class="empty-hint">
      {{ $t('todo.noSubtask') }}
    </div>

    <!-- 列表 -->
    <div ref="listRef" class="subtask-items">
      <div
        v-for="sub in subtasks"
        :key="sub.id"
        :data-id="sub.id"
        class="subtask-item"
        :class="{
          completed: sub.completed,
          overdue: !sub.completed && sub.dueDate && sub.dueDate < today
        }"
        @dblclick="openEditDialog(sub)"
      >
        <!-- 拖拽手柄 -->
        <span class="drag-handle" :title="$t('todo.subtaskDragHint')">⠿</span>

        <!-- Checkbox -->
        <el-checkbox
          :model-value="sub.completed"
          @change="toggle(sub)"
          size="small"
        />

        <!-- 文本 -->
        <span class="subtask-text">{{ sub.text }}</span>

        <!-- 截止日期徽章 -->
        <span
          v-if="sub.dueDate"
          class="due-badge"
          :class="badgeClass(sub.dueDate, sub.completed)"
        >
          {{ formatDate(sub.dueDate) }}
          <span v-if="!sub.completed && sub.dueDate < today" class="badge-label">
            {{ $t('todo.subtaskOverdue') }}
          </span>
          <span v-else-if="!sub.completed && sub.dueDate === today" class="badge-label">
            {{ $t('todo.subtaskToday') }}
          </span>
        </span>

        <!-- 负责人徽章 -->
        <span v-if="sub.assigneeName" class="assignee-badge">
          {{ sub.assigneeName }}
        </span>

        <!-- 删除 -->
        <el-button
          link
          type="danger"
          :icon="Delete"
          size="small"
          @click.stop="confirmDelete(sub.id)"
        />
      </div>
    </div>

    <!-- 添加弹窗 -->
    <el-dialog
      v-model="addVisible"
      :title="$t('todo.subtaskAddTitle')"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-width="70px">
        <el-form-item :label="$t('todo.content')" required>
          <el-input v-model="addForm.text" :placeholder="$t('todo.subtaskPlaceholder')" />
        </el-form-item>
        <el-form-item v-if="members && members.length > 0" :label="$t('todo.assignee')">
          <el-select v-model="addForm.assigneeId" clearable :placeholder="$t('todo.assigneePlaceholder')" style="width:100%">
            <el-option
              v-for="m in members"
              :key="m.userId"
              :label="m.displayName"
              :value="m.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('todo.dueDate')">
          <el-date-picker
            v-model="addForm.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="addLoading" @click="confirmAdd">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      :title="$t('todo.subtaskEditTitle')"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-width="70px">
        <el-form-item :label="$t('todo.content')" required>
          <el-input v-model="editForm.text" />
        </el-form-item>
        <el-form-item v-if="members && members.length > 0" :label="$t('todo.assignee')">
          <el-select v-model="editForm.assigneeId" clearable :placeholder="$t('todo.assigneePlaceholder')" style="width:100%">
            <el-option
              v-for="m in members"
              :key="m.userId"
              :label="m.displayName"
              :value="m.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('todo.dueDate')">
          <el-date-picker
            v-model="editForm.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="display:flex;justify-content:space-between">
          <el-button type="danger" @click="confirmDelete(editingId)">
            {{ $t('common.delete') }}
          </el-button>
          <div>
            <el-button @click="editVisible = false">{{ $t('common.cancel') }}</el-button>
            <el-button type="primary" :loading="editLoading" @click="confirmEdit">
              {{ $t('common.save') }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import type { Subtask, TeamMember } from '@/types'
import { subtasksApi } from '@/api/subtasks'

const props = defineProps<{
  todoId: number
  members?: TeamMember[]
}>()

const { t } = useI18n()

const subtasks = ref<Subtask[]>([])
const loading = ref(false)
const listRef = ref<HTMLElement | null>(null)
let sortable: Sortable | null = null

// ---------- 进度条 ----------
const totalCount = computed(() => subtasks.value.length)
const doneCount = computed(() => subtasks.value.filter(s => s.completed).length)
const percentage = computed(() =>
  totalCount.value === 0 ? 0 : Math.round((doneCount.value / totalCount.value) * 100)
)

// ---------- 日期工具 ----------
const today = new Date().toISOString().slice(0, 10)

function formatDate(dateStr: string): string {
  return dateStr.slice(5)
}

function badgeClass(dateStr: string, completed: boolean): string {
  if (completed) return 'badge-done'
  if (dateStr < today) return 'badge-overdue'
  if (dateStr === today) return 'badge-today'
  return 'badge-future'
}

// ---------- 获取列表 ----------
async function fetch() {
  loading.value = true
  try {
    const { data } = await subtasksApi.list(props.todoId)
    subtasks.value = data.data
  } catch {
    ElMessage.error(t('todo.subtaskOpFailed'))
  } finally {
    loading.value = false
  }
}

// ---------- SortableJS 拖拽 ----------
function initSortable() {
  if (!listRef.value) return
  sortable?.destroy()
  sortable = Sortable.create(listRef.value, {
    handle: '.drag-handle',
    animation: 200,
    onEnd: async (_evt) => {
      const items = Array.from(listRef.value!.children) as HTMLElement[]
      const reorderData = items.map((el, idx) => ({
        id: Number(el.dataset.id),
        sortOrder: idx
      }))
      try {
        await subtasksApi.reorder(props.todoId, reorderData)
      } catch {
        ElMessage.error(t('todo.subtaskOpFailed'))
        await fetch()
      }
    }
  })
}

// ---------- 完成切换 ----------
async function toggle(sub: Subtask) {
  try {
    await subtasksApi.toggleComplete(props.todoId, sub.id)
    sub.completed = !sub.completed
  } catch {
    ElMessage.error(t('todo.subtaskOpFailed'))
  }
}

// ---------- 添加 ----------
const addVisible = ref(false)
const addLoading = ref(false)
const addForm = ref({ text: '', assigneeId: undefined as number | undefined, dueDate: '' })

function openAddDialog() {
  addForm.value = { text: '', assigneeId: undefined, dueDate: '' }
  addVisible.value = true
}

async function confirmAdd() {
  if (!addForm.value.text.trim()) return
  addLoading.value = true
  try {
    await subtasksApi.create(props.todoId, {
      text: addForm.value.text.trim(),
      assigneeId: addForm.value.assigneeId ?? null,
      dueDate: addForm.value.dueDate || undefined
    })
    addVisible.value = false
    await fetch()
    ElMessage.success(t('todo.subtaskAdded'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('todo.subtaskAddFailed'))
  } finally {
    addLoading.value = false
  }
}

// ---------- 编辑 ----------
const editVisible = ref(false)
const editLoading = ref(false)
const editingId = ref(0)
const editForm = ref({ text: '', assigneeId: undefined as number | undefined, dueDate: '' })

function openEditDialog(sub: Subtask) {
  editingId.value = sub.id
  editForm.value = {
    text: sub.text,
    assigneeId: sub.assigneeId ?? undefined,
    dueDate: sub.dueDate ?? ''
  }
  editVisible.value = true
}

async function confirmEdit() {
  if (!editForm.value.text.trim()) return
  editLoading.value = true
  try {
    await subtasksApi.update(props.todoId, editingId.value, {
      text: editForm.value.text.trim(),
      assigneeId: editForm.value.assigneeId ?? null,
      dueDate: editForm.value.dueDate || undefined
    })
    editVisible.value = false
    await fetch()
    ElMessage.success(t('todo.subtaskUpdated'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('todo.subtaskUpdateFailed'))
  } finally {
    editLoading.value = false
  }
}

// ---------- 删除 ----------
async function confirmDelete(subtaskId: number) {
  try {
    await ElMessageBox.confirm(t('todo.subtaskDeleteConfirm'), t('common.confirm'), {
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    await subtasksApi.delete(props.todoId, subtaskId)
    subtasks.value = subtasks.value.filter(s => s.id !== subtaskId)
    ElMessage.success(t('todo.subtaskDeleted'))
  } catch {
    // 取消或失败都不处理
  }
}

// ---------- 生命周期 ----------
onMounted(async () => {
  await fetch()
  await nextTick()
  initSortable()
})

watch(() => subtasks.value.length, () => {
  nextTick(() => initSortable())
})
</script>

<style scoped>
.subtask-list { margin-top: 16px; }

.subtask-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.progress-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.subtask-progress :deep(.el-progress) {
  flex: 1;
}

.subtask-actions {
  margin-bottom: 8px;
}

.empty-hint {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  padding: 16px 0;
  text-align: center;
}

.subtask-items {
  display: flex;
  flex-direction: column;
}

.subtask-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  border-radius: 4px;
  cursor: default;
  transition: background 0.15s;
}
.subtask-item:hover {
  background: var(--el-fill-color-light);
}
.subtask-item.completed .subtask-text {
  text-decoration: line-through;
  color: var(--el-text-color-placeholder);
}
.subtask-item.overdue {
  border-left: 3px solid var(--el-color-danger);
  padding-left: 1px;
}

.drag-handle {
  cursor: grab;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
  user-select: none;
  line-height: 1;
}
.drag-handle:active {
  cursor: grabbing;
}

.subtask-text {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.due-badge {
  font-size: 11px;
  padding: 0 6px;
  border-radius: 3px;
  white-space: nowrap;
  line-height: 18px;
}
.badge-overdue {
  background: #fce4ec;
  color: #c62828;
  font-weight: 600;
}
.badge-today {
  background: #fff3e0;
  color: #e65100;
}
.badge-future {
  background: #e8f5e9;
  color: #2e7d32;
}
.badge-done {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-placeholder);
}
.badge-label {
  margin-left: 2px;
}

.assignee-badge {
  font-size: 12px;
  padding: 0 6px;
  border-radius: 3px;
  background: #e3f2fd;
  color: #1565c0;
  white-space: nowrap;
  line-height: 18px;
}
</style>
