<template>
  <div class="admin-container">
    <div class="admin-sidebar">
      <div class="logo">GoodShare Admin</div>
      <div 
        class="menu-item" 
        :class="{ active: currentTab === 'tags' }"
        @click="currentTab = 'tags'"
      >
        <span>标签管理</span>
      </div>
      <div 
        class="menu-item" 
        :class="{ active: currentTab === 'posts' }"
        @click="currentTab = 'posts'"
      >
        <span>帖子管理</span>
      </div>
    </div>
    
    <div class="admin-content">
      <div class="header">
        <h2>{{ currentTab === 'tags' ? '标签管理' : '帖子管理' }}</h2>
        <el-button type="primary" @click="logout">退出登录</el-button>
      </div>
      
      <!-- Tags Management -->
      <div v-if="currentTab === 'tags'">
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

      <!-- Posts Management -->
      <div v-if="currentTab === 'posts'">
         <el-table :data="posts" style="width: 100%; margin-top: 20px;" v-loading="postsLoading">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="封面" width="100">
             <template #default="scope">
                <el-image 
                    style="width: 60px; height: 60px; border-radius: 4px;" 
                    :src="scope.row.coverUrl" 
                    :preview-src-list="[scope.row.coverUrl]"
                    fit="cover"
                />
             </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" show-overflow-tooltip />
          <el-table-column label="作者" width="150">
             <template #default="scope">
                {{ scope.row.user?.nickname || scope.row.user?.username }}
             </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="发布时间" width="180">
              <template #default="scope">
                  {{ formatDate(scope.row.createdAt) }}
              </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-button 
                type="danger" 
                size="small" 
                @click="deletePost(scope.row.id)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import request from '../../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import authStore from '../../stores/auth'

const router = useRouter()
const currentTab = ref('tags')

// Tags Data
const tags = ref([])
const newTag = ref('')
const loading = ref(false)

// Posts Data
const posts = ref([])
const postsLoading = ref(false)

onMounted(() => {
  fetchTags()
})

watch(currentTab, (newTab) => {
    if (newTab === 'posts') {
        fetchPosts()
    } else if (newTab === 'tags') {
        fetchTags()
    }
})

// --- Tag Methods ---
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
    // Send object with name property instead of raw string
    await request.post('/tags', { name: newTag.value.trim() })
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

// --- Post Methods ---
const fetchPosts = async () => {
    postsLoading.value = true
    try {
        const res = await request.get('/admin/posts')
        posts.value = res.data
    } catch (error) {
        console.error('Failed to fetch posts', error)
        ElMessage.error('获取帖子列表失败')
    } finally {
        postsLoading.value = false
    }
}

const deletePost = (id) => {
  ElMessageBox.confirm(
    '确定要强制删除这条帖子吗？此操作不可恢复。',
    '警告',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'error',
    }
  ).then(async () => {
    try {
      await request.delete(`/admin/posts/${id}`)
      ElMessage.success('删除成功')
      fetchPosts()
    } catch (error) {
      console.error(error)
      ElMessage.error('删除失败')
    }
  })
}

const formatDate = (dateStr) => {
    if (!dateStr) return ''
    return new Date(dateStr).toLocaleString()
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
  width: 250px;
  background-color: #304156;
  color: #fff;
  flex-shrink: 0;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 20px;
  font-weight: bold;
  border-bottom: 1px solid #1f2d3d;
}

.menu-item {
  height: 50px;
  line-height: 50px;
  padding: 0 20px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.menu-item:hover {
  background-color: #263445;
}

.menu-item.active {
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
  background: #fff;
  padding: 15px 20px;
  border-radius: 4px;
}

.header h2 {
  margin: 0;
}

.actions {
  margin-bottom: 20px;
  background: #fff;
  padding: 20px;
  border-radius: 4px;
}
</style>