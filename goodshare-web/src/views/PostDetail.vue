<template>
  <div class="post-detail-container">
    <div class="overlay" @click="$router.back()"></div>
    <div class="modal-content">
      <div class="close-btn" @click="$router.back()">
        <el-icon><Close /></el-icon>
      </div>
      
      <div class="content-flex">
        <!-- Left: Image Section -->
        <div v-if="imageList.length > 0" class="image-section">
          <el-carousel v-if="imageList.length > 1" trigger="click" height="100%" :autoplay="false" arrow="always">
            <el-carousel-item v-for="(img, index) in imageList" :key="index">
              <div class="image-wrapper" :style="{ backgroundImage: `url(${img})` }"></div>
            </el-carousel-item>
          </el-carousel>
          <div v-else class="image-wrapper" :style="{ backgroundImage: `url(${imageList[0]})` }"></div>
        </div>
        
        <!-- Right: Info -->
        <div class="info-section" :class="{ 'full-width': !post.coverUrl }">
          <!-- Author Header -->
          <div class="author-header">
            <el-avatar :size="40" :src="post.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
            <span class="username">{{ post.user?.username || '用户' }}</span>
            <el-button type="primary" round size="small" class="follow-btn">关注</el-button>
          </div>
          
          <!-- Scrollable Content -->
          <div class="scrollable-content">
            <h1 class="post-title">{{ post.title }}</h1>
            <div class="post-text" v-html="post.content"></div>
            
            <div class="tags-list">
              <span v-for="tag in post.tags" :key="tag.id" class="tag">#{{ tag.name }}</span>
            </div>
            
            <div class="date">{{ formatDate(post.createdAt) }}</div>
            
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
                <div class="action-btn">
                    <el-icon :size="24"><Collection /></el-icon>
                    <span>收藏</span>
                </div>
                <div class="action-btn">
                    <el-icon :size="24"><ChatDotRound /></el-icon>
                    <span>{{ comments.length > 0 ? comments.length : '评论' }}</span>
                </div>
            </div>
            <div class="comment-input-area">
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '../utils/request'
import { Close, Star, StarFilled, Collection, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const post = ref({})
const comments = ref([])
const newComment = ref('')
const submitting = ref(false)
const isLiked = ref(false)
const likeCount = ref(0)

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

onMounted(() => {
    const postId = route.params.id
    if (postId) {
        fetchPost(postId)
        fetchComments(postId)
        fetchLikeInfo(postId)
    }
})
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
  background: rgba(0, 0, 0, 0.5);
}
.modal-content {
  position: relative;
  width: 900px;
  height: 85vh;
  background: white;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
}
.close-btn {
  position: absolute;
  top: 20px;
  left: 20px;
  z-index: 2001;
  background: rgba(0,0,0,0.1);
  border-radius: 50%;
  width: 40px;
  height: 40px;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  color: white;
  transition: background 0.3s;
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
  background: white;
  max-width: 50%; /* Default if image exists */
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
.username {
  margin-left: 12px;
  font-weight: 600;
  flex: 1;
}
.scrollable-content {
  flex: 1;
  overflow-y: auto;
  padding-right: 10px;
}
.post-title {
  font-size: 18px;
  margin-bottom: 8px;
}
.post-text {
  font-size: 14px;
  color: #333;
  line-height: 1.6;
  white-space: pre-wrap;
  margin-bottom: 20px;
}
.post-text :deep(img) {
    max-width: 100%;
    border-radius: 8px;
}
.tags-list {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag {
  color: #13386c;
  cursor: pointer;
}
.date {
  margin-top: 10px;
  font-size: 12px;
  color: #999;
}
.comments-section {
    margin-top: 20px;
}
.comment-count {
    font-size: 14px;
    color: #666;
    margin-bottom: 12px;
}
.no-comments {
    text-align: center;
    color: #999;
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
    color: #999;
    margin-bottom: 2px;
}
.comment-text {
    font-size: 14px;
    color: #333;
    line-height: 1.4;
}
.comment-date {
    font-size: 11px;
    color: #ccc;
    margin-top: 2px;
}
.bottom-actions {
  border-top: 1px solid #f0f0f0;
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
  color: #333;
}
.action-btn.active {
    color: #ff2442;
}
.action-btn span {
    font-size: 12px;
    margin-top: 2px;
}
.comment-input-area {
    width: 100%;
}
</style>
