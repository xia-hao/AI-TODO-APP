<template>
  <el-row :gutter="16">
    <el-col 
      v-for="c in cards" 
      :key="c.label" 
      :xs="12"
      :sm="6"
    >
      <el-card shadow="hover">
        <div class="value">{{ c.value }}</div>
        <div class="label">{{ c.label }}</div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const props = defineProps<{ data: any }>()
const cards = computed(() => [
  { label: t('dashboard.total'), value: props.data?.total ?? 0 },
  { label: t('dashboard.completed'), value: props.data?.completed ?? 0 },
  { label: t('dashboard.active'), value: props.data?.active ?? 0 },
  { label: t('dashboard.overdue'), value: props.data?.overdue ?? 0 }
])
</script>