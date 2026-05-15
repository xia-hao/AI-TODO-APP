<template>
  <div class="project-panel">
    <div class="panel-header">
      <span>{{ $t('project.title') }}</span>
      <el-button link :icon="Plus" @click="showCreate = true" />
    </div>

    <router-link
      v-for="proj in projects.projects"
      :key="proj.id"
      :to="`/projects/${proj.id}`"
      class="project-item"
      :class="{ active: currentId === proj.id }"
    >
      <span class="project-color" :style="{ background: proj.color }" />
      <span>{{ proj.name }}</span>
      <!-- 显示关联的团队标签 -->
      <span class="team-tags" v-if="proj.teams && proj.teams.length">
        <el-tag v-for="t in proj.teams" :key="t.id" size="small" type="primary" effect="dark" class="team-tag">
          {{ t.name }}
        </el-tag>
      </span>
      <span v-else class="team-tags">
        <el-tag size="small" type="info" effect="dark" class="team-tag">{{ $t('report.personal') }}</el-tag>
      </span>
    </router-link>

    <el-dialog v-model="showCreate" :title="$t('project.createProject')" width="360px">
      <el-form :model="form" ref="formRef">
        <el-form-item prop="name" :rules="[{ required: true, message: $t('project.projectNamePlaceholder') }]">
          <el-input v-model="form.name" :placeholder="$t('project.projectNamePlaceholder')" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.description" :placeholder="$t('project.descPlaceholder')" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item prop="teamIds" v-if="teams.teams.length">
          <el-select v-model="form.teamIds" multiple clearable :placeholder="$t('project.teamPlaceholder')">
            <el-option v-for="t in teams.teams" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">{{ $t('common.create') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useProjectsStore } from '@/stores/projects'
import { useTeamsStore } from '@/stores/teams'

const projects = useProjectsStore()
const teams = useTeamsStore()
const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const currentId = computed(() => (route.params.projectId ? Number(route.params.projectId) : null))

const showCreate = ref(false)
const creating = ref(false)
const formRef = ref()
const form = ref({ name: '', description: '', teamIds: [] as number[] })

async function doCreate() {
  await formRef.value.validate()
  creating.value = true
  try {
    const payload: any = { name: form.value.name, description: form.value.description }
    if (form.value.teamIds?.length) payload.teamIds = form.value.teamIds
    const project = await projects.createProject(payload)
    showCreate.value = false
    form.value = { name: '', description: '', teamIds: [] }
    ElMessage.success(t('project.created'))
    router.push(`/projects/${project.id}`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('project.createFailed'))
  } finally {
    creating.value = false
  }
}

</script>

<style scoped>
.project-panel { padding: 8px 0; }
.panel-header { display: flex; justify-content: space-between; align-items: center; padding: 0 12px 8px; font-weight: 600; color: var(--el-text-color-regular); font-size: 13px; }
.project-item:hover, .project-item.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.project-color { width: 10px; height: 10px; border-radius: 50%; display: inline-block; flex-shrink: 0; }
.project-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  color: var(--el-text-color-primary);
  text-decoration: none;
  font-size: 14px;
  transition: background 0.15s;
}
.project-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.team-tags {
  display: flex;
  gap: 4px;
  margin-left: auto;
  flex-shrink: 0;
}
.team-tag {
  flex-shrink: 0;
  font-size: 12px;          /* 原来 11px → 12px */
  font-weight: 600;         /* 加粗 */
  letter-spacing: 0.5px;    /* 稍微拉开字符 */
  padding: 2px 8px;         /* 适当增加左右内边距 */
}
</style>