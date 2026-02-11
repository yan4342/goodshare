<template>
  <div class="notifications-container">
    <div class="notifications-layout">
        <!-- Top Navigation -->
        <div class="notification-nav">
            <div class="nav-header">
                <h2>消息中心</h2>
            </div>
            <div class="nav-menu">
                <div class="nav-item" :class="{ active: activeTab === 'dynamic' }" @click="activeTab = 'dynamic'">
                    <el-icon><Compass /></el-icon>
                    <span>关注动态</span>
                </div>
                <div class="nav-item" :class="{ active: activeTab === 'likes' }" @click="activeTab = 'likes'">
                    <el-icon><StarFilled /></el-icon>
                    <span>收到的赞</span>
                </div>
                <div class="nav-item" :class="{ active: activeTab === 'follows' }" @click="activeTab = 'follows'">
                    <el-icon><UserFilled /></el-icon>
                    <span>新增关注</span>
                </div>
                <div class="nav-item" :class="{ active: activeTab === 'comments' }" @click="activeTab = 'comments'">
                    <el-icon><Comment /></el-icon>
                    <span>评论</span>
                </div>
                 <div class="nav-item" :class="{ active: activeTab === 'messages' }" @click="activeTab = 'messages'">
                    <el-icon><Message /></el-icon>
                    <span>私信</span>
                </div>
            </div>
        </div>

        <!-- Content Area -->
        <div class="notification-content-area">
             <div class="content-header">
                <h3>{{ getTabTitle(activeTab) }}</h3>
             </div>
             
             <div class="content-list">
                <!-- Dynamic Feed -->
                <div v-if="activeTab === 'dynamic'">
                     <div v-if="dynamicPosts.length > 0" class="feed-list">
                        <div v-for="post in dynamicPosts" :key="post.id" class="feed-card" @click="openPostModal(post, $event)">
                             <!-- Header -->
                             <div class="feed-header">
                                <div class="header-left">
                                    <el-avatar :size="40" :src="post.user?.avatarUrl || defaultAvatar" class="avatar clickable" @click.stop="goToUser(post.user?.id)" />
                                    <div class="user-info">
                                        <span class="username clickable" @click.stop="goToUser(post.user?.id)">{{ post.user?.nickname || post.user?.username }}</span>
                                        <span class="time">{{ formatTime(post.createdAt) }}</span>
                                    </div>
                                </div>
                                <div class="header-right">
                                     <el-dropdown trigger="click" @command="handleFeedAction($event, post)">
                                        <div class="more-btn-wrapper" @click.stop>
                                            <el-icon class="more-btn"><MoreFilled /></el-icon>
                                        </div>
                                        <template #dropdown>
                                            <el-dropdown-menu>
                                                <el-dropdown-item command="unfollow">取消关注</el-dropdown-item>
                                            </el-dropdown-menu>
                                        </template>
                                    </el-dropdown>
                                </div>
                             </div>

                             <!-- Content -->
                             <div class="feed-body">
                                <div class="feed-text" :class="{ collapsed: !post.expanded }" v-html="processContent(post.content)">
                                </div>
                                <div v-if="shouldShowExpand(post.content)" class="expand-control" @click.stop="toggleExpand(post)">
                                    {{ post.expanded ? '收起' : '...全文' }}
                                </div>
                                
                                <!-- Image Grid -->
                                <div class="image-grid" v-if="getPostImages(post).length > 0" :class="gridClass(getPostImages(post).length)" @click.stop>
                                     <div v-for="(img, idx) in getPostImages(post).slice(0, 9)" :key="idx" class="grid-image-wrapper">
                                        <el-image 
                                            :src="img" 
                                            :preview-src-list="getPostImages(post)"
                                            :initial-index="idx"
                                            fit="cover"
                                            class="grid-image"
                                            hide-on-click-modal
                                            preview-teleported
                                            @click.stop
                                        />
                                     </div>
                                </div>
                             </div>

                             <!-- Action Bar -->
                             <div class="feed-actions">
                                <div class="action-btn" :class="{ active: post.isLiked }" @click.stop="handleLike(post)">
                                    <el-icon><StarFilled v-if="post.isLiked" /><Star v-else /></el-icon>
                                    <span>{{ post.likeCount || 0 }}</span>
                                </div>
                                <div class="action-btn" @click.stop="handleComment(post)">
                                    <el-icon><ChatDotRound /></el-icon>
                                    <span>{{ post.commentCount || 0 }}</span>
                                </div>
                                 <div class="action-btn" :class="{ active: post.isFavorited }" @click.stop="handleFavorite(post)">
                                    <el-icon><CollectionTag v-if="post.isFavorited" /><Collection v-else /></el-icon>
                                    <span>{{ post.isFavorited ? '已收藏' : '收藏' }}</span>
                                </div>
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
      :focus-comment="shouldFocusComment"
      @close="closePostModal"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '../utils/request'
import { useRouter } from 'vue-router'
import { getThumbnailUrl } from '../utils/image'
import { Compass, StarFilled, UserFilled, Comment, Message, MoreFilled, Star, ChatDotRound, Collection, CollectionTag } from '@element-plus/icons-vue'
import PostDetail from './PostDetail.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import authStore from '../stores/auth'

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
const shouldFocusComment = ref(false)

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
        dynamicPosts.value = (res.data?.records || []).map(p => ({ ...p, expanded: false }))
    } catch (error) {
        console.error('Failed to fetch followed posts', error)
        dynamicPosts.value = []
    }
}

const getPostImages = (post) => {
    if (!post) return []
    if (post._parsedImages) return post._parsedImages
    
    let imgs = []
    
    // 1. Extract images from JSON field
    if (post.images) {
        try {
            imgs = typeof post.images === 'string' ? JSON.parse(post.images) : post.images
        } catch (e) {}
    }
    
    // 2. Extract images from Content
    if (post.content) {
        const contentImgs = []
        const regex = /<img[^>]+src=['"]([^'"]+)['"][^>]*>/gi
        let match
        while ((match = regex.exec(post.content)) !== null) {
            if (match[1]) {
                contentImgs.push(match[1])
            }
        }
        if (contentImgs.length > 0) {
            imgs = [...imgs, ...contentImgs]
        }
    }

    // Filter out placeholders if needed, or keeping them
    imgs = imgs.filter(url => url && !url.includes('placehold.co'))
    
    // Deduplicate images
    imgs = [...new Set(imgs)]
    
    post._parsedImages = imgs
    return imgs
}

const gridClass = (count) => {
    if (count === 1) return 'grid-1'
    if (count === 2) return 'grid-2'
    if (count === 4) return 'grid-2' // 2x2
    return 'grid-3' // 3 columns for 3, 5-9
}

const handleFeedAction = (command, post) => {
    if (command === 'unfollow') {
        handleUnfollow(post)
    }
}

const handleUnfollow = async (post) => {
    try {
        const userId = post.user?.id || post.userId
        if (!userId) {
            console.error('Cannot find user ID to unfollow', post)
            ElMessage.error('无法获取用户信息')
            return
        }

        await ElMessageBox.confirm(
            `确定要取消关注 ${post.user?.nickname || post.user?.username} 吗？`,
            '提示',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning',
            }
        )
        
        await request.post(`/users/${userId}/unfollow`)
        ElMessage.success('已取消关注')
        // Remove posts from this user from the feed locally
        dynamicPosts.value = dynamicPosts.value.filter(p => (p.user?.id || p.userId) !== userId)
        
    } catch (error) {
        if (error !== 'cancel') {
             console.error('Unfollow failed', error)
             ElMessage.error('操作失败')
        }
    }
}

const handleLike = async (post) => {
    if (!authStore.state.isAuthenticated) {
        ElMessage.warning('请先登录')
        return
    }
    
    try {
        if (post.isLiked) {
            await request.delete(`/posts/${post.id}/likes`)
            post.isLiked = false
            post.likeCount = Math.max(0, (post.likeCount || 0) - 1)
        } else {
            await request.post(`/posts/${post.id}/likes`)
            post.isLiked = true
            post.likeCount = (post.likeCount || 0) + 1
        }
    } catch (error) {
        console.error('Like failed', error)
        ElMessage.error('操作失败')
    }
}

const handleFavorite = async (post) => {
    if (!authStore.state.isAuthenticated) {
        ElMessage.warning('请先登录')
        return
    }

    try {
        if (post.isFavorited) {
            await request.delete(`/favorites/${post.id}`)
            post.isFavorited = false
        } else {
            await request.post(`/favorites/${post.id}`)
            post.isFavorited = true
        }
    } catch (error) {
        console.error('Favorite failed', error)
        ElMessage.error('操作失败')
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

const processContent = (content) => {
    if (!content) return ''
    // Remove img tags but keep others
    return content.replace(/<img[^>]*>/gi, '')
}

const shouldShowExpand = (content) => {
    if (!content) return false
    // Simple check on length of processed content
    return processContent(content).length > 100
}

const toggleExpand = (post) => {
    post.expanded = !post.expanded
}

const handleComment = (post) => {
    openPostModal(post, null, true)
}

const getSnippet = (content) => {
    if (!content) return ''
    return processContent(content).substring(0, 60) + '...'
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
    flex-direction: column;
    width: 800px;
    max-width: 95%;
    margin-top: 20px;
    height: calc(100vh - 40px);
    background: var(--bg-color-overlay);
    border-radius: 16px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.05);
    overflow: hidden;
}

/* Top Navigation */
.notification-nav {
    display: flex;
    align-items: center;
    padding: 0 20px;
    background-color: #fff;
    border-bottom: 1px solid var(--border-color);
    flex-shrink: 0;
}

.nav-header {
    margin-right: 40px;
}

.nav-header h2 {
    font-size: 18px;
    font-weight: 600;
    margin: 0;
    color: var(--text-color);
}

.nav-menu {
    display: flex;
    gap: 10px;
    overflow-x: auto;
}

.nav-item {
    display: flex;
    align-items: center;
    padding: 15px 12px;
    cursor: pointer;
    color: var(--text-color-secondary);
    font-weight: 500;
    position: relative;
    white-space: nowrap;
    transition: all 0.3s;
}

.nav-item:hover {
    color: var(--el-color-primary);
}

.nav-item.active {
    color: var(--el-color-primary);
    font-weight: 600;
}

.nav-item.active::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 12px;
    right: 12px;
    height: 3px;
    background-color: var(--el-color-primary);
    border-radius: 3px 3px 0 0;
}

.nav-item .el-icon {
    font-size: 18px;
    margin-right: 6px;
}

/* Content Area */
.notification-content-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    background-color: var(--bg-color-overlay);
    overflow: hidden;
}

.content-header {
    display: none; /* Hidden since we have top tabs now */
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
    background: #fff;
}

.feed-card:hover {
    background-color: var(--bg-color-hover, #fafafa);
}

.feed-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 12px;
}

.header-left {
    display: flex;
    align-items: center;
}

.user-info {
    margin-left: 12px;
    display: flex;
    flex-direction: column;
}

.user-info .username {
    font-weight: 600;
    font-size: 15px;
    color: var(--text-color);
    line-height: 1.2;
}

.user-info .time {
    font-size: 12px;
    color: var(--text-color-secondary);
    margin-top: 4px;
}

.more-btn-wrapper {
    padding: 4px;
    cursor: pointer;
    color: var(--text-color-secondary);
}

.more-btn-wrapper:hover {
    color: var(--text-color);
}

.feed-body {
    margin-bottom: 12px;
}

.feed-text {
    font-size: 15px;
    line-height: 1.6;
    color: var(--text-color);
    white-space: pre-wrap;
    margin-bottom: 8px;
}

.feed-text.collapsed {
    display: -webkit-box;
    -webkit-line-clamp: 4;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.expand-control {
    color: var(--el-color-primary);
    cursor: pointer;
    font-size: 14px;
    margin-bottom: 12px;
    display: inline-block;
}

.image-grid {
    display: grid;
    gap: 6px;
    margin-top: 12px;
    width: 100%;
    max-width: 500px; /* Limit width for aesthetics */
}

.grid-1 {
    grid-template-columns: 1fr;
    max-width: 300px;
}

.grid-2 {
    grid-template-columns: repeat(2, 1fr);
}

.grid-3 {
    grid-template-columns: repeat(3, 1fr);
}

.grid-image-wrapper {
    aspect-ratio: 1 / 1;
    overflow: hidden;
    border-radius: 4px;
    background-color: var(--border-color);
}

.grid-image {
    width: 100%;
    height: 100%;
    transition: transform 0.3s;
    cursor: zoom-in;
}

.grid-image:hover {
    transform: scale(1.05);
}

.feed-actions {
    display: flex;
    align-items: center;
    justify-content: space-evenly; /* 首尾及中间全部等距 */
    padding-top: 12px;
    border-top: 1px solid #f0f0f0;
    width: 100%;
}

.action-btn {
    display: flex;
    align-items: center;
    color: var(--text-color-secondary);
    font-size: 14px;
    cursor: pointer;
    transition: color 0.2s;
}

.action-btn:hover {
    color: var(--el-color-primary);
}

.action-btn .el-icon {
    font-size: 18px;
    margin-right: 6px;
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