<template>
  <div class="me-container">
      <Sidebar />
    <!-- Profile Header -->
    <div class="profile-header">
      <div class="profile-info">
        <div class="avatar-wrapper">
          <el-upload
            class="avatar-uploader"
            action="/api/uploads"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
            :before-upload="beforeAvatarUpload"
            :headers="uploadHeaders"
          >
            <el-avatar :size="100" :src="userProfile.avatarUrl || defaultAvatar" class="avatar" />
            <div class="avatar-mask">
              <el-icon><Camera /></el-icon>
            </div>
          </el-upload>
        </div>
        <div class="user-details">
          <h1 class="username">{{ userProfile.nickname || userProfile.username }}</h1>
          <p class="bio">{{ userProfile.bio || '这个人很懒，什么都没有写~' }}</p>
          <div class="stats">
            <div class="stat-item">
              <span class="count">{{ userProfile.followingCount || 0 }}</span>
              <span class="label">关注</span>
            </div>
            <div class="stat-item">
              <span class="count">{{ userProfile.followerCount || 0 }}</span>
              <span class="label">粉丝</span>
            </div>
            <div class="stat-item">
              <span class="count">{{ totalLikes || 0 }}</span>
              <span class="label">获赞</span>
            </div>
          </div>
        </div>
        <div class="actions">
            <el-button round @click="showEditProfile = true">编辑资料</el-button>
            <el-button circle><el-icon><Setting /></el-icon></el-button>
        </div>
      </div>
    </div>

    <!-- Content Tabs -->
    <div class="content-tabs">
      <el-tabs v-model="activeTab" @tab-click="handleTabClick">
        <el-tab-pane label="笔记" name="posts">
            <div v-if="posts.length === 0" class="empty-state">
                <el-empty description="还没有发布过笔记哦" />
            </div>
            <div v-else class="masonry-grid">
                <div v-for="post in posts" :key="post.id" class="post-card" :class="{ 'no-image': !getCoverUrl(post) }" @click="$router.push(`/post/${post.id}`)">
                    <div v-if="getCoverUrl(post)" class="card-image" :style="{ backgroundImage: `url(${getCoverUrl(post)})` }">
                        <div class="delete-btn" @click.stop="handleDelete(post)">
                            <el-icon><Delete /></el-icon>
                        </div>
                    </div>
                    <div class="card-content">
                        <h3 class="card-title">{{ post.title }}</h3>
                        <div class="card-footer">
                            <div class="author">
                                <el-avatar :size="20" :src="post.user?.avatarUrl || defaultAvatar" />
                                <span>{{ post.user?.username }}</span>
                            </div>
                            <div class="likes">
                                <el-icon><Star /></el-icon>
                                <span>{{ post.likeCount || 0 }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </el-tab-pane>
        <el-tab-pane label="收藏" name="favorites">
            <div v-if="favorites.length === 0" class="empty-state">
                <el-empty description="还没有收藏过笔记哦" />
            </div>
            <div v-else class="masonry-grid">
                <div v-for="post in favorites" :key="post.id" class="post-card" @click="$router.push(`/post/${post.id}`)">
                    <div class="card-image" :style="{ backgroundImage: `url(${getCoverUrl(post)})` }"></div>
                    <div class="card-content">
                        <h3 class="card-title">{{ post.title }}</h3>
                        <div class="card-footer">
                            <div class="author">
                                <el-avatar :size="20" :src="post.user?.avatarUrl || defaultAvatar" />
                                <span>{{ post.user?.username }}</span>
                            </div>
                            <div class="likes">
                                <el-icon><Star /></el-icon>
                                <span>{{ post.likeCount || 0 }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </el-tab-pane>
        <el-tab-pane label="赞过" name="likes">
            <el-empty description="开发中..." />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Edit Profile Dialog -->
    <el-dialog v-model="showEditProfile" title="编辑资料" width="500px">
        <el-form :model="editForm" label-width="80px">
            <el-form-item label="昵称">
                <el-input v-model="editForm.nickname" placeholder="请输入昵称" maxlength="50" show-word-limit />
            </el-form-item>
            <el-form-item label="简介">
                <el-input v-model="editForm.bio" type="textarea" :rows="3" maxlength="100" show-word-limit />
            </el-form-item>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="showEditProfile = false">取消</el-button>
                <el-button type="primary" @click="saveProfile">保存</el-button>
            </span>
        </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import request from '../utils/request'
import authStore from '../stores/auth'
import { Camera, Setting, Star, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Sidebar from "../components/Sidebar.vue";


const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const userProfile = ref({})
const posts = ref([])
const favorites = ref([])
const activeTab = ref('posts')
const showEditProfile = ref(false)
const totalLikes = ref(0) // Need backend support or calc from posts

const editForm = reactive({
    username: '',
    nickname: '',
    bio: ''
})

const uploadHeaders = computed(() => ({
    'Authorization': `Bearer ${authStore.state.token}`
}))

const fetchUserProfile = async () => {
    try {
        const res = await request.get('/profile')
        userProfile.value = res.data
        editForm.username = res.data.username
        editForm.nickname = res.data.nickname
        editForm.bio = res.data.bio
        
        // Fetch posts after profile to use ID
        if (res.data.id) {
            fetchMyPosts(res.data.id)
            fetchMyFavorites()
        }
    } catch (err) {
        console.error('Failed to load profile', err)
        ElMessage.error('加载个人信息失败')
    }
}

const fetchMyPosts = async (userId) => {
    try {
        const res = await request.get(`/posts/user/${userId}`)
        posts.value = res.data
    } catch (err) {
        console.error('Failed to load posts', err)
    }
}

const fetchMyFavorites = async () => {
    try {
        const res = await request.get('/favorites')
        favorites.value = res.data
    } catch (err) {
        console.error('Failed to load favorites', err)
    }
}

const handleAvatarSuccess = async (response, uploadFile) => {
    // response is the URL string
    userProfile.value.avatarUrl = response
    // Update backend immediately
    try {
        await request.put('/profile', {
            avatarUrl: response,
            bio: userProfile.value.bio,
            email: userProfile.value.email
        })
        ElMessage.success('头像更新成功')
    } catch (err) {
        ElMessage.error('头像保存失败')
    }
}

const beforeAvatarUpload = (rawFile) => {
    if (rawFile.type !== 'image/jpeg' && rawFile.type !== 'image/png') {
        ElMessage.error('Avatar picture must be JPG format!')
        return false
    } else if (rawFile.size / 1024 / 1024 > 2) {
        ElMessage.error('Avatar picture size can not exceed 2MB!')
        return false
    }
    return true
}

const saveProfile = async () => {
    try {
        await request.put('/profile', {
            nickname: editForm.nickname,
            bio: editForm.bio,
            avatarUrl: userProfile.value.avatarUrl,
            email: userProfile.value.email
        })
        userProfile.value.bio = editForm.bio
        userProfile.value.nickname = editForm.nickname
        showEditProfile.value = false
        ElMessage.success('保存成功')
    } catch (err) {
        ElMessage.error('保存失败')
    }
}

const getCoverUrl = (post) => {
    if (post.coverUrl) return post.coverUrl
    if (post.images) {
        try {
            const imgs = JSON.parse(post.images)
            if (Array.isArray(imgs) && imgs.length > 0) return imgs[0]
        } catch (e) {}
    }
    return 'https://placehold.co/300x400?text=No+Image'
}

const handleTabClick = (tab) => {
    if (tab.props.name === 'favorites') {
        fetchMyFavorites()
    } else if (tab.props.name === 'posts' && userProfile.value.id) {
        fetchMyPosts(userProfile.value.id)
    }
}

const handleDelete = async (post) => {
    try {
        await ElMessageBox.confirm(
            '确定要删除这条笔记吗？此操作无法撤销。',
            '提示',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning',
            }
        )
        
        await request.delete(`/posts/${post.id}`)
        ElMessage.success('删除成功')
        // Remove from list
        posts.value = posts.value.filter(p => p.id !== post.id)
    } catch (err) {
        if (err !== 'cancel') {
            console.error('Delete failed', err)
            ElMessage.error('删除失败')
        }
    }
}

onMounted(() => {
    fetchUserProfile()
})
</script>

<style scoped>
.me-container {
    max-width: 960px;
    margin: 0 auto;
    padding: 20px;
}

.profile-header {
    background: #fff;
    padding: 30px;
    border-radius: 12px;
    margin-bottom: 20px;
    display: flex;
    justify-content: center;
}

.profile-info {
    display: flex;
    align-items: flex-start;
    gap: 40px;
}

.avatar-wrapper {
    position: relative;
    cursor: pointer;
}

.avatar-mask {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    border-radius: 50%;
    background: rgba(0,0,0,0.5);
    display: flex;
    justify-content: center;
    align-items: center;
    opacity: 0;
    transition: opacity 0.3s;
    color: #fff;
    font-size: 24px;
}

.avatar-wrapper:hover .avatar-mask {
    opacity: 1;
}

.user-details {
    flex: 1;
}

.username {
    font-size: 24px;
    font-weight: 600;
    margin: 0 0 10px 0;
}

.bio {
    color: #666;
    font-size: 14px;
    margin-bottom: 20px;
    white-space: pre-wrap;
}

.stats {
    display: flex;
    gap: 20px;
}

.stat-item {
    text-align: center;
    cursor: pointer;
}

.stat-item .count {
    display: block;
    font-size: 18px;
    font-weight: 600;
}

.stat-item .label {
    font-size: 12px;
    color: #999;
}

.actions {
    display: flex;
    gap: 10px;
}

.content-tabs {
    background: #fff;
    padding: 20px;
    border-radius: 12px;
    min-height: 500px;
}

/* Masonry Grid (Reused) */
.masonry-grid {
    column-count: 4;
    column-gap: 20px;
}

.post-card {
    break-inside: avoid;
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
    margin-bottom: 20px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.05);
    transition: transform 0.2s;
    cursor: pointer;
    border: 1px solid #eee;
}

.post-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 8px 20px rgba(0,0,0,0.1);
}

.card-image {
    width: 100%;
    padding-top: 133%; /* 3:4 aspect ratio */
    background-size: cover;
    background-position: center;
    position: relative;
}

.delete-btn {
    position: absolute;
    top: 8px;
    right: 8px;
    background: rgba(0, 0, 0, 0.5);
    color: white;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
    cursor: pointer;
    opacity: 0;
    transition: opacity 0.3s;
}

.post-card:hover .delete-btn {
    opacity: 1;
}

.delete-btn:hover {
    background: rgba(245, 108, 108, 0.9); /* Red color on hover */
}

.card-content {
    padding: 12px;
}

.card-title {
    font-size: 14px;
    margin: 0 0 8px 0;
    line-height: 1.4;
    display: -webkit-box;
    -webkit-line-clamp: 2;
     line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.post-card.no-image .card-title {
    -webkit-line-clamp: 6;
    font-size: 15px;
    margin-top: 8px;
}

.card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;
    color: #999;
}

.author {
    display: flex;
    align-items: center;
    gap: 4px;
}

.likes {
    display: flex;
    align-items: center;
    gap: 2px;
}

@media (max-width: 768px) {
    .masonry-grid {
        column-count: 2;
        column-gap: 10px;
    }
    
    .profile-info {
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: 20px;
    }
    
    .stats {
        justify-content: center;
    }
}
</style>