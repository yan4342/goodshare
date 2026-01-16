<template>
  <div class="home-container">
    <Sidebar v-if="isAuthenticated" />
    <div class="main-content" :class="{ 'with-sidebar': isAuthenticated }">
      <Navbar />
      
      <div class="content-body">
        <!-- Tag Filter (Mock) -->
        <div class="tags-bar">
          <span class="tag active">推荐</span>
        <span class="tag">穿搭</span>
        <span class="tag">美食</span>
        <span class="tag">彩妆</span>
        <span class="tag">影视</span>
        <span class="tag">职场</span>
        <span class="tag">数码</span>
        <span class="tag">家居</span>
        <span class="tag">游戏</span>
        <span class="tag">旅行</span>
        <span class="tag">健身</span>
      </div>

      <!-- Waterfall Grid -->
      <div class="masonry-grid">
        <div v-for="post in posts" :key="post.id" class="post-card" @click="openPost(post)">
          <div class="cover-image" :style="{ backgroundImage: `url(${post.coverUrl || 'https://via.placeholder.com/300x400?text=No+Image'})` }"></div>
          <div class="card-info">
            <h3 class="post-title">{{ post.title }}</h3>
            <div class="post-meta">
              <div class="author">
                <el-avatar :size="20" icon="UserFilled" :src="post.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
                <span class="author-name">{{ post.user?.username || '用户' }}</span>
              </div>
              <div class="likes">
                <el-icon><Star /></el-icon>
                <span>{{ Math.floor(Math.random() * 1000) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div v-if="posts.length === 0" class="empty-state">
          <el-empty description="暂无内容" />
      </div>
    </div>
    </div>
  </div>
</template>

<script setup>
import Navbar from '../components/Navbar.vue'
import Sidebar from '../components/Sidebar.vue'
import { ref, onMounted, computed } from 'vue'
import request from '../utils/request'
import { Star, UserFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import authStore from '../stores/auth'

const router = useRouter()
const posts = ref([])
const isAuthenticated = computed(() => authStore.state.isAuthenticated)

onMounted(async () => {
  try {
    const res = await request.get('/posts')
    posts.value = res.data
  } catch (err) {
    console.error('Failed to fetch posts', err)
    // Mock data if backend is empty or failed
    if (posts.value.length === 0) {
        // posts.value = [
        //     { id: 1, title: 'Mock Post 1', coverUrl: 'https://via.placeholder.com/300x400' },
        //     { id: 2, title: 'Mock Post 2', coverUrl: 'https://via.placeholder.com/300x500' },
        // ]
    }
  }
})

const openPost = (post) => {
  router.push(`/post/${post.id}`)
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background-color: #fff;
}
.main-content {
  margin: 0 auto;
  transition: margin-left 0.3s, width 0.3s;
}
.main-content.with-sidebar {
    margin-left: 240px;
    width: calc(100% - 240px);
}
.content-body {
    max-width: 1600px;
    margin: 0 auto;
    padding: 10px 24px;
}
.tags-bar {
  display: flex;
  gap: 30px;
  padding: 15px 0 25px;
  overflow-x: auto;
  font-size: 16px;
  color: #999;
  justify-content: flex-start;
}
.tag {
  cursor: pointer;
  white-space: nowrap;
  padding: 6px 12px;
  border-radius: 20px;
}
.tag.active {
  color: #333;
  font-weight: 600;
  background-color: #f5f5f5;
}
.masonry-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
}
.post-card {
  cursor: pointer;
  break-inside: avoid;
  margin-bottom: 20px;
  transition: transform 0.2s;
}
.post-card:hover {
  transform: translateY(-4px);
}
.cover-image {
  width: 100%;
  padding-bottom: 133%; /* 3:4 Aspect Ratio */
  background-size: cover;
  background-position: center;
  border-radius: 16px;
  margin-bottom: 12px;
  background-color: #f0f0f0;
  position: relative;
}
/* Optional: Add a dark overlay on hover */
.cover-image::after {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0,0,0,0.03);
    opacity: 0;
    transition: opacity 0.2s;
    border-radius: 12px;
}
.post-card:hover .cover-image::after {
    opacity: 1;
}
.card-info {
  padding: 0 4px;
}
.post-title {
  font-size: 14px;
  color: #333;
  line-height: 1.4;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-weight: 500;
}
.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #999;
}
.author {
  display: flex;
  align-items: center;
  gap: 6px;
}
.author-name {
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.likes {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
