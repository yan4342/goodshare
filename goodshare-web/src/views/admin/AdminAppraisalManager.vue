<template>
  <div class="admin-appraisal-manager">
    <div class="header">
      <h2>鉴定管理</h2>
    </div>

    <el-table :data="appraisals" v-loading="loading" style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      
      <el-table-column label="图片" width="100">
        <template #default="scope">
            <el-image 
                v-if="getCover(scope.row)"
                :src="getCover(scope.row)" 
                style="width: 50px; height: 50px"
                :preview-src-list="[getCover(scope.row)]"
                fit="cover"
            />
        </template>
      </el-table-column>

      <el-table-column prop="productName" label="商品名称" />
      
      <el-table-column label="发布用户" width="150">
        <template #default="scope">
            {{ scope.row.user?.nickname || 'Unknown' }}
        </template>
      </el-table-column>

      <el-table-column label="投票情况" width="150">
        <template #default="scope">
            <span style="color: #67c23a">真: {{ scope.row.realVotes }}</span> / 
            <span style="color: #f56c6c">假: {{ scope.row.fakeVotes }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="createdAt" label="发布时间" width="180">
        <template #default="scope">
            {{ new Date(scope.row.createdAt).toLocaleString() }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-popconfirm title="确定删除这个鉴定请求吗？" @confirm="handleDelete(scope.row.id)">
            <template #reference>
              <el-button type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
        <el-pagination
            background
            layout="prev, pager, next"
            :total="total"
            :page-size="pageSize"
            v-model:current-page="currentPage"
            @current-change="loadAppraisals"
        />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'

const appraisals = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const loadAppraisals = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/appraisals', {
      params: {
        page: currentPage.value,
        size: pageSize.value
      }
    })
    appraisals.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleDelete = async (id) => {
  try {
    await request.delete(`/admin/appraisals/${id}`)
    ElMessage.success('删除成功')
    loadAppraisals()
  } catch (error) {
    console.error(error)
  }
}

const getCover = (item) => {
    if (!item.images) return null
    try {
        const imgs = JSON.parse(item.images)
        return imgs.length > 0 ? imgs[0] : null
    } catch (e) {
        return null
    }
}

onMounted(() => {
  loadAppraisals()
})
</script>

<style scoped>
.admin-appraisal-manager {
  padding: 20px;
}

.header {
  margin-bottom: 20px;
}

.pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}
</style>
