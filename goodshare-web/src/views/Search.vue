<template>
  <div class="home-container">
    <div class="main-content" :class="{ 'with-sidebar': isAuthenticated }">
      <Navbar />
      
      <div class="content-body">
        <div class="search-layout">
          <div class="search-results">
            <h2 class="search-title" v-if="tag">标签: <span style="color:#ff2442">#{{ tag }}</span></h2>
            <h2 class="search-title" v-else>搜索结果: "<span style="color:#ff2442">{{ query }}</span>"</h2>

            <el-tabs v-model="activeTab" class="search-tabs">
              <el-tab-pane label="帖子" name="posts">
                <!-- Waterfall Grid -->
                <div class="masonry-grid" v-if="posts.length > 0">
                  <div v-for="post in posts" :key="post.id" class="post-card" :class="{ 'no-image': !getCoverUrl(post) }" @click="openPost(post, $event)">
                    <div v-if="getCoverUrl(post)" class="cover-image" :style="{ backgroundImage: `url('${getCoverUrl(post)}')` }"></div>
                    <div class="card-info">
                      <h3 class="post-title" v-html="highlightText(post.title)"></h3>
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
                    <el-empty description="未找到相关帖子" />
                </div>
              </el-tab-pane>

              <el-tab-pane label="用户" name="users" v-if="!tag">
                <div class="user-list" v-if="users.length > 0">
                  <div v-for="user in users" :key="user.id" class="user-card" @click="openUser(user)">
                    <el-avatar :size="60" :src="user.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
                    <div class="user-info">
                      <div class="user-name">
                        <span class="nickname" v-html="highlightText(user.nickname || user.username)"></span>
                        <span class="username" v-html="'@' + highlightText(user.username)"></span>
                      </div>
                      <div class="user-bio" v-if="user.bio">{{ user.bio }}</div>
                    </div>
                    <el-button type="primary" round @click.stop="openUser(user)">查看主页</el-button>
                  </div>
                </div>
                 <div v-else class="empty-state">
                    <el-empty description="未找到相关用户" />
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>

          <!-- Hot Search Sidebar -->
          <div class="search-sidebar">
            <div class="hot-search-card">
              <h3 class="hot-title">
                <el-icon color="#ff2442"><Trophy /></el-icon>
                全站热搜
              </h3>
              <div class="hot-list">
                <div v-for="(item, index) in hotKeywords" :key="item.id" class="hot-item" @click="handleHotClick(item.keyword)">
                  <span class="hot-rank" :class="{ 'top-3': index < 3 }">{{ index + 1 }}</span>
                  <span class="hot-keyword">{{ item.keyword }}</span>
                  <span class="hot-count">{{ formatCount(item.searchCount) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <PostDetail 
        v-if="showPostDetail" 
        :post-id="selectedPostId"
        :origin-rect="clickedRect" 
        @close="closePost"
        @update="handlePostUpdate" 
      />
    </div>
  </div>
</template>

<script setup>
import Navbar from '../components/Navbar.vue'
import PostDetail from './PostDetail.vue'
import { ref, onMounted, computed, watch } from 'vue'
import request from '../utils/request'
import { Star, UserFilled, Trophy } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
import { getThumbnailUrl } from '../utils/image'

const router = useRouter()
const route = useRoute()
const posts = ref([])
const users = ref([])
const hotKeywords = ref([])
const isAuthenticated = computed(() => authStore.isAuthenticated)
const query = computed(() => route.query.q || '')
const tag = computed(() => route.query.tag || '')
const activeTab = ref('posts')
const showPostDetail = ref(false)
const selectedPostId = ref(null)
const clickedRect = ref(null)

// Ensure activeTab is correct when tag is present
const checkActiveTab = () => {
    if (tag.value) {
        activeTab.value = 'posts'
    }
}

const searchPosts = async () => {
    checkActiveTab() // Ensure tab is correct before searching
    if (!query.value && !tag.value) return
    try {
        let url = '/search'
        if (tag.value) {
            url += `?tag=${encodeURIComponent(tag.value)}`
        } else {
            url += `?query=${encodeURIComponent(query.value)}`
        }
        
        const res = await request.get(url)
        // Backend now returns List<PostDocument> directly
        if (Array.isArray(res.data)) {
            posts.value = res.data.map(doc => {
                return {
                    id: doc.id,
                    title: doc.title,
                    content: doc.content,
                    coverUrl: doc.coverUrl,
                    likeCount: doc.likeCount,
                    viewCount: doc.viewCount,
                    commentCount: doc.commentCount,
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

const searchUsers = async () => {
    // Only search users if there is a text query (not tag search)
    if (!query.value || tag.value) {
        users.value = []
        return
    }
    try {
        const res = await request.get(`/search/users?query=${encodeURIComponent(query.value)}`)
        users.value = res.data || []
    } catch (err) {
        console.error('Failed to search users', err)
        users.value = []
    }
}

const fetchHotKeywords = async () => {
  try {
    const res = await request.get('/search/hot')
    hotKeywords.value = res.data || []
  } catch (err) {
    console.error('Failed to fetch hot keywords', err)
  }
}

const handleHotClick = (keyword) => {
  router.push({ path: '/search', query: { q: keyword } })
}

const formatCount = (count) => {
  if (count > 10000) {
    return (count / 10000).toFixed(1) + 'w'
  }
  return count
}

onMounted(() => {
  checkActiveTab()
  searchPosts()
  searchUsers()
  fetchHotKeywords()
})

watch(() => [route.query.q, route.query.tag], () => {
    checkActiveTab()
    searchPosts()
    searchUsers()
})

const openPost = (post, event) => {
    if (event && event.currentTarget) {
        clickedRect.value = event.currentTarget.getBoundingClientRect()
    }
    selectedPostId.value = post.id
    showPostDetail.value = true
}

// 关闭帖子详情，清除相关状态
const closePost = () => {
    showPostDetail.value = false
    selectedPostId.value = null
    clickedRect.value = null
}

const handlePostUpdate = (updatedFields) => {
    // 更新 posts 数组中对应的帖子
    const postIndex = posts.value.findIndex(p => p.id === updatedFields.id)
    if (postIndex !== -1) {
        posts.value[postIndex] = { ...posts.value[postIndex], ...updatedFields }
    }
}

const openUser = (user) => {
  router.push(`/user/${user.id}`)
}

const escapeHtml = (str) => str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

const highlightText = (text) => {
    if (!text || (!query.value && !tag.value)) return escapeHtml(text)
    const keyword = tag.value || query.value
    if (!keyword) return escapeHtml(text)
    const escaped = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const regex = new RegExp(`(${escaped})`, 'gi')
    return escapeHtml(text).replace(regex, '<span style="color:#ff2442;font-weight:600">$1</span>')
}

const getCoverUrl = (post) => {
    let url = null
    if (post.coverUrl && !post.coverUrl.includes('placehold.co')) {
        url = post.coverUrl
    }
    return getThumbnailUrl(url)
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
    max-width: 1600px;
    margin: 0 auto;
    padding: 20px 32px;
}
.search-layout {
  display: flex;
  gap: 40px;
  align-items: flex-start;
}
.search-results {
  flex: 1;
  min-width: 0; /* Prevent flex item from overflowing */
}
.search-sidebar {
  width: 300px;
  flex-shrink: 0;
  position: sticky;
  top: 84px; /* Navbar height + padding */
}
.hot-search-card {
  background: var(--bg-color);
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
.hot-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-color);
}
.hot-item {
  display: flex;
  align-items: center;
  padding: 10px 0;
  cursor: pointer;
  transition: background-color 0.2s;
  border-radius: 4px;
}
.hot-item:hover {
  background-color: var(--bg-color-page);
}
.hot-rank {
  width: 20px;
  font-weight: 700;
  font-style: italic;
  margin-right: 10px;
  text-align: center;
  color: #999;
}
.hot-rank.top-3 {
  color: #ff2442;
}
.hot-keyword {
  flex: 1;
  font-size: 14px;
  color: var(--text-color);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hot-count {
  font-size: 12px;
  color: #999;
}

.search-title {
    margin-bottom: 24px;
    font-size: 24px;
    font-weight: 600;
    color: var(--text-color);
}
.masonry-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
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
  border-radius: 16px;
  margin-bottom: 12px;
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
  font-weight: 500;
  line-clamp: 2;
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
    max-width: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.likes {
  display: flex;
  align-items: center;
  gap: 4px;
}

.search-tabs {
  margin-bottom: 20px;
}
.search-tabs :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background-color: var(--border-color);
}
.search-tabs :deep(.el-tabs__item) {
    font-size: 16px;
    color: var(--text-color-secondary);
}
.search-tabs :deep(.el-tabs__item.is-active) {
    color: var(--primary-color);
    font-weight: 600;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.user-card {
  display: flex;
  align-items: center;
  padding: 16px;
  background: var(--bg-color);
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.2s;
  border: 1px solid var(--border-color);
}
.user-card:hover {
  background-color: var(--bg-color-page);
}
.user-info {
  flex: 1;
  margin-left: 16px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.user-name {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.nickname {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-color);
}
.username {
  font-size: 14px;
  color: var(--text-color-secondary);
}
.user-bio {
  font-size: 14px;
  color: var(--text-color-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 帖子卡片样式 */
.post-card.no-image .cover-image {
  display: none;
}
/* 文本帖子样式 */
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
