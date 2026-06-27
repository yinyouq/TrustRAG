<script setup lang="ts">
// 测试集管理页，负责创建、编辑和筛选评估数据集。
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Search } from '@element-plus/icons-vue'
import { evaluationApi } from '@/api'
import type { EvalDataset, EvalDatasetPayload } from '@/api/types'
import DatasetFormDrawer from '@/components/DatasetFormDrawer.vue'
import { useAppStore } from '@/stores/app'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const store = useAppStore()
const loading = ref(false)
const saving = ref(false)
const datasets = ref<EvalDataset[]>([])
const drawerVisible = ref(false)
const editing = ref<EvalDataset | null>(null)
const filters = reactive({
  tenantId: store.tenantId,
  projectId: store.projectId,
  includeDisabled: false,
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    datasets.value = await evaluationApi.listDatasets(filters)
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  drawerVisible.value = true
}

function openEdit(dataset: EvalDataset) {
  editing.value = dataset
  drawerVisible.value = true
}

async function save(payload: EvalDatasetPayload) {
  saving.value = true
  try {
    if (editing.value) await evaluationApi.updateDataset(editing.value.id, payload)
    else await evaluationApi.createDataset(payload)
    drawerVisible.value = false
    ElMessage.success(editing.value ? '测试集已更新' : '测试集已创建')
    await load()
  } catch (reason) {
    ElMessage.error(message(reason))
  } finally {
    saving.value = false
  }
}

async function remove(dataset: EvalDataset) {
  try {
    await ElMessageBox.confirm(
      `删除“${dataset.name}”及其未运行样例？已有历史评估的测试集不能删除。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await evaluationApi.deleteDataset(dataset.id)
    ElMessage.success('测试集已删除')
    await load()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error(message(reason))
  }
}

function message(reason: unknown) {
  return reason instanceof Error ? reason.message : '操作失败'
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="eyebrow">DATASETS</span><h1>标准测试集</h1><p>定义稳定、可重复的评估问题边界。</p></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建测试集</el-button>
    </div>
    <div class="toolbar content-panel">
      <el-input v-model="filters.tenantId" placeholder="租户 ID" clearable />
      <el-input v-model="filters.projectId" placeholder="项目 ID" clearable />
      <el-checkbox v-model="filters.includeDisabled">包含停用</el-checkbox>
      <el-button :icon="Search" @click="load">查询</el-button>
    </div>
    <div class="content-panel block-gap table-panel">
      <el-table v-loading="loading" :data="datasets" empty-text="暂无测试集">
        <el-table-column label="测试集" min-width="260">
          <template #default="{ row }"><strong>{{ row.name }}</strong><small class="table-note">{{ row.description || '暂无说明' }}</small></template>
        </el-table-column>
        <el-table-column label="Scope" min-width="190">
          <template #default="{ row }">{{ row.tenantId || '全局' }} / {{ row.projectId || '全部项目' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="更新时间" width="150"><template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ path: '/eval/cases', query: { datasetId: row.id } })">管理样例</el-button>
            <el-button link @click="router.push({ path: '/eval/runs', query: { datasetId: row.id } })">运行评估</el-button>
            <el-button link :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <DatasetFormDrawer v-model="drawerVisible" :dataset="editing" :tenant-id="filters.tenantId" :project-id="filters.projectId" :saving="saving" @submit="save" />
  </section>
</template>
