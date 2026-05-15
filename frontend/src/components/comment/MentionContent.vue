<template>
  <span class="mention-content">
    <template v-for="(token, i) in tokens" :key="i">
      <span v-if="token.type === 'mention'" class="mention-highlight">{{ token.value }}</span>
      <template v-else>{{ token.value }}</template>
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Token {
  type: 'text' | 'mention'
  value: string
}

const props = defineProps<{ content: string }>()

const tokens = computed<Token[]>(() => {
  const result: Token[] = []
  const re = /@([\w一-龥]{2,50})/g
  let last = 0
  let match: RegExpExecArray | null
  while ((match = re.exec(props.content)) !== null) {
    if (match.index > last) {
      result.push({ type: 'text', value: props.content.slice(last, match.index) })
    }
    result.push({ type: 'mention', value: match[0] })
    last = re.lastIndex
  }
  if (last < props.content.length) {
    result.push({ type: 'text', value: props.content.slice(last) })
  }
  return result
})
</script>

<style scoped>
.mention-highlight {
  color: var(--el-color-primary);
  font-weight: 500;
  background: var(--el-color-primary-light-9);
  padding: 0 2px;
  border-radius: 3px;
}
</style>
