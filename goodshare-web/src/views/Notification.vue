<template>
  <div class="notifications-container">
    <div class="notifications-layout">
        <!-- Left Sidebar -->
        <div class="notification-sidebar">
            <div class="sidebar-header">
                <h2>消息中心</h2>
            </div>
            <div class="sidebar-menu">
                <div class="menu-item" :class="{ active: activeTab === 'dynamic' }" @click="activeTab = 'dynamic'">
                    <div class="menu-icon-wrapper dynamic-icon">
                        <el-icon><Compass /></el-icon>
                    </div>
                    <span class="menu-label">关注动态</span>
                </div>
                <div class="menu-item" :class="{ active: activeTab === 'likes' }" @click="activeTab = 'likes'">
                    <div class="menu-icon-wrapper like-icon">
                        <el-icon><StarFilled /></el-icon>
                    </div>
                    <span class="menu-label">收到的赞</span>
                </div>
                <div class="menu-item" :class="{ active: activeTab === 'follows' }" @click="activeTab = 'follows'">
                    <div class="menu-icon-wrapper follow-icon">
                        <el-icon><UserFilled /></el-icon>
                    </div>
                    <span class="menu-label">新增关注</span>
                </div>
                <div class="menu-item" :class="{ active: activeTab === 'comments' }" @click="activeTab = 'comments'">
                    <div class="menu-icon-wrapper comment-icon">
                        <el-icon><Comment /></el-icon>
                    </div>
                    <span class="menu-label">评论</span>
                </div>
                 <div class="menu-item" :class="{ active: activeTab === 'messages' }" @click="activeTab = 'messages'">
                    <div class="menu-icon-wrapper message-icon">
                        <el-icon><Message /></el-icon>
                    </div>
                    <span class="menu-label">私信</span>
                </div>
            </div>
        </div>

        <!-- Right Content -->
        <div class="notification-content-area">
             <div class="content-header">
                <h3>{{ getTabTitle(activeTab) }}</h3>
             </div>
             
             <div class="content-list">
                <!-- Dynamic Feed -->
                <div v-if="activeTab === 'dynamic'">
                     <div v-if="dynamicPosts.length > 0" class="feed-list">
                        <div v-for="post in dynamicPosts" :key="post.id" class="feed-card" @click="openPostModal(post, $event)">
                             <div class="feed-header">
                                <el-avatar :size="40" :src="post.user?.avatarUrl || defaultAvatar" class="avatar" />
                                <div class="feed-meta">
                                    <span class="username">{{ post.user?.nickname || post.user?.username }}</span>
                                    <span class="time">{{ formatTime(post.createdAt) }}</span>
                                </div>
                             </div>
                             <div class="feed-body">
                                <div class="feed-content-wrapper">
                                    <h4 class="feed-title">{{ post.title }}</h4>
                                    <p class="feed-snippet" v-if="post.content">{{ getSnippet(post.content) }}</p>
                                </div>
                                <div class="feed-cover" v-if="getCoverUrl(post)" :style="{ backgroundImage: `url('${getCoverUrl(post)}')` }"></div>
                             </div>
                        </div>
                    </div>
                    <div v-else class="empty-state">
                        <el-empty description="暂无关注动态" />
                    </div>
                </div>

                <!-- Likes -->
                <div v-if="activeTab === 'likes'">
                    <div v-if="likes.length > 0">
                        <div v-for="item in likes" :key="item.id" class="notification-item">
                            <el-avatar :size="48" :src="item.sender?.avatarUrl || defaultAvatar" class="avatar clickable" @click.stop="goToUser(item.sender?.id)" />
                            <div class="item-content">
                                <div class="item-header">
                                    <span class="username clickable" @click.stop="goToUser(item.sender?.id)">{{ item.sender?.nickname || item.sender?.username }}</span>
                                    <span class="time">{{ formatTime(item.createdAt) }}</span>
                                </div>
                                <div class="item-action">赞了你的帖子</div>
                            </div>
                            <div class="item-preview" v-if="item.relatedPost && getCoverUrl(item.relatedPost)" @click="openPostModal(item.relatedPost, $event)" :style="{ backgroundImage: `url('${getCoverUrl(item.relatedPost)}')` }"></div>
                        </div>
                    </div>
                    <div v-else class="empty-state">
                        <el-empty description="暂无新的赞" />
                    </div>
                </div>

                <!-- Follows -->
                <div v-if="activeTab === 'follows'">
                    <div v-if="follows.length > 0">
                        <div v-for="item in follows" :key="item.id" class="notification-item">
                            <el-avatar :size="48" :src="item.sender?.avatarUrl || defaultAvatar" class="avatar clickable" @click.stop="goToUser(item.sender?.id)" />
                            <div class="item-content">
                                <div class="item-header">
                                    <span class="username clickable" @click.stop="goToUser(item.sender?.id)">{{ item.sender?.nickname || item.sender?.username }}</span>
                                    <span class="time">{{ formatTime(item.createdAt) }}</span>
                                </div>
                                <div class="item-action">关注了你</div>
                            </div>
                            <!-- Future: Follow back button -->
                        </div>
                    </div>
                    <div v-else class="empty-state">
                        <el-empty description="暂无新关注" />
                    </div>
                </div>
                
                <!-- Comments -->
                <div v-if="activeTab === 'comments'">
                     <div v-if="comments.length > 0">
                        <div v-for="item in comments" :key="item.id" class="notification-item">
                            <el-avatar :size="48" :src="item.sender?.avatarUrl || defaultAvatar" class="avatar clickable" @click.stop="goToUser(item.sender?.id)" />
                            <div class="item-content">
                                <div class="item-header">
                                    <span class="username clickable" @click.stop="goToUser(item.sender?.id)">{{ item.sender?.nickname || item.sender?.username }}</span>
                                    <span class="time">{{ formatTime(item.createdAt) }}</span>
                                </div>
                                <div class="item-action">评论了你的帖子</div>
                                <div class="comment-preview" v-if="item.content">{{ item.content }}</div>
                            </div>
                            <div class="item-preview" v-if="item.relatedPost && getCoverUrl(item.relatedPost)" @click="openPostModal(item.relatedPost, $event)" :style="{ backgroundImage: `url('${getCoverUrl(item.relatedPost)}')` }"></div>
                        </div>
                    </div>
                    <div v-else class="empty-state">
                        <el-empty description="暂无新评论" />
                    </div>
                </div>

                <!-- Messages -->
                <div v-if="activeTab === 'messages'">
                    <div v-if="messageConversations.length > 0">
                        <div v-for="item in messageConversations" :key="item.userId" class="notification-item clickable" @click="goToChat(item.userId)">
                            <el-badge :is-dot="item.unreadCount > 0" class="avatar-badge">
                                <el-avatar :size="48" :src="item.avatarUrl || defaultAvatar" />
                            </el-badge>
                            <div class="item-content">
                                <div class="item-header">
                                    <span class="username">{{ item.nickname || item.username }}</span>
                                    <span class="time">{{ formatTime(item.lastMessageTime) }}</span>
                                </div>
                                <div class="item-action message-preview">{{ item.lastMessageContent }}</div>
                            </div>
                        </div>
                    </div>
                    <div v-else class="empty-state">
                        <el-empty description="暂无私信" />
                    </div>
                </div>
             </div>
        </div>
    </div>

    <!-- Post Detail Modal -->
    <PostDetail 
      v-if="showPostDetail" 
      :post-id="selectedPostId"
      :origin-rect="clickedRect" 
      @close="closePostModal"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '../utils/request'
import { useRouter } from 'vue-router'
import { getThumbnailUrl } from '../utils/image'
import { Compass, StarFilled, UserFilled, Comment, Message } from '@element-plus/icons-vue'
import PostDetail from './PostDetail.vue'

const router = useRouter()
const activeTab = ref('dynamic')
const notifications = ref([])
const dynamicPosts = ref([])
const messageConversations = ref([])
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

// Post Modal State
const showPostDetail = ref(false)
const selectedPostId = ref(null)
const clickedRect = ref(null)

const getTabTitle = (tab) => {
    const titles = {
        'dynamic': '关注动态',
        'likes': '收到的赞',
        'follows': '新增关注',
        'comments': '评论',
        'messages': '私信'
    }
    return titles[tab] || '通知'
}

const fetchNotifications = async () => {
    try {
        const res = await request.get('/notifications')
        notifications.value = res.data?.records || []
    } catch (error) {
        console.error('Failed to fetch notifications', error)
        notifications.value = []
    }
}

const fetchFollowedPosts = async () => {
    try {
        const res = await request.get('/posts/followed', {
            params: {
                page: 1,
                size: 20
            }
        })
        dynamicPosts.value = res.data?.records || []
    } catch (error) {
        console.error('Failed to fetch followed posts', error)
        dynamicPosts.value = []
    }
}

const fetchMessageConversations = async () => {
    try {
        const res = await request.get('/messages/conversations')
        messageConversations.value = res.data || []
    } catch (error) {
        console.error('Failed to fetch conversations', error)
    }
}

onMounted(() => {
    fetchNotifications()
    fetchFollowedPosts()
    fetchMessageConversations()
})

const likes = computed(() => notifications.value.filter(n => n.type === 'LIKE'))
const follows = computed(() => notifications.value.filter(n => n.type === 'FOLLOW'))
const comments = computed(() => notifications.value.filter(n => n.type === 'COMMENT'))

const formatTime = (timeStr) => {
    if (!timeStr) return ''
    const date = new Date(timeStr)
    const now = new Date()
    const diff = now - date
    
    if (diff < 60 * 1000) return '刚刚'
    if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))}分钟前`
    if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))}小时前`
    return date.toLocaleDateString()
}

const getSnippet = (content) => {
    if (!content) return ''
    // Strip HTML tags and replace with space
    const plainText = content.replace(/<[^>]+>/g, ' ')
    return plainText.length > 60 ? plainText.substring(0, 60) + '...' : plainText
}

const goToUser = (userId) => {
    if (!userId) return
    router.push(`/user/${userId}`)
}

const getCoverUrl = (post) => {
    if (!post) return ''
    let url = null
    if (post.coverUrl && !post.coverUrl.includes('placehold.co')) {
        url = post.coverUrl
    } else if (post.images) {
        try {
            const imgs = typeof post.images === 'string' ? JSON.parse(post.images) : post.images;
            if (Array.isArray(imgs) && imgs.length > 0) url = imgs[0]
        } catch (e) {}
    }
    return getThumbnailUrl(url)
}

const openPostModal = (post, event) => {
    if (!post) return
    if (event && event.currentTarget) {
        clickedRect.value = event.currentTarget.getBoundingClientRect()
    }
    selectedPostId.value = post.id
    showPostDetail.value = true
}

const closePostModal = () => {
    showPostDetail.value = false
    selectedPostId.value = null
    clickedRect.value = null
}

const goToChat = (userId) => {
    if (!userId) return
    router.push({
        path: '/chat',
        query: { userId }
    })
}
</script>

<style scoped>
.notifications-container {
    display: flex;
    justify-content: center;
    padding-top: 20px;
    padding-left: var(--sidebar-width);
    background-color: var(--bg-color);
    min-height: 100vh;
    box-sizing: border-box;
}

.notifications-layout {
    display: flex;
    width: 1000px;
    max-width: 95%;
    margin-top: 20px;
    height: calc(100vh - 40px);
    background: var(--bg-color-overlay);
    border-radius: 16px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.05);
    overflow: hidden;
}

/* Sidebar */
.notification-sidebar {
    width: 240px;
    border-right: 1px solid var(--border-color);
    display: flex;
    flex-direction: column;
    padding: 20px 0;
    background-color: var(--bg-color-overlay);
}

.sidebar-header {
    padding: 0 24px;
    margin-bottom: 24px;
}

.sidebar-header h2 {
    font-size: 20px;
    font-weight: 600;
    margin: 0;
    color: var(--text-color);
}

.sidebar-menu {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 0 12px;
}

.menu-item {
    display: flex;
    align-items: center;
    padding: 12px 16px;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.2s;
    color: var(--text-color);
}

.menu-item:hover {
    background-color: var(--bg-color);
}

.menu-item.active {
    background-color: var(--bg-color-active, rgba(0,0,0,0.05));
    font-weight: 600;
}

.menu-icon-wrapper {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 12px;
    font-size: 18px;
}

.dynamic-icon { background-color: rgba(255, 99, 71, 0.1); color: #ff6347; }
.like-icon { background-color: rgba(255, 69, 58, 0.1); color: #ff453a; }
.follow-icon { background-color: rgba(10, 132, 255, 0.1); color: #0a84ff; }
.comment-icon { background-color: rgba(48, 209, 88, 0.1); color: #30d158; }
.message-icon { background-color: rgba(191, 90, 242, 0.1); color: #bf5af2; }

.menu-label {
    font-size: 15px;
}

/* Content Area */
.notification-content-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    background-color: var(--bg-color-overlay);
}

.content-header {
    padding: 20px 30px;
    border-bottom: 1px solid var(--border-color);
}

.content-header h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
}

.content-list {
    flex: 1;
    overflow-y: auto;
    padding: 0;
}

/* Feed Card (Dynamic) */
.feed-card {
    padding: 20px 30px;
    border-bottom: 1px solid var(--border-color);
    cursor: pointer;
    transition: background-color 0.2s;
}

.feed-card:hover {
    background-color: var(--bg-color-hover, #fafafa);
}

.feed-header {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
}

.feed-meta {
    margin-left: 12px;
    display: flex;
    flex-direction: column;
}

.feed-meta .username {
    font-weight: 600;
    font-size: 14px;
    color: var(--text-color);
}

.feed-meta .time {
    font-size: 12px;
    color: var(--text-color-secondary);
    margin-top: 2px;
}

.feed-body {
    display: flex;
    justify-content: space-between;
}

.feed-content-wrapper {
    flex: 1;
    margin-right: 20px;
}

.feed-title {
    margin: 0 0 8px 0;
    font-size: 16px;
    font-weight: 600;
    line-height: 1.4;
    color: var(--text-color);
}

.feed-snippet {
    margin: 0;
    font-size: 14px;
    color: var(--text-color-secondary);
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.feed-cover {
    width: 100px;
    height: 100px;
    border-radius: 8px;
    background-size: cover;
    background-position: center;
    background-color: var(--border-color);
    flex-shrink: 0;
}

/* Notification Item (Likes, Comments, etc) */
.notification-item {
    display: flex;
    padding: 20px 30px;
    border-bottom: 1px solid var(--border-color);
    transition: background-color 0.2s;
}

.notification-item.clickable {
    cursor: pointer;
}

.clickable {
    cursor: pointer;
}

.clickable:hover {
    opacity: 0.8;
}

.notification-item:hover {
    background-color: var(--bg-color-hover, #fafafa);
}

.item-content {
    flex: 1;
    margin-left: 16px;
    display: flex;
    flex-direction: column;
    justify-content: center;
}

.item-header {
    margin-bottom: 4px;
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.item-header .username {
    font-weight: 600;
    font-size: 14px;
    color: var(--text-color);
}

.item-header .time {
    font-size: 12px;
    color: var(--text-color-secondary);
}

.item-action {
    font-size: 14px;
    color: var(--text-color-secondary);
}

.message-preview {
    color: var(--text-color);
    font-weight: 500;
}

.comment-preview {
    margin-top: 8px;
    font-size: 14px;
    color: var(--text-color);
    background-color: var(--bg-color);
    padding: 8px 12px;
    border-radius: 8px;
}

.item-preview {
    width: 60px;
    height: 60px;
    border-radius: 6px;
    background-size: cover;
    background-position: center;
    background-color: var(--border-color);
    margin-left: 16px;
    cursor: pointer;
}

.empty-state {
    padding: 60px 0;
}
</style>