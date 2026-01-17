<template>
  <div class="notifications-container">
    <Sidebar />
    <div class="notifications-card">
      <h2>消息通知</h2>
      
      <el-tabs v-model="activeTab" class="notification-tabs">
        <el-tab-pane label="收到的赞" name="likes">
            <div v-if="likes.length > 0">
                <div v-for="item in likes" :key="item.id" class="notification-item">
                    <el-avatar :size="40" :src="item.sender?.avatarUrl || defaultAvatar" />
                    <div class="notification-content">
                        <span class="username">{{ item.sender?.nickname || item.sender?.username }}</span>
                        <span class="action">赞了你的帖子</span>
                        <span class="time">{{ formatTime(item.createdAt) }}</span>
                    </div>
                    <div class="post-preview" v-if="item.relatedPost && getCoverUrl(item.relatedPost)" @click="goToPost(item.relatedPost.id)" :style="{ backgroundImage: `url(${getCoverUrl(item.relatedPost)})` }"></div>
                </div>
            </div>
            <div v-else class="empty-state">
                <el-empty description="暂无新的赞" />
            </div>
        </el-tab-pane>
        
        <el-tab-pane label="新增关注" name="follows">
            <div v-if="follows.length > 0">
                <div v-for="item in follows" :key="item.id" class="notification-item">
                    <el-avatar :size="40" :src="item.sender?.avatarUrl || defaultAvatar" />
                    <div class="notification-content">
                        <span class="username">{{ item.sender?.nickname || item.sender?.username }}</span>
                        <span class="action">关注了你</span>
                        <span class="time">{{ formatTime(item.createdAt) }}</span>
                    </div>
                    <!-- Follow back button or status could go here -->
                </div>
            </div>
            <div v-else class="empty-state">
                <el-empty description="暂无新关注" />
            </div>
        </el-tab-pane>
        
        <el-tab-pane label="评论" name="comments">
             <div v-if="comments.length > 0">
                <div v-for="item in comments" :key="item.id" class="notification-item">
                    <el-avatar :size="40" :src="item.sender?.avatarUrl || defaultAvatar" />
                    <div class="notification-content">
                        <span class="username">{{ item.sender?.nickname || item.sender?.username }}</span>
                        <span class="action">评论了你的帖子</span>
                        <span class="time">{{ formatTime(item.createdAt) }}</span>
                    </div>
                    <div class="post-preview" v-if="item.relatedPost && getCoverUrl(item.relatedPost)" @click="goToPost(item.relatedPost.id)" :style="{ backgroundImage: `url(${getCoverUrl(item.relatedPost)})` }"></div>
                </div>
            </div>
            <div v-else class="empty-state">
                <el-empty description="暂无新评论" />
            </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Sidebar from "../components/Sidebar.vue";
import request from '../utils/request'
import { useRouter } from 'vue-router'

const router = useRouter()
const activeTab = ref('likes')
const notifications = ref([])
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const fetchNotifications = async () => {
    try {
        const res = await request.get('/notifications')
        notifications.value = res.data || []
    } catch (error) {
        console.error('Failed to fetch notifications', error)
        notifications.value = []
    }
}

const likes = computed(() => notifications.value.filter(n => n.type === 'LIKE'))
const follows = computed(() => notifications.value.filter(n => n.type === 'FOLLOW'))
const comments = computed(() => notifications.value.filter(n => n.type === 'COMMENT'))

const formatTime = (timeStr) => {
    if (!timeStr) return ''
    const date = new Date(timeStr)
    const now = new Date()
    const diff = now - date
    
    // Adjust for timezone if necessary, but assuming standard parsing works
    if (diff < 60 * 1000) return '刚刚'
    if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))}分钟前`
    if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))}小时前`
    return date.toLocaleDateString()
}

const getCoverUrl = (post) => {
    if (!post) return '';
    if (post.coverUrl) return post.coverUrl;
    if (post.images) {
        try {
            // Check if images is already an array or a JSON string
            const imgs = typeof post.images === 'string' ? JSON.parse(post.images) : post.images;
            if (Array.isArray(imgs) && imgs.length > 0) return imgs[0];
        } catch (e) {}
    }
    return null;
}

const goToPost = (postId) => {
    router.push(`/post/${postId}`)
}

onMounted(() => {
    fetchNotifications()
})
</script>

<style scoped>
.notifications-container {
    display: flex;
    justify-content: center;
    padding: 40px;
    background-color: var(--bg-color);
    min-height: 100vh;
    transition: background-color 0.3s;
}

.notifications-card {
    width: 800px;
    background: var(--bg-color-overlay);
    padding: 30px;
    border-radius: 16px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.05);
    transition: background-color 0.3s;
}

.notification-item {
    display: flex;
    align-items: center;
    padding: 15px 0;
    border-bottom: 1px solid var(--border-color);
}

.notification-content {
    flex: 1;
    margin-left: 15px;
    display: flex;
    flex-direction: column;
}

.username {
    font-weight: 600;
    font-size: 14px;
    color: var(--text-color);
}

.action {
    color: var(--text-color-secondary);
    font-size: 14px;
}

.time {
    color: var(--text-color-secondary);
    font-size: 12px;
    margin-top: 4px;
}

.post-preview {
    width: 48px;
    height: 48px;
    background-color: var(--border-color);
    border-radius: 4px;
    background-size: cover;
    background-position: center;
    cursor: pointer;
}
</style>
