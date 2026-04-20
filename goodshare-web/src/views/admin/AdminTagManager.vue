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
      <div 
        class="menu-item" 
        :class="{ active: currentTab === 'audit' }"
        @click="currentTab = 'audit'"
      >
        <span>内容审核</span>
      </div>
      <div 
        class="menu-item" 
        :class="{ active: currentTab === 'users' }"
        @click="currentTab = 'users'"
      >
        <span>用户管理</span>
      </div>
      <div 
        class="menu-item" 
        :class="{ active: currentTab === 'weights' }"
        @click="currentTab = 'weights'"
      >
        <span>算法权重</span>
      </div>
      <div 
        class="menu-item" 
        :class="{ active: currentTab === 'moderation' }"
        @click="currentTab = 'moderation'"
      >
        <span>违禁词配置</span>
      </div>
      <div 
        class="menu-item" 
        :class="{ active: currentTab === 'appraisals' }"
        @click="currentTab = 'appraisals'"
      >
        <span>鉴定管理</span>
      </div>
    </div>
    
    <div class="admin-content">
      <div class="header">
        <h2>{{ 
            currentTab === 'tags' ? '标签管理' : 
            currentTab === 'posts' ? '帖子管理' : 
            currentTab === 'audit' ? '内容审核' :
            currentTab === 'users' ? '用户管理' : 
            currentTab === 'moderation' ? '违禁词配置' :
            currentTab === 'appraisals' ? '鉴定管理' : '算法权重配置' 
        }}</h2>
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
        <div class="actions">
            <el-button 
                type="danger" 
                @click="deleteSelectedPosts" 
                :disabled="selectedPosts.length === 0"
            >
                批量删除
            </el-button>
        </div>
         <el-table :data="posts" style="width: 100%; margin-top: 20px;" v-loading="postsLoading" @selection-change="handlePostSelectionChange">
          <el-table-column type="selection" width="55" />
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="封面" width="100">
             <template #default="scope">
                <el-image 
                    v-if="getCoverUrl(scope.row)"
                    style="width: 60px; height: 60px; border-radius: 4px;" 
                    :src="getCoverUrl(scope.row)" 
                    :preview-src-list="[getOriginalCoverUrl(scope.row)]"
                    fit="cover"
                    preview-teleported
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

      <!-- Audit Management -->
      <div v-if="currentTab === 'audit'">
         <el-table :data="auditPosts" style="width: 100%; margin-top: 20px;" v-loading="auditLoading">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="封面" width="100">
             <template #default="scope">
                <el-image 
                    v-if="getCoverUrl(scope.row)"
                    style="width: 60px; height: 60px; border-radius: 4px;" 
                    :src="getCoverUrl(scope.row)" 
                    :preview-src-list="[getOriginalCoverUrl(scope.row)]"
                    fit="cover"
                    preview-teleported
                />
                <div v-else style="width: 60px; height: 60px; background: #f0f0f0; display: flex; align-items: center; justify-content: center; color: #999;">无图</div>
             </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" show-overflow-tooltip />
          <el-table-column prop="content" label="内容摘要" show-overflow-tooltip />
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
          <el-table-column label="操作" width="180">
            <template #default="scope">
              <el-button 
                type="success" 
                size="small" 
                @click="updateStatus(scope.row.id, 1)"
              >通过</el-button>
              <el-button 
                type="danger" 
                size="small" 
                @click="openRejectDialog(scope.row.id)"
              >拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top: 20px; display: flex; justify-content: flex-end;">
            <el-pagination
                v-model:current-page="auditPage"
                v-model:page-size="auditSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="auditTotal"
                @size-change="handleAuditSizeChange"
                @current-change="handleAuditCurrentChange"
            />
        </div>
        
        <!-- Reject Dialog -->
        <el-dialog v-model="rejectDialogVisible" title="审核不通过" width="500px">
            <el-form :model="rejectForm" label-width="100px">
                <el-form-item label="不通过原因" required>
                    <el-select v-model="rejectForm.reasonCategory" placeholder="请选择原因" style="width: 100%;">
                        <el-option v-for="item in rejectOptions" :key="item" :label="item" :value="item" />
                    </el-select>
                </el-form-item>
                <el-form-item label="补充说明">
                    <el-input v-model="rejectForm.reasonDetail" type="textarea" :rows="3" placeholder="可选填，输入更详细的原因..." />
                </el-form-item>
            </el-form>
            <template #footer>
                <span class="dialog-footer">
                    <el-button @click="rejectDialogVisible = false">取消</el-button>
                    <el-button type="danger" @click="submitReject" :loading="rejecting">确定拒绝</el-button>
                </span>
            </template>
        </el-dialog>
      </div>

      <!-- Users Management -->
      <div v-if="currentTab === 'users'">
        <el-table :data="users" style="width: 100%; margin-top: 20px;" v-loading="usersLoading">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" width="150" />
          <el-table-column prop="nickname" label="昵称" width="150" />
          <el-table-column prop="email" label="邮箱" show-overflow-tooltip />
          <el-table-column prop="role" label="角色" width="120">
             <template #default="scope">
                 <el-tag :type="scope.row.role.includes('ADMIN') ? 'danger' : 'info'">{{ scope.row.role }}</el-tag>
             </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="注册时间" width="180">
              <template #default="scope">
                  {{ formatDate(scope.row.createdAt) }}
              </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-button 
                type="danger" 
                size="small" 
                @click="deleteUser(scope.row.id)"
                :disabled="scope.row.role.includes('ADMIN')"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <div style="margin-top: 20px; display: flex; justify-content: flex-end;">
            <el-pagination
                v-model:current-page="currentPage"
                v-model:page-size="pageSize"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                :total="totalUsers"
                @size-change="handleSizeChange"
                @current-change="handleCurrentChange"
            />
        </div>
      </div>

      <!-- Algorithm Weights Management -->
      <div v-if="currentTab === 'weights'" style="margin-top: 20px; max-width: 600px;">
          <el-card v-loading="weightsLoading">
              <template #header>
                  <div class="card-header">
                      <span>推荐算法权重配置 (UserCF)</span>
                  </div>
              </template>
              <el-form label-position="right" label-width="120px">
                  <el-form-item label="浏览 (View)">
                      <el-input-number v-model="weights['weight.view']" :step="0.1" :min="0" />
                      <span class="help-text">用户浏览帖子的权重 (默认 0.5)</span>
                  </el-form-item>
                  <el-form-item label="点赞 (Like)">
                      <el-input-number v-model="weights['weight.like']" :step="0.1" :min="0" />
                      <span class="help-text">用户点赞帖子的权重 (默认 1.0)</span>
                  </el-form-item>
                  <el-form-item label="收藏 (Favorite)">
                      <el-input-number v-model="weights['weight.favorite']" :step="0.1" :min="0" />
                      <span class="help-text">用户收藏帖子的权重 (默认 2.0)</span>
                  </el-form-item>
                  <el-form-item label="评论 (Comment)">
                      <el-input-number v-model="weights['weight.comment']" :step="0.1" :min="0" />
                      <span class="help-text">用户评论帖子的权重 (默认 3.0)</span>
                  </el-form-item>
                  <el-form-item label="热度 (Popularity)">
                      <el-input-number v-model="weights['weight.comment_count']" :step="0.05" :min="0" />
                      <span class="help-text">帖子评论总数的加权系数 (默认 0.1)</span>
                  </el-form-item>
                  <el-form-item>
                      <el-button type="primary" @click="saveWeights">保存配置</el-button>
                  </el-form-item>
              </el-form>
              <div style="margin-top: 20px; color: #666; font-size: 14px;">
                  <p>说明：</p>
                  <p>1. 权重越高，该行为对推荐结果的影响越大。</p>
                  <p>2. 修改后，新的推荐计算将立即使用新权重。</p>
              </div>
          </el-card>
      </div>

      <div v-if="currentTab === 'moderation'" style="margin-top: 20px; max-width: 900px;">
          <el-card v-loading="forbiddenWordsLoading">
              <template #header>
                  <div class="card-header">
                      <span>帖子违禁词列表</span>
                  </div>
              </template>
              <el-form label-position="top">
                  <el-form-item label="违禁词内容">
                      <el-input
                          v-model="forbiddenWordsText"
                          type="textarea"
                          :rows="8"
                          placeholder="支持英文逗号、中文逗号、分号或换行分隔"
                      />
                  </el-form-item>
                  <div class="forbidden-words-meta">
                      <span>当前共 {{ forbiddenWords.length }} 个词</span>
                      <span>保存后立即作用于帖子发布与编辑</span>
                  </div>
                  <div v-if="forbiddenWords.length > 0" class="forbidden-words-preview">
                      <el-tag
                          v-for="word in forbiddenWords"
                          :key="word"
                          class="forbidden-word-tag"
                          type="danger"
                      >
                          {{ word }}
                      </el-tag>
                  </div>
                  <el-form-item style="margin-top: 20px;">
                      <el-button type="primary" @click="saveForbiddenWordsConfig">保存配置</el-button>
                      <el-button @click="fetchForbiddenWordsConfig">重置</el-button>
                  </el-form-item>
              </el-form>
          </el-card>
      </div>

      <!-- Appraisals Management -->
      <div v-if="currentTab === 'appraisals'">
        <el-table :data="appraisals" v-loading="appraisalsLoading" style="width: 100%">
          <el-table-column prop="id" label="ID" width="80" />
          
          <el-table-column label="图片" width="100">
            <template #default="scope">
                <el-image 
                    v-if="getAppraisalCover(scope.row)"
                    :src="getAppraisalCover(scope.row)" 
                    style="width: 50px; height: 50px"
                    :preview-src-list="[getAppraisalCover(scope.row)]"
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
                {{ formatDate(scope.row.createdAt) }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-popconfirm title="确定删除这个鉴定请求吗？" @confirm="deleteAppraisal(scope.row.id)">
                <template #reference>
                  <el-button type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination" style="margin-top: 20px; display: flex; justify-content: flex-end;">
            <el-pagination
                background
                layout="prev, pager, next"
                :total="appraisalTotal"
                :page-size="appraisalPageSize"
                v-model:current-page="appraisalPage"
                @current-change="fetchAppraisals"
            />
        </div>
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
import { getThumbnailUrl } from '../../utils/image'

const router = useRouter()
const currentTab = ref('tags')

// Tags Data
const tags = ref([])
const newTag = ref('')
const loading = ref(false)

// Posts Data
const posts = ref([])
const postsLoading = ref(false)
const selectedPosts = ref([])

// Users Data
const users = ref([])
const usersLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const totalUsers = ref(0)

// Audit Data
const auditPosts = ref([])
const auditLoading = ref(false)
const auditPage = ref(1)
const auditSize = ref(10)
const auditTotal = ref(0)

const rejectDialogVisible = ref(false)
const rejecting = ref(false)
const rejectForm = ref({
    postId: null,
    reasonCategory: '',
    reasonDetail: ''
})
const rejectOptions = ['言语不当', '标签错误', '违规广告', '传播谣言', '涉嫌抄袭']

// Weights Data
const weights = ref({
    'weight.view': 0.5,
    'weight.like': 1.0,
    'weight.favorite': 2.0,
    'weight.comment': 3.0
})
const weightsLoading = ref(false)

const forbiddenWordsText = ref('')
const forbiddenWords = ref([])
const forbiddenWordsLoading = ref(false)

// Appraisal Data
const appraisals = ref([])
const appraisalsLoading = ref(false)
const appraisalPage = ref(1)
const appraisalPageSize = ref(10)
const appraisalTotal = ref(0)

onMounted(() => {
  fetchTags()
})

watch(currentTab, (newTab) => {
    if (newTab === 'posts') {
        fetchPosts()
    } else if (newTab === 'tags') {
        fetchTags()
    } else if (newTab === 'users') {
        fetchUsers()
    } else if (newTab === 'weights') {
        fetchWeights()
    } else if (newTab === 'moderation') {
        fetchForbiddenWordsConfig()
    } else if (newTab === 'audit') {
        fetchAuditPosts()
    } else if (newTab === 'appraisals') {
        fetchAppraisals()
    }
})

watch(forbiddenWordsText, (value) => {
    forbiddenWords.value = parseForbiddenWords(value)
})

// --- Tag Methods ---
const fetchTags = async () => {
  loading.value = true
  try {
    const res = await request.get('/tags', { _isAdmin: true })
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
    await request.post('/tags', { name: newTag.value.trim() }, { _isAdmin: true })
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
      await request.delete(`/tags/${id}`, { _isAdmin: true })
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
        const res = await request.get('/admin/posts', { _isAdmin: true })
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
      await request.delete(`/admin/posts/${id}`, { _isAdmin: true })
      ElMessage.success('删除成功')
      fetchPosts()
    } catch (error) {
      console.error(error)
      ElMessage.error('删除失败')
    }
  })
}

const deleteSelectedPosts = () => {
    ElMessageBox.confirm(
        `确定要批量删除选中的 ${selectedPosts.value.length} 条帖子吗？此操作不可恢复。`,
        '警告',
        {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'error',
        }
    ).then(async () => {
        const ids = selectedPosts.value.map(p => p.id)
        try {
            await request.delete('/admin/posts', { data: { ids }, _isAdmin: true })
            ElMessage.success('批量删除成功')
            fetchPosts()
        } catch (error) {
            console.error(error)
            ElMessage.error('批量删除失败')
        }
    })
}

const handlePostSelectionChange = (selection) => {
    selectedPosts.value = selection
}

// --- Audit Methods ---
const fetchAuditPosts = async () => {
    auditLoading.value = true
    try {
        const res = await request.get('/admin/posts/pending', {
            params: {
                page: auditPage.value,
                size: auditSize.value
            },
            _isAdmin: true
        })
        auditPosts.value = res.data.records
        auditTotal.value = res.data.total
    } catch (error) {
        console.error('Failed to fetch pending posts', error)
        ElMessage.error('获取待审核列表失败')
    } finally {
        auditLoading.value = false
    }
}

const updateStatus = async (id, status) => {
    try {
        await request.put(`/admin/posts/${id}/status`, { status }, { _isAdmin: true })
        ElMessage.success(status === 1 ? '已通过' : '已拒绝')
        fetchAuditPosts()
    } catch (error) {
        ElMessage.error('操作失败')
    }
}

const openRejectDialog = (id) => {
    rejectForm.value.postId = id
    rejectForm.value.reasonCategory = ''
    rejectForm.value.reasonDetail = ''
    rejectDialogVisible.value = true
}

const submitReject = async () => {
    if (!rejectForm.value.reasonCategory) {
        ElMessage.warning('请选择不通过原因')
        return
    }
    
    rejecting.value = true
    try {
        await request.put(`/admin/posts/${rejectForm.value.postId}/status`, { 
            status: 2,
            reasonCategory: rejectForm.value.reasonCategory,
            reasonDetail: rejectForm.value.reasonDetail
        }, { _isAdmin: true })
        
        ElMessage.success('已拒绝并发送系统通知')
        rejectDialogVisible.value = false
        fetchAuditPosts()
    } catch (error) {
        ElMessage.error('操作失败')
    } finally {
        rejecting.value = false
    }
}

const handleAuditSizeChange = (val) => {
    auditSize.value = val
    fetchAuditPosts()
}

const handleAuditCurrentChange = (val) => {
    auditPage.value = val
    fetchAuditPosts()
}

// --- User Methods ---
const fetchUsers = async () => {
    usersLoading.value = true
    try {
        const res = await request.get('/admin/users', {
            params: {
                page: currentPage.value,
                size: pageSize.value
            },
            _isAdmin: true
        })
        users.value = res.data.records
        totalUsers.value = res.data.total
    } catch (e) {
        ElMessage.error('获取用户列表失败')
    } finally {
        usersLoading.value = false
    }
}

const handleSizeChange = (val) => {
    pageSize.value = val
    fetchUsers()
}

const handleCurrentChange = (val) => {
    currentPage.value = val
    fetchUsers()
}

const deleteUser = (id) => {
    ElMessageBox.confirm('确定要删除该用户吗？将同时删除其所有帖子、评论和互动数据！', '严重警告', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'error'
    }).then(async () => {
        try {
            await request.delete(`/admin/users/${id}`, { _isAdmin: true })
            ElMessage.success('用户已删除')
            fetchUsers()
        } catch (e) {
            ElMessage.error('删除失败')
        }
    })
}

// --- Weight Methods ---
const fetchWeights = async () => {
    weightsLoading.value = true
    try {
        const res = await request.get('/admin/weights', { _isAdmin: true })
        weights.value = { ...weights.value, ...res.data }
    } catch (e) {
        ElMessage.error('获取权重配置失败')
    } finally {
        weightsLoading.value = false
    }
}

const saveWeights = async () => {
    try {
        await request.post('/admin/weights', weights.value, { _isAdmin: true })
        ElMessage.success('权重配置已保存')
    } catch (e) {
        ElMessage.error('保存失败')
    }
}

const parseForbiddenWords = (rawValue) => {
    if (!rawValue) return []
    return Array.from(new Set(
        rawValue
            .split(/[,，;；\r\n]+/)
            .map(word => word.trim())
            .filter(Boolean)
    ))
}

const fetchForbiddenWordsConfig = async () => {
    forbiddenWordsLoading.value = true
    try {
        const res = await request.get('/admin/post-moderation/forbidden-words', { _isAdmin: true })
        forbiddenWordsText.value = res.data.rawValue || ''
        forbiddenWords.value = Array.isArray(res.data.forbiddenWords)
            ? res.data.forbiddenWords
            : parseForbiddenWords(res.data.rawValue)
    } catch (error) {
        console.error(error)
        ElMessage.error('获取违禁词配置失败')
    } finally {
        forbiddenWordsLoading.value = false
    }
}

const saveForbiddenWordsConfig = async () => {
    const parsedWords = parseForbiddenWords(forbiddenWordsText.value)
    if (parsedWords.length === 0) {
        ElMessage.warning('请至少填写一个违禁词')
        return
    }

    forbiddenWordsLoading.value = true
    try {
        const res = await request.put('/admin/post-moderation/forbidden-words', {
            rawValue: forbiddenWordsText.value
        }, { _isAdmin: true })
        forbiddenWordsText.value = res.data.rawValue || parsedWords.join(',')
        forbiddenWords.value = Array.isArray(res.data.forbiddenWords)
            ? res.data.forbiddenWords
            : parseForbiddenWords(forbiddenWordsText.value)
        ElMessage.success('违禁词配置已保存')
    } catch (error) {
        console.error(error)
        ElMessage.error('保存违禁词配置失败')
    } finally {
        forbiddenWordsLoading.value = false
    }
}

// --- Appraisal Methods ---
const fetchAppraisals = async () => {
  appraisalsLoading.value = true
  try {
    const res = await request.get('/admin/appraisals', {
      params: {
        page: appraisalPage.value,
        size: appraisalPageSize.value
      },
      _isAdmin: true
    })
    appraisals.value = res.data.records
    appraisalTotal.value = res.data.total
  } catch (error) {
    console.error(error)
    ElMessage.error('获取鉴定列表失败')
  } finally {
    appraisalsLoading.value = false
  }
}

const deleteAppraisal = async (id) => {
  try {
    await request.delete(`/admin/appraisals/${id}`, { _isAdmin: true })
    ElMessage.success('删除成功')
    fetchAppraisals()
  } catch (error) {
    console.error(error)
    ElMessage.error('删除失败')
  }
}

const getAppraisalCover = (item) => {
    if (!item.images) return null
    try {
        const imgs = JSON.parse(item.images)
        return Array.isArray(imgs) && imgs.length > 0 ? imgs[0] : null
    } catch (e) {
        return null
    }
}

const formatDate = (dateStr) => {
    if (!dateStr) return ''
    const date = new Date(dateStr)
    return date.toLocaleString()
}

const getCoverUrl = (post) => {
    let url = getOriginalCoverUrl(post)
    return getThumbnailUrl(url)
}

const getOriginalCoverUrl = (post) => {
    if (!post) return ''
    let url = null
    if (post.coverUrl && !post.coverUrl.includes('placehold.co')) {
        url = post.coverUrl
    } else if (post.images) {
        try {
            const imgs = typeof post.images === 'string' ? JSON.parse(post.images) : post.images
            if (Array.isArray(imgs) && imgs.length > 0) url = imgs[0]
        } catch (e) {}
    }
    return url
}

const logout = () => {
    localStorage.removeItem('admin_token')
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

.forbidden-words-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #666;
  font-size: 14px;
}

.forbidden-words-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.forbidden-word-tag {
  margin: 0;
}
</style>
