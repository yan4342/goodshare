<template>
  <div class="post-detail-container">
    <transition name="fade">
      <div v-if="visible" class="overlay" @click="handleClose"></div>
    </transition>
    <transition name="zoom">
      <div v-if="visible" class="modal-content">
        <div class="content-flex">
        <!-- Left: Image Section -->
        <div v-if="imageList.length > 0" class="image-section">
          <el-carousel v-if="imageList.length > 1" trigger="click" height="100%" :autoplay="false" arrow="always">
            <el-carousel-item v-for="(img, index) in imageList" :key="index">
              <div class="image-wrapper" :style="{ backgroundImage: `url('${img}')` }"></div>
            </el-carousel-item>
          </el-carousel>
          <div v-else class="image-wrapper" :style="{ backgroundImage: `url('${imageList[0]}')` }"></div>
        </div>
        
        <!-- Right: Info -->
        <div class="info-section" :class="{ 'full-width': !post.coverUrl }">
          <!-- Author Header -->
          <div class="author-header">
            <div class="avatar-container" @click="goToUser">
                <el-avatar 
                    :size="40" 
                    :src="post.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" 
                    class="clickable-avatar"
                />
            </div>
            <span class="username clickable-username" @click="goToUser">{{ post.user?.username || '用户' }}</span>
            <el-button 
                v-if="(post.userId || post.user?.id) !== authStore.state.user?.id"
                :type="isFollowing ? 'default' : 'primary'"  
                round 
                size="small" 
                class="follow-btn"
                @click="toggleFollow"
                :loading="followLoading"
            >
                {{ isFollowing ? '已关注' : '关注' }}
            </el-button>
          </div>
          
          <!-- Scrollable Content -->
          <div class="scrollable-content">
            <h1 class="post-title">{{ post.title }}</h1>
            <div class="post-text" v-html="post.content"></div>
            
            <div class="tags-list">
              <span v-for="tag in post.tags" :key="tag.id" class="tag">#{{ tag.name }}</span>
            </div>
            
            <div class="meta-row">
                <div class="date">{{ formatDate(post.createdAt) }}</div>
                <div class="view-count">
                    <el-icon><View /></el-icon> <span>{{ post.viewCount || 0 }}</span>
                </div>
            </div>
            
            <el-divider />
            
            <!-- Comments Section -->
            <div class="comments-section">
              <div class="comment-count">共 {{ comments.length }} 条评论</div>
              <div v-if="comments.length === 0" class="no-comments">暂无评论，快来抢沙发吧~</div>
              <div v-for="comment in comments" :key="comment.id" class="comment-item">
                <el-avatar :size="32" :src="comment.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
                <div class="comment-content">
                  <div class="comment-user">{{ comment.user?.username || '用户' }}</div>
                  <div class="comment-text">{{ comment.content }}</div>
                  <div class="comment-date">{{ formatDate(comment.createdAt) }}</div>
                </div>
              </div>
            </div>
          </div>
          
          <!-- Bottom Actions -->
          <div class="bottom-actions">
            <div class="interaction-bar">
                <div class="action-btn" @click="toggleLike" :class="{ active: isLiked }">
                    <el-icon :size="24"><StarFilled v-if="isLiked" /><Star v-else /></el-icon>
                    <span>{{ likeCount > 0 ? likeCount : '点赞' }}</span>
                </div>
                <div class="action-btn" @click="toggleFavorite" :class="{ active: isFavorited }">
                    <el-icon :size="24"><Collection v-if="!isFavorited" /><CollectionTag v-else /></el-icon>
                    <span>{{ isFavorited ? '已收藏' : '收藏' }}</span>
                </div>

            </div>
            <div class="comment-input-area">
                 <el-popover
                    placement="top"
                    :width="300"
                    trigger="click"
                 >
                    <template #reference>
                        <el-button circle class="emoji-btn">
                            <span style="font-size: 18px; line-height: 1;">😀</span>
                        </el-button>
                    </template>
                    <div class="emoji-picker">
                        <span 
                            v-for="emoji in emojis" 
                            :key="emoji" 
                            class="emoji-item"
                            @click="addEmoji(emoji)"
                        >{{ emoji }}</span>
                    </div>
                 </el-popover>
                 <el-input 
                    v-model="newComment" 
                    placeholder="说点什么..." 
                    class="comment-input"
                    @keyup.enter="submitComment"
                 >
                    <template #append>
                        <el-button @click="submitComment" :loading="submitting">发送</el-button>
                    </template>
                 </el-input>
            </div>
          </div>
        </div>
      </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'
import { Close, Star, StarFilled, Collection, ChatDotRound, CollectionTag, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import authStore from '../stores/auth'

const props = defineProps({
    postId: {
        type: [String, Number],
        default: null
    }
})

const emit = defineEmits(['close'])

const route = useRoute()
const router = useRouter()
const visible = ref(false)
const post = ref({})
const comments = ref([])
const newComment = ref('')
const submitting = ref(false)
const isLiked = ref(false)
const likeCount = ref(0)
const isFavorited = ref(false)
const isFollowing = ref(false)
const followLoading = ref(false)

const emojis = [
    '😀', '😃', '😄', '😁', '😆', '😅', '🤣', '😂', '🙂', '🙃',
    '😉', '😊', '😇', '🥰', '😍', '🤩', '😘', '😗', '😚', '😙',
    '😋', '😛', '😜', '🤪', '😝', '🤑', '🤗', '🤭', '🤫', '🤔',
    '🤐', '🤨', '😐', '😑', '😶', '😏', '😒', '🙄', '😬', '🤥',
    '😌', '😔', '😪', '🤤', '😴', '😷', '🤒', '🤕', '🤢', '🤮',
    '🤧', '🥵', '🥶', '🥴', '😵', '🤯', '🤠', '🥳', '😎', '🤓',
    '🧐', '😕', '😟', '🙁', '😮', '😯', '😲', '😳', '🥺', '😦',
    '😧', '😨', '😰', '😥', '😢', '😭', '😱', '😖', '😣', '😞',
    '😓', '😩', '😫', '🥱', '😤', '😡', '😠', '🤬', '😈', '👿',
    '💀', '☠️', '💩', '🤡', '👹', '👺', '👻', '👽', '👾', '🤖',
    '😺', '😸', '😹', '😻', '😼', '😽', '🙀', '😿', '😾', '👋',
    '🤚', '🖐', '✋', '🖖', '👌', '🤏', '✌️', '🤞', '🤟', '🤘',
    '🤙', '👈', '👉', '👆', '🖕', '👇', '👍', '👎', '✊', '👊',
    '🤛', '🤜', '👏', '🙌', '👐', '🤲', '🤝', '🙏', '💅', '💪'
]

const addEmoji = (emoji) => {
    newComment.value += emoji
}

const goToUser = async () => {
    try {
        const authorId = post.value.userId || post.value.user?.id
        
        if (!authorId) {
            ElMessage.warning('无法获取用户信息')
            return
        }
        
        if (authStore.state.user?.id === authorId) {
            await router.push('/me')
        } else {
            await router.push(`/user/${authorId}`)
        }
    } catch (e) {
        console.error('Navigation error:', e)
    }
}

const imageList = computed(() => {
    if (post.value.images) {
        try {
            const imgs = JSON.parse(post.value.images)
            if (Array.isArray(imgs) && imgs.length > 0) {
                return imgs
            }
        } catch (e) {
            console.error('Failed to parse images', e)
        }
    }
    if (post.value.coverUrl) {
        return [post.value.coverUrl]
    }
    return []
})

const formatDate = (dateStr) => {
    if (!dateStr) return ''
    const date = new Date(dateStr)
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString()
}

const fetchPost = async (id) => {
    try {
        const res = await request.get(`/posts/${id}`)
        post.value = res.data
        const authorId = post.value.userId || post.value.user?.id
        if (authorId) {
            checkFollowStatus(authorId)
        }
    } catch (err) {
        console.error('Failed to load post', err)
        ElMessage.error('加载帖子失败')
    }
}

const fetchComments = async (id) => {
    try {
        const res = await request.get(`/posts/${id}/comments`)
        comments.value = res.data
    } catch (err) {
        console.error('Failed to load comments', err)
    }
}

const fetchLikeInfo = async (id) => {
    try {
        const countRes = await request.get(`/posts/${id}/likes/count`)
        likeCount.value = countRes.data
        
        const statusRes = await request.get(`/posts/${id}/likes/status`)
        isLiked.value = statusRes.data
    } catch (err) {
        console.error('Failed to load like info', err)
    }
}

const fetchFavoriteStatus = async (id) => {
    try {
        const res = await request.get(`/favorites/${id}/check`)
        isFavorited.value = res.data
    } catch (err) {
        console.error('Failed to load favorite status', err)
    }
}

const checkFollowStatus = async (authorId) => {
    if (!authStore.state.isAuthenticated || !authorId) return
    if (authStore.state.user?.id === authorId) return // Don't check for self

    try {
        const res = await request.get(`/users/${authorId}/is-following`)
        isFollowing.value = res.data.isFollowing
    } catch (err) {
        console.error('Failed to check follow status', err)
    }
}

const toggleFollow = async () => {
    if (!authStore.state.isAuthenticated) {
        ElMessage.warning('请先登录')
        router.push('/login')
        return
    }
    
    const authorId = post.value.userId || post.value.user?.id
    if (!authorId) {
        console.error('No author ID found for post:', post.value)
        ElMessage.warning('无法获取作者信息')
        return
    }
    
    followLoading.value = true
    try {
        if (isFollowing.value) {
            await request.post(`/users/${authorId}/unfollow`)
            isFollowing.value = false
            ElMessage.success('已取消关注')
        } else {
            await request.post(`/users/${authorId}/follow`)
            isFollowing.value = true
            ElMessage.success('关注成功')
        }
    } catch (err) {
        console.error('Failed to toggle follow', err)
        ElMessage.error('操作失败')
    } finally {
        followLoading.value = false
    }
}

const toggleLike = async () => {
    if (!post.value.id) return
    try {
        if (isLiked.value) {
            await request.delete(`/posts/${post.value.id}/likes`)
            isLiked.value = false
            likeCount.value--
        } else {
            await request.post(`/posts/${post.value.id}/likes`)
            isLiked.value = true
            likeCount.value++
        }
    } catch (err) {
        console.error('Failed to toggle like', err)
        ElMessage.error('操作失败')
    }
}

const toggleFavorite = async () => {
    if (!post.value.id) return
    try {
        if (isFavorited.value) {
            await request.delete(`/favorites/${post.value.id}`)
            isFavorited.value = false
        } else {
            await request.post(`/favorites/${post.value.id}`)
            isFavorited.value = true
        }
    } catch (err) {
        console.error('Failed to toggle favorite', err)
        ElMessage.error('操作失败')
    }
}

const submitComment = async () => {
    if (!newComment.value.trim()) return
    submitting.value = true
    try {
        await request.post(`/posts/${post.value.id}/comments`, {
            content: newComment.value
        })
        newComment.value = ''
        ElMessage.success('评论成功')
        fetchComments(post.value.id) // Refresh comments
    } catch (err) {
        console.error('Failed to submit comment', err)
        ElMessage.error('评论失败')
    } finally {
        submitting.value = false
    }
}

const recordView = async (id) => {
    try {
        await request.post(`/posts/${id}/view`)
    } catch (err) {
        // Ignore view record errors
        console.error('Failed to record view', err)
    }
}

onMounted(() => {
    visible.value = true
    const id = props.postId || route.params.id
    if (id) {
        recordView(id)
        fetchPost(id)
        fetchComments(id)
        fetchLikeInfo(id)
        fetchFavoriteStatus(id)
    }
})

const handleClose = () => {
    visible.value = false
    setTimeout(() => {
        if (props.postId) {
            emit('close')
        } else {
            router.back()
        }
    }, 300)
}
</script>

<style scoped>
.post-detail-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 2000;
  display: flex;
  justify-content: center;
  align-items: center;
}
.overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: brightness(90%);
  -webkit-backdrop-filter: brightness(90%);
}
.modal-content {
  position: relative;
  width: 1000px;
  height: 90%;
  background: var(--bg-color-overlay);
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  transition: background-color 0.3s;
}
.close-btn {
  position: absolute;
  top: 20px;
  right: 20px; /* Changed from left to right to avoid overlap with avatar */
  z-index: 2001;
  background: rgba(0,0,0,0.1);
  border-radius: 50%;
  width: 40px;
  height: 40px;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  color: var(--text-color); /* Changed from white to adapt to both themes/backgrounds */
  transition: background 0.3s, color 0.3s;
}
.close-btn:hover {
    background: rgba(0,0,0,0.3);
}
.content-flex {
  display: flex;
  width: 100%;
  height: 100%;
}
.image-section {
  flex: 1.5;
  background: #000;
  position: relative; /* Changed from flex center to handle carousel */
}
.image-wrapper {
  width: 100%;
  height: 100%;
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
}
/* Ensure carousel fills the section */
.image-section :deep(.el-carousel) {
    width: 100%;
    height: 100%;
}
.info-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 24px;
  background: var(--bg-color-overlay);
  max-width: 50%; /* Default if image exists */
  transition: background-color 0.3s;
}
.info-section.full-width {
    flex: 1;
    max-width: 100%;
}
.author-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}
.clickable-avatar {
    cursor: pointer;
    transition: opacity 0.3s;
}
.clickable-avatar:hover {
    opacity: 0.8;
}
.username {
  margin-left: 12px;
  font-weight: 600;
  flex: 1;
  color: var(--text-color);
}
.clickable-username {
    cursor: pointer;
}
.clickable-username:hover {
    color: var(--el-color-primary);
}
.scrollable-content {
  flex: 1;
  overflow-y: auto;
  padding-right: 10px;
}
.post-title {
  font-size: 18px;
  margin-bottom: 8px;
  color: var(--text-color);
}
.post-text {
  font-size: 14px;
  color: var(--text-color);
  line-height: 1.6;
  white-space: pre-wrap;
  margin-bottom: 20px;
}
.post-text :deep(img) {
    max-width: 100%;
    border-radius: 8px;
.tags-list {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag {
    color: var(--el-color-primary);
    cursor: pointer;
}
.meta-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 10px;
    color: var(--text-color-secondary);
    font-size: 12px;
}
.view-count {
    display: flex;
    align-items: center;
    gap: 4px;
}
.date {
  /* color: var(--text-color-secondary); Remove if handled by meta-row */
} flex-wrap: wrap;
  gap: 8px;
}
.tag {
  color: var(--el-color-primary); /* Use primary color for tags */
  cursor: pointer;
}
.date {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-color-secondary);
}
.comments-section {
    margin-top: 20px;
}
.comment-count {
    font-size: 14px;
    color: var(--text-color-secondary);
    margin-bottom: 12px;
}
.no-comments {
    text-align: center;
    color: var(--text-color-secondary);
    padding: 20px 0;
    font-size: 13px;
}
.comment-item {
    display: flex;
    gap: 10px;
    margin-bottom: 16px;
}
.comment-content {
    flex: 1;
}
.comment-user {
    font-size: 13px;
    color: var(--text-color-secondary);
    margin-bottom: 2px;
}
.comment-text {
    font-size: 14px;
    color: var(--text-color);
    line-height: 1.4;
}
.comment-date {
    font-size: 11px;
    color: var(--text-color-secondary);
    margin-top: 2px;
}
.bottom-actions {
  border-top: 1px solid var(--border-color);
  padding-top: 12px;
  margin-top: 12px;
}
.interaction-bar {
  display: flex;
  justify-content: space-around;
  margin-bottom: 12px;
}
.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  color: var(--text-color);
}
.action-btn.active {
    color: #ff2442;
}
.action-btn span {
    font-size: 12px;
    margin-top: 2px;
}
.comment-input-area {
  padding: 16px 20px;
  background: var(--bg-color-overlay);
  border-top: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 10px;
  transition: background-color 0.3s, border-color 0.3s;
}

.emoji-btn {
    border: none;
    font-size: 20px;
}

.emoji-picker {
    display: grid;
    grid-template-columns: repeat(8, 1fr);
    gap: 5px;
    max-height: 200px;
    overflow-y: auto;
}

.emoji-item {
    cursor: pointer;
    font-size: 20px;
    text-align: center;
    padding: 5px;
    border-radius: 4px;
    color: var(--text-color);
}

.emoji-item:hover {
    background-color: var(--hover-bg);
}

.comment-input {
  flex: 1;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.zoom-enter-active,
.zoom-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.zoom-enter-from,
.zoom-leave-to {
  transform: scale(0.9);
  opacity: 0;
}
</style>
