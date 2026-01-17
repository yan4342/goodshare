<template>
  <div class="home-container">
    <Sidebar v-if="isAuthenticated" />
    <div class="main-content" :class="{ 'with-sidebar': isAuthenticated }">
      <Navbar />
      
      <div class="content-body">
        <h2 class="search-title">搜索结果: "{{ query }}"</h2>

        <!-- Waterfall Grid -->
        <div class="masonry-grid" v-if="posts.length > 0">
          <div v-for="post in posts" :key="post.id" class="post-card" @click="openPost(post)">
            <div class="cover-image" :style="{ backgroundImage: `url(${post.coverUrl || 'https://placehold.co/300x400?text=No+Image'})` }"></div>
            <div class="card-info">
              <h3 class="post-title">{{ post.title }}</h3>
              <div class="post-meta">
                <div class="author">
                  <el-avatar :size="20" icon="UserFilled" :src="post.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
                  <span class="author-name">{{ post.user?.nickname || post.user?.username || '用户' }}</span>
                </div>
                <div class="likes">
                  <el-icon><Star /></el-icon>
                  <span>{{ post.likeCount || 0 }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div v-else class="empty-state">
            <el-empty description="未找到相关内容" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import Navbar from '../components/Navbar.vue'
import Sidebar from '../components/Sidebar.vue'
import { ref, onMounted, computed, watch } from 'vue'
import request from '../utils/request'
import { Star, UserFilled } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import authStore from '../stores/auth'

const router = useRouter()
const route = useRoute()
const posts = ref([])
const isAuthenticated = computed(() => authStore.state.isAuthenticated)
const query = computed(() => route.query.q || '')

const searchPosts = async () => {
    if (!query.value) return
    try {
        const res = await request.get(`/search?query=${encodeURIComponent(query.value)}`)
        // Backend now returns List<PostDocument> directly
        if (Array.isArray(res.data)) {
            posts.value = res.data.map(doc => {
                return {
                    id: doc.id,
                    title: doc.title,
                    content: doc.content,
                    coverUrl: doc.coverUrl,
                    likeCount: doc.likeCount,
                    user: {
                        id: doc.userId,
                        username: doc.username,
                        nickname: doc.nickname,
                        avatarUrl: doc.avatarUrl
                    }
                }
            })
        } else {
            posts.value = []
        }
    } catch (err) {
        console.error('Failed to search posts', err)
        posts.value = []
    }
}

onMounted(() => {
  searchPosts()
})

watch(() => route.query.q, () => {
    searchPosts()
})

const openPost = (post) => {
  router.push(`/post/${post.id}`)
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background-color: var(--bg-color-overlay);
  transition: background-color 0.3s;
}
.main-content {
  margin: 0 auto;
  transition: margin-left 0.3s, width 0.3s;
}
.main-content.with-sidebar {
    margin-left: var(--sidebar-width);
    width: calc(100% - var(--sidebar-width));
}
.content-body {
    max-width: 1800px;
    margin: 0 auto;
    padding: 20px 32px;
}
.search-title {
    margin-bottom: 24px;
    font-size: 24px;
    font-weight: 600;
    color: var(--text-color);
}
.masonry-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}
.post-card {
  cursor: pointer;
  break-inside: avoid;
  margin-bottom: 24px;
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
  border-radius: 20px;
  margin-bottom: 14px;
  background-color: var(--border-color);
  position: relative;
}
.cover-image::after {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0,0,0,0.03);
    opacity: 0;
    transition: opacity 0.2s;
    border-radius: 20px;
}
.post-card:hover .cover-image::after {
    opacity: 1;
}
.card-info {
  padding: 0 6px;
}
.post-title {
  font-size: 18px;
  color: var(--text-color);
  line-height: 1.4;
  margin-bottom: 10px;
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
  color: var(--text-color-secondary);
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
