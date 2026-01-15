<template>
  <div class="home-container">
    <Sidebar />
    <div class="content-wrapper">
      <Navbar :show-logo="false" />
      <div class="main-content">
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
        <div v-for="post in posts" :key="post.id" class="post-card" @click="$router.push(`/post/${post.id}`)">
          <div class="cover-image" :style="{ backgroundImage: `url(${post.coverUrl || 'https://via.placeholder.com/300x400?text=No+Image'})` }"></div>
          <div class="card-info">
            <h3 class="post-title">{{ post.title }}</h3>
            <div class="post-meta">
              <div class="author">
                <el-avatar :size="20" icon="UserFilled" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
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
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { Star, UserFilled } from '@element-plus/icons-vue'

const posts = ref([])

onMounted(async () => {
  try {
    const res = await axios.get('/api/posts')
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
  console.log('Open post', post.id)
  // Can implement modal detail view here
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background-color: #fff;
  display: flex;
}
.content-wrapper {
  flex: 1;
  margin-left: 240px; /* Width of Sidebar */
  width: calc(100% - 240px);
}
.main-content {
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
  transition: all 0.3s;
}
.tag:hover {
    background-color: #f5f5f5;
    color: #333;
}
.tag.active {
  color: #333;
  font-weight: 600;
  background-color: #f5f5f5;
}
.masonry-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr); /* 5 columns for large screens */
  gap: 20px;
}
@media (max-width: 1400px) {
    .masonry-grid {
        grid-template-columns: repeat(4, 1fr);
    }
}
@media (max-width: 1100px) {
    .masonry-grid {
        grid-template-columns: repeat(3, 1fr);
    }
}
@media (max-width: 800px) {
    .masonry-grid {
        grid-template-columns: repeat(2, 1fr);
    }
}

.post-card {
  break-inside: avoid;
  border-radius: 12px; /* More rounded */
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s;
  background: #fff;
}
.post-card:hover {
  /* transform: translateY(-2px); */
}
.cover-image {
  width: 100%;
  padding-bottom: 133%; /* 3:4 Aspect Ratio */
  background-size: cover;
  background-position: center;
  background-color: #f8f8f8;
  border-radius: 12px; /* Rounded images */
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
  padding: 12px 4px;
}
.post-title {
  font-size: 15px;
  color: #333;
  margin: 0 0 8px;
  line-height: 1.4;
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
  color: #888;
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
.empty-state {
    padding: 50px 0;
    text-align: center;
}
</style>
