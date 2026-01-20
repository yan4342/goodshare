<template>
  <div class="home-container">
    <Sidebar v-if="isAuthenticated" />
    <el-scrollbar height="100vh" @scroll="handleScroll">
      <div class="main-content" :class="{ 'with-sidebar': isAuthenticated }" ref="scrollContent">
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
          <div v-for="post in posts" :key="post.id" class="post-card" :class="{ 'no-image': !getCoverUrl(post) }" @click="openPost(post)">
            <div v-if="getCoverUrl(post)" class="cover-image" :style="{ backgroundImage: `url('${getCoverUrl(post)}' )` }"></div>
            <div class="card-info">
              <h3 class="post-title">{{ post.title }}</h3>
              <div class="post-meta">
                <div class="author">
                  <el-avatar :size="16" icon="UserFilled" :src="post.user?.avatarUrl || '../assests/avatar.png'" />
                  <span class="author-name" v-if="post.user">{{ post.user.nickname || post.user.username || '用户' }}</span>
                  <span class="author-name" v-else>用户</span>
                </div>
                <div class="metrics">
                  <div class="likes">
                      <el-icon><Star /></el-icon>
                      <span>{{ post.likeCount || 0 }}</span>
                  </div>
                  <div class="views" v-if="post.viewCount !== undefined">
                      <el-icon><View /></el-icon>
                      <span>{{ post.viewCount }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div v-if="loading" class="loading-state">
            <el-skeleton :rows="3" animated />
        </div>

        <div v-if="!loading && posts.length === 0" class="empty-state">
            <el-empty description="暂无内容" />
        </div>
        
        <div v-if="!loading && !hasMore && posts.length > 0" class="no-more-state">
            <el-divider>没有更多了</el-divider>
        </div>
        
        <PostDetail 
          v-if="showPostDetail" 
          :post-id="selectedPostId" 
          @close="closePost" 
        />
      </div>
      </div>
    </el-scrollbar>
  </div>
</template>

<script setup>
import Navbar from '../components/Navbar.vue'
import Sidebar from '../components/Sidebar.vue'
import PostDetail from './PostDetail.vue'
import { ref, onMounted, computed } from 'vue'
import request from '../utils/request'
import { getThumbnailUrl } from '../utils/image'
import { Star, UserFilled, View } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import authStore from '../stores/auth'

const router = useRouter()
const posts = ref([])
const tags = ref([])
const activeTag = ref('推荐')
const isAuthenticated = computed(() => authStore.state.isAuthenticated)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const hasMore = ref(true)
const showPostDetail = ref(false)
const selectedPostId = ref(null)
const scrollContent = ref(null)

const handleScroll = ({ scrollTop }) => {
    if (loading.value || !hasMore.value) return
    
    // Calculate scroll bottom
    // We need the height of the content. 
    // Since el-scrollbar wraps the content, we can get the inner div height via ref
    if (scrollContent.value) {
        const contentHeight = scrollContent.value.clientHeight
        const windowHeight = window.innerHeight // or the height of el-scrollbar (100vh)
        
        // Trigger when within 300px of bottom
        if (contentHeight - scrollTop - windowHeight < 300) {
            loadMore()
        }
    }
}

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
    page.value = 1
    posts.value = []
    hasMore.value = true
    await fetchPosts(tagName)
}

const loadMore = () => {
    if (loading.value || !hasMore.value) return
    fetchPosts(null, true)
}

const fetchPosts = async (tag = null, isLoadMore = false) => {
    if (loading.value) return
    loading.value = true
    try {
        let url = '/posts'
        const targetTag = tag || activeTag.value
        
        let params = {
            page: page.value,
            size: pageSize.value
        }

        if (targetTag === '推荐') {
            if (authStore.state.isAuthenticated && authStore.state.user?.id) {
                url = `/recommendations`
                params.user_id = authStore.state.user.id
            }
        } else {
            url = `/posts`
            params.tag = targetTag
        }
        
        // Use params object which axios supports (assuming request wrapper supports it or we build query string)
        // Check if request wrapper supports second argument config
        const res = await request.get(url, { params })
        
        let newPosts = []
        if (res.data && Array.isArray(res.data)) {
            newPosts = res.data
        } else if (res.data && res.data.records) {
            newPosts = res.data.records
        }

        if (newPosts.length < pageSize.value) {
            hasMore.value = false
        }

        if (isLoadMore) {
            posts.value = [...posts.value, ...newPosts]
        } else {
            posts.value = newPosts
        }
        
        if (newPosts.length > 0) {
            page.value++
        } else {
            hasMore.value = false
        }
    } catch (err) {
        console.error('Failed to fetch posts', err)
        hasMore.value = false
    } finally {
        loading.value = false
    }
}

onMounted(async () => {
  await fetchTags()
  await fetchPosts()
})

const getCoverUrl = (post) => {
    let url = null
    if (post.coverUrl && !post.coverUrl.includes('placehold.co')) {
        url = post.coverUrl
    } else if (post.images) {
        try {
            const imgs = JSON.parse(post.images)
            if (Array.isArray(imgs) && imgs.length > 0) url = imgs[0]
        } catch (e) {}
    }
    return getThumbnailUrl(url)
}

const openPost = (post) => {
  selectedPostId.value = post.id
  showPostDetail.value = true
  // URL update removed to avoid conflict with Vue Router
  // window.history.pushState({}, '', `/post/${post.id}`)
}

const closePost = () => {
  showPostDetail.value = false
  selectedPostId.value = null
  // URL restore removed
  // window.history.pushState({}, '', '/')
}
</script>

<style scoped>
.home-container {
  height: 100vh;
  overflow: hidden;
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
  padding: 8px 0 30px; /* Reduced top padding for alignment with sidebar */
  padding-left: 20px; /* Added left padding for alignment */
  overflow-x: auto;
  font-size: 16px;
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
  margin-bottom: 10px;
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
    border-radius: 16px;
}
.post-card:hover .cover-image::after {
    opacity: 1;
}
.card-info {
    padding: 0 4px;
}
.post-title {
  font-size: 15px;
  color: var(--text-color);
  line-height: 1.4;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
  gap: 4px;
  overflow: hidden;
}
.author-name {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 80px;
}
.metrics {
    display: flex;
    gap: 6px;
    align-items: center;
}
.likes, .views {
  display: flex;
  align-items: center;
  gap: 3px;
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
  border-radius: 12px;
  padding: 12px;
  border: 1px solid var(--border-color);
  height: auto;
  min-height: 120px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.post-card.no-image .post-title {
  font-size: 14px;
  line-height: 1.5;
  -webkit-line-clamp: 4; /* Show more lines for text-only posts */
  margin-bottom: 10px;
  flex: 1;
}
</style>
