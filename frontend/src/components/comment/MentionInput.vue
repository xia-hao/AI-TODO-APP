<template>
  <div class="mention-input-wrap">
    <textarea
      ref="textareaRef"
      class="mention-textarea"
      :value="modelValue"
      :placeholder="placeholder"
      rows="2"
      @input="onInput"
      @keydown="onKeydown"
      @scroll="onScroll"
    />
    <div
      v-if="showDropdown && candidates.length > 0"
      class="mention-dropdown"
      :style="dropdownStyle"
    >
      <div
        v-for="(m, i) in candidates"
        :key="m.userId"
        class="mention-item"
        :class="{ selected: i === selectedIdx }"
        @mousedown.prevent="selectMember(m)"
      >
        <span class="mention-name">{{ m.displayName }}</span>
        <span class="mention-username">@{{ m.username }}</span>
      </div>
    </div>
    <div v-if="showMaxLength" class="char-count" :class="{ over: modelValue.length > maxLength }">
      {{ modelValue.length }}/{{ maxLength }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { matchMembers, type MentionMember } from '@/utils/pinyin'

const props = withDefaults(defineProps<{
  modelValue: string
  members?: MentionMember[]
  placeholder?: string
  maxLength?: number
}>(), {
  members: () => [],
  placeholder: '',
  maxLength: 2000
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const showDropdown = ref(false)
const candidates = ref<MentionMember[]>([])
const selectedIdx = ref(0)
const dropdownStyle = ref({ top: '0px', left: '0px' })
let matchStart = 0
let matchEnd = 0

const showMaxLength = computed(() => props.maxLength > 0)

function onInput(e: Event) {
  const ta = e.target as HTMLTextAreaElement
  const val = ta.value
  emit('update:modelValue', val)

  const cursor = ta.selectionStart
  const beforeCursor = val.slice(0, cursor)
  const atIdx = beforeCursor.lastIndexOf('@')

  if (atIdx >= 0) {
    const query = val.slice(atIdx + 1, cursor)
    if (query.length >= 1 && !/\s/.test(query)) {
      const filtered = matchMembers(query, props.members)
      candidates.value = filtered
      if (filtered.length > 0) {
        matchStart = atIdx
        matchEnd = cursor
        selectedIdx.value = 0
        updatePosition(ta)
        showDropdown.value = true
        return
      }
    }
  }
  showDropdown.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (!showDropdown.value) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    selectedIdx.value = Math.min(selectedIdx.value + 1, candidates.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    selectedIdx.value = Math.max(selectedIdx.value - 1, 0)
  } else if (e.key === 'Enter' || e.key === 'Tab') {
    if (candidates.value[selectedIdx.value]) {
      e.preventDefault()
      selectMember(candidates.value[selectedIdx.value])
    }
  } else if (e.key === 'Escape') {
    showDropdown.value = false
  }
}

function selectMember(m: MentionMember) {
  const ta = textareaRef.value
  if (!ta) return
  const val = ta.value
  const before = val.slice(0, matchStart)
  const after = val.slice(matchEnd)
  const replacement = `@${m.displayName} `
  const newVal = before + replacement + after
  ta.value = newVal
  emit('update:modelValue', newVal)
  showDropdown.value = false
  const pos = matchStart + replacement.length
  ta.setSelectionRange(pos, pos)
  ta.focus()
}

function onScroll() {
  const ta = textareaRef.value
  if (ta) updatePosition(ta)
}

function updatePosition(ta: HTMLTextAreaElement) {
  const rect = ta.getBoundingClientRect()
  dropdownStyle.value = {
    top: `${rect.height + 4}px`,
    left: '0px'
  }
}
</script>

<style scoped>
.mention-input-wrap {
  position: relative;
}

.mention-textarea {
  width: 100%;
  resize: vertical;
  padding: 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}
.mention-textarea:focus {
  border-color: var(--el-color-primary);
}

.mention-dropdown {
  position: absolute;
  z-index: 3000;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  max-height: 200px;
  overflow-y: auto;
  min-width: 200px;
}

.mention-item {
  padding: 8px 12px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.mention-item:hover,
.mention-item.selected {
  background: var(--el-color-primary-light-9);
}
.mention-name {
  font-weight: 500;
  font-size: 14px;
}
.mention-username {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.char-count {
  text-align: right;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.char-count.over {
  color: var(--el-color-danger);
}
</style>
