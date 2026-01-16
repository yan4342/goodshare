<template>
  <div class="admin-container">
    <div class="admin-sidebar">
      <div class="logo">GoodShare Admin</div>
      <div class="menu-item active">
        <span>标签管理</span>
      </div>
    </div>
    
    <div class="admin-content">
      <div class="header">
        <h2>标签管理</h2>
        <el-button type="primary" @click="logout">退出登录</el-button>
      </div>
      
      <div class="actions">
        <el-input 
          v-model="newTag" 
          placeholder="输入新标签名称" 
          style="width: 200px; margin-right: 10px;"
          @keyup.enter="addTag"
        />
        <el-button type="primary" @click="addTag">添加标签</el-button>
      </div>
      
      <el-table :data="tags" style="width: 100%; margin-top: 20px;" v-loading="loading">
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="name" label="标签名称" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button 
              type="danger" 
              size="small" 
              @click="deleteTag(scope.row.id)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import authStore from '../../stores/auth'

const router = useRouter()
const tags = ref([])
const newTag = ref('')
const loading = ref(false)

onMounted(() => {
  fetchTags()
})

const fetchTags = async () => {
  loading.value = true
  try {
    const res = await request.get('/tags')
    tags.value = res.data
  } catch (error) {
    console.error('Failed to fetch tags', error)
  } finally {
    loading.value = false
  }
}

const addTag = async () => {
  if (!newTag.value.trim()) return
  
  try {
    await request.post('/tags', newTag.value.trim()) // Backend expects plain string body based on TagController
    ElMessage.success('添加成功')
    newTag.value = ''
    fetchTags()
  } catch (error) {
    // Error handled by interceptor
  }
}

const deleteTag = (id) => {
  ElMessageBox.confirm(
    '确定要删除这个标签吗？',
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await request.delete(`/tags/${id}`)
      ElMessage.success('删除成功')
      fetchTags()
    } catch (error) {
      // Error handled by interceptor
    }
  })
}

const logout = () => {
  authStore.logout()
  router.push('/admin/login')
}
</script>

<style scoped>
.admin-container {
  display: flex;
  min-height: 100vh;
}

.admin-sidebar {
  width: 200px;
  background-color: #001529;
  color: white;
  padding: 20px 0;
}

.logo {
  font-size: 18px;
  font-weight: bold;
  text-align: center;
  margin-bottom: 30px;
}

.menu-item {
  padding: 15px 20px;
  cursor: pointer;
  transition: background 0.3s;
}

.menu-item.active, .menu-item:hover {
  background-color: #1890ff;
}

.admin-content {
  flex: 1;
  padding: 20px;
  background-color: #f0f2f5;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  background: white;
  padding: 15px;
  border-radius: 4px;
}

.actions {
  background: white;
  padding: 15px;
  border-radius: 4px;
}
</style>