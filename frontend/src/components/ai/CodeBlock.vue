<template>
  <div class="code-block-wrapper">
    <div class="code-header">
      <span class="code-lang">{{ language }}</span>
      <el-button text size="small" class="copy-btn" @click="handleCopy">
        {{ copied ? '已复制' : '复制' }}
      </el-button>
    </div>
    <div class="code-body" v-html="highlightedCode" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'

const props = defineProps<{ code: string; language?: string }>()
const copied = ref(false)

const highlightedCode = computed(() => {
  try {
    if (props.language && hljs.getLanguage(props.language)) {
      return hljs.highlight(props.code, { language: props.language }).value
    }
    return hljs.highlightAuto(props.code).value
  } catch { return props.code }
})

const language = computed(() => {
  if (props.language && hljs.getLanguage(props.language)) return props.language
  return 'code'
})

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.code)
  } catch {
    const ta = document.createElement('textarea')
    ta.value = props.code
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
  }
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}
</script>

<style scoped>
.code-block-wrapper { margin: 8px 0; border-radius: 10px; overflow: hidden; border: 1px solid var(--el-border-color-light); }
.code-header { display: flex; justify-content: space-between; align-items: center; padding: 6px 12px; background: var(--el-fill-color-light); border-bottom: 1px solid var(--el-border-color-light); }
.code-lang { font-size: 12px; color: var(--el-text-color-secondary); text-transform: lowercase; }
.copy-btn { font-size: 12px; height: 24px; }
.code-body { background: #1e1e1e; padding: 14px 16px; overflow-x: auto; line-height: 1.6; font-size: 13px; font-family: 'Menlo', 'Monaco', 'Courier New', monospace; }
.code-body :deep(pre) { margin: 0; background: transparent; }
.code-body :deep(code) { background: transparent; padding: 0; color: #d4d4d4; font-size: inherit; }
</style>
