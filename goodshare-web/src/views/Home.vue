<template>
  <div class="home-container">
    <Sidebar v-if="isAuthenticated" />
    <div class="main-content" :class="{ 'with-sidebar': isAuthenticated }">
      <Navbar />
      
      <div class="content-body">
        <!-- Tag Filter -->
        <div class="tags-bar">
          <span 
            class="tag" 
            :class="{ active: activeTag === '推荐' }" 
            @click="selectTag('推荐')"
          >推荐</span>
          <span 
            v-for="tag in tags" 
            :key="tag.id" 
            class="tag" 
            :class="{ active: activeTag === tag.name }" 
            @click="selectTag(tag.name)"
          >
            {{ tag.name }}
          </span>
        </div>

      <!-- Waterfall Grid -->
      <div class="masonry-grid">
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
const tags = ref([])
const activeTag = ref('推荐')
const isAuthenticated = computed(() => authStore.state.isAuthenticated)

const fetchTags = async () => {
    try {
        const res = await request.get('/tags')
        tags.value = res.data
    } catch (err) {
        console.error('Failed to fetch tags', err)
    }
}

const selectTag = async (tagName) => {
    activeTag.value = tagName
    await fetchPosts(tagName)
}

const fetchPosts = async (tag = null) => {
    try {
        const url = (tag && tag !== '推荐') ? `/posts?tag=${encodeURIComponent(tag)}` : '/posts'
        const res = await request.get(url)
        posts.value = res.data
    } catch (err) {
        console.error('Failed to fetch posts', err)
        posts.value = []
    }
}

onMounted(async () => {
  await fetchTags()
  await fetchPosts()
})

const getCoverUrl = (post) => {
    if (post.coverUrl && !post.coverUrl.includes('placehold.co')) return post.coverUrl
    if (post.images) {
        try {
            const imgs = JSON.parse(post.images)
            if (Array.isArray(imgs) && imgs.length > 0) return imgs[0]
        } catch (e) {}
    }
    return null
}

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
.tags-bar {
  display: flex;
  gap: 30px;
  padding: 20px 0 30px;
  overflow-x: auto;
  font-size: 20px;
  color: var(--text-color-secondary);
  justify-content: flex-start;
}
.tag {
  cursor: pointer;
  white-space: nowrap;
  padding: 8px 16px;
  border-radius: 24px;
  transition: color 0.3s, background-color 0.3s;
}
.tag.active {
  color: var(--text-color);
  font-weight: 600;
  background-color: var(--hover-bg);
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
/* Optional: Add a dark overlay on hover */
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
}
.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: var(--text-color-secondary);
}
.author {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}
.author-name {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100px;
}
.likes {
  display: flex;
  align-items: center;
  gap: 4px;
}
.empty-state {
    padding: 100px 0;
    display: flex;
    justify-content: center;
}

/* Text-only post styling */
.post-card.no-image .cover-image {
  display: none;
}

.post-card.no-image {
  background-color: var(--bg-color-overlay);
  border-radius: 16px;
  padding: 16px;
  border: 1px solid var(--border-color);
  height: auto;
  min-height: 150px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.post-card.no-image .post-title {
  font-size: 16px;
  line-height: 1.5;
  -webkit-line-clamp: 4; /* Show more lines for text-only posts */
  margin-bottom: 12px;
  flex: 1;
}
</style>
