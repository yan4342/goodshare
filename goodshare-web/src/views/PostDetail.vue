<template>
  <div class="post-detail-container">
    <div class="overlay" @click="$router.back()"></div>
    <div class="modal-content">
      <div class="close-btn" @click="$router.back()">
        <el-icon><Close /></el-icon>
      </div>
      
      <div class="content-flex">
        <!-- Left: Image -->
        <div class="image-section">
          <div class="image-wrapper" :style="{ backgroundImage: `url(${post.coverUrl || 'https://via.placeholder.com/600x800'})` }"></div>
        </div>
        
        <!-- Right: Info -->
        <div class="info-section">
          <!-- Author Header -->
          <div class="author-header">
            <el-avatar :size="40" :src="post.user?.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
            <span class="username">{{ post.user?.username || '用户' }}</span>
            <el-button type="primary" round size="small" class="follow-btn">关注</el-button>
          </div>
          
          <!-- Scrollable Content -->
          <div class="scrollable-content">
            <h1 class="post-title">{{ post.title }}</h1>
            <p class="post-text">{{ post.content }}</p>
            
            <div class="tags-list">
              <span v-for="tag in post.tags" :key="tag.id" class="tag">#{{ tag.name }}</span>
            </div>
            
            <div class="date">{{ formatDate(post.createdAt) }}</div>
            
            <el-divider />
            
            <!-- Comments Section (Mock) -->
            <div class="comments-section">
              <div class="comment-count">共 {{ comments.length }} 条评论</div>
              <div v-for="comment in comments" :key="comment.id" class="comment-item">
                <el-avatar :size="24" :src="comment.avatar" />
                <div class="comment-content">
                  <span class="comment-user">{{ comment.username }}</span>
                  <span class="comment-text">{{ comment.content }}</span>
                </div>
              </div>
            </div>
          </div>
          
          <!-- Bottom Actions -->
          <div class="bottom-actions">
            <div class="interaction-bar">
                <div class="action-btn">
                    <el-icon :size="24"><Star /></el-icon>
                    <span>点赞</span>
                </div>
                <div class="action-btn">
                    <el-icon :size="24"><Collection /></el-icon>
                    <span>收藏</span>
                </div>
                <div class="action-btn">
                    <el-icon :size="24"><ChatDotRound /></el-icon>
                    <span>评论</span>
                </div>
            </div>
            <div class="comment-input-area">
                 <el-input placeholder="说点什么..." class="comment-input">
                    <template #append>发送</template>
                 </el-input>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { Close, Star, Collection, ChatDotRound } from '@element-plus/icons-vue'

const route = useRoute()
const post = ref({})
const comments = ref([
    { id: 1, username: 'Alice', content: '好棒的分享！', avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png' },
    { id: 2, username: 'Bob', content: '学到了~', avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png' }
])

onMounted(async () => {
  const postId = route.params.id
  try {
    const res = await axios.get(`/api/posts/${postId}`)
    post.value = res.data
  } catch (err) {
    console.error('Failed to load post', err)
  }
})

const formatDate = (dateStr) => {
    if (!dateStr) return ''
    return new Date(dateStr).toLocaleDateString()
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
  background-color: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(5px);
}

.modal-content {
  position: relative;
  width: 80%;
  max-width: 1000px;
  height: 85vh;
  background-color: #fff;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.close-btn {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 10;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  border: 1px solid #eee;
}

.content-flex {
  display: flex;
  width: 100%;
  height: 100%;
}

.image-section {
  flex: 1.5;
  background-color: #f8f8f8;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
}

.image-wrapper {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
}

.info-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-left: 1px solid #f0f0f0;
  background: white;
}

.author-header {
  padding: 20px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
}

.username {
  margin-left: 10px;
  font-weight: 600;
  flex: 1;
}

.scrollable-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.post-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 10px;
  line-height: 1.4;
}

.post-text {
  font-size: 16px;
  color: #333;
  line-height: 1.6;
  white-space: pre-wrap;
  margin-bottom: 15px;
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.tag {
  color: #13386c;
  cursor: pointer;
}

.date {
  font-size: 12px;
  color: #999;
  margin-bottom: 20px;
}

.comment-item {
    display: flex;
    gap: 10px;
    margin-bottom: 15px;
}

.comment-content {
    display: flex;
    flex-direction: column;
}

.comment-user {
    color: #999;
    font-size: 12px;
    margin-bottom: 2px;
}

.bottom-actions {
  padding: 10px 20px;
  border-top: 1px solid #f0f0f0;
}

.interaction-bar {
    display: flex;
    justify-content: space-around;
    margin-bottom: 15px;
}

.action-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    cursor: pointer;
    color: #333;
    font-size: 12px;
}

.action-btn:hover {
    color: #ff2442;
}

.comment-input :deep(.el-input-group__append) {
    background-color: #ff2442;
    color: white;
    cursor: pointer;
}
</style>
