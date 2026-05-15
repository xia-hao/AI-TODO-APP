<template>
  <div class="reports-view">
    <h2>{{ $t('report.title') }}</h2>

    <div class="actions">
      <el-button type="primary" :loading="generating" @click="handleGenerate('DAILY')">
        {{ $t('report.generateDaily') }}
      </el-button>
      <el-button type="success" :loading="generating" @click="handleGenerate('WEEKLY')">
        {{ $t('report.generateWeekly') }}
      </el-button>
    </div>

    <div class="filters">
      <el-select v-model="filterScope" :placeholder="$t('report.scope')" clearable style="width:140px;margin-right:12px" @change="fetchList">
        <el-option :label="$t('report.scopeAll')" value="" />
        <el-option :label="$t('report.scopeSelf')" value="SELF" />
        <el-option :label="$t('report.scopeTeam')" value="TEAM" />
      </el-select>
      <el-select v-model="filterType" :placeholder="$t('report.type')" clearable style="width:120px" @change="fetchList">
        <el-option :label="$t('report.typeAll')" value="" />
        <el-option :label="$t('report.typeDaily')" value="DAILY" />
        <el-option :label="$t('report.typeWeekly')" value="WEEKLY" />
      </el-select>
    </div>

    <el-table :data="list" v-loading="loading" stripe style="width:100%">
      <el-table-column prop="title" :label="$t('report.titleColumn')" min-width="200" />
      <el-table-column :label="$t('report.scopeColumn')" width="110">
        <template #default="{ row }">
          <el-tag :type="row.scope === 'SELF' ? 'primary' : 'warning'" size="small">
            {{ row.scope === 'SELF' ? $t('report.scopeSelfValue') : $t('report.scopeTeamValue') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('report.teamColumn')" width="120">
        <template #default="{ row }">
          {{ row.teamName || $t('report.personal') }}
        </template>
      </el-table-column>
      <el-table-column prop="periodStart" :label="$t('report.periodStart')" width="100" />
      <el-table-column prop="periodEnd" :label="$t('report.periodEnd')" width="100" />
      <el-table-column prop="preview" :label="$t('report.summary')" min-width="200">
        <template #default="{ row }">
          <span class="preview-text">{{ row.preview }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('report.generatedAt')" width="160">
        <template #default="{ row }">
          {{ row.createTime?.replace('T', ' ').slice(0, 16) }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('report.action')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="viewReport(row.id)">{{ $t('report.view') }}</el-button>
          <el-popconfirm :title="$t('report.deleteConfirm')" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button type="danger" link size="small">{{ $t('report.delete') }}</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="prev, pager, next"
        @current-change="fetchList"
      />
    </div>

    <ReportDetailDrawer v-model="detailVisible" :report-id="detailId" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { reportsApi } from '@/api/reports'
import ReportDetailDrawer from '@/components/report/ReportDetailDrawer.vue'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const list = ref<any[]>([])
const loading = ref(false)
const generating = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filterScope = ref('')
const filterType = ref('')

const detailVisible = ref(false)
const detailId = ref<number>(0)

async function fetchList() {
  loading.value = true
  try {
    const params: any = { page: page.value, size: size.value }
    if (filterScope.value) params.scope = filterScope.value
    if (filterType.value) params.type = filterType.value
    const { data } = await reportsApi.list(params)
    list.value = data.data.records || []
    total.value = data.data.total || 0
  } finally {
    loading.value = false
  }
}

async function handleGenerate(type: 'DAILY' | 'WEEKLY') {
  generating.value = true
  try {
    await reportsApi.generate(type)
    ElMessage.success(t('report.generated'))
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || t('report.generateFailed'))
  } finally {
    generating.value = false
  }
}

function viewReport(id: number) {
  detailId.value = id
  detailVisible.value = true
}

async function handleDelete(id: number) {
  try {
    await reportsApi.delete(id)
    ElMessage.success(t('report.deleted'))
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || t('report.deleteFailed'))
  }
}

onMounted(fetchList)
</script>

<style scoped>
.reports-view { padding: 20px; }
.actions { margin-bottom: 16px; display: flex; gap: 8px; }
.filters { margin-bottom: 16px; }
.pagination { margin-top: 20px; display: flex; justify-content: center; }
.preview-text { color: #909399; font-size: 13px; }
h2 { margin-bottom: 16px; }
</style>
