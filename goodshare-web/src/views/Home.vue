<template>
  <div class="home-container">
    <Sidebar v-if="isAuthenticated" />
    <el-scrollbar height="100vh" @scroll="handleScroll" ref="scrollbarRef">
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
        <div class="waterfall-grid">
          <div class="column" v-for="(colPosts, index) in columns" :key="index">
            <div v-for="post in colPosts" :key="post.id" class="post-card" :class="{ 'no-image': !getCoverUrl(post) }" @click="openPost(post, $event)">
                <img v-if="getCoverUrl(post)" :src="getCoverUrl(post)" class="cover-image" loading="lazy" />
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
                    <div class="comments" style="display: flex; align-items: center;">
                        <el-icon><ChatDotRound /></el-icon>
                        <span style="margin-left: 4px;">{{ post.commentCount || 0 }}</span>
                    </div>
                    <div class="views" v-if="post.viewCount !== undefined">
                        <el-icon><View /></el-icon>
                        <span>{{ post.viewCount }}</span>
                    </div>
                    </div>
                    <button class="dislike-btn" @click.stop="handleDislike(post)">
                        <el-icon><CircleClose /></el-icon>
                        <span>不喜欢</span>
                    </button>
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
          :origin-rect="clickedRect" 
          @close="closePost"
          @update="handlePostUpdate" 
        />

        </div>
      </div>
    </el-scrollbar>

    <div class="refresh-fab" @click="refreshPosts">
        <el-icon><Refresh /></el-icon>
    </div>
  </div>
</template>

<style scoped>
.refresh-fab {
    position: fixed;
    bottom: 40px;
    right: 40px;
    width: 50px;
    height: 50px;
    border-radius: 50%;
    background-color: var(--el-color-primary);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    cursor: pointer;
    transition: all 0.3s;
    z-index: 100;
    font-size: 24px;
}

.refresh-fab:hover {
    transform: scale(1.1) rotate(180deg);
    background-color: var(--el-color-primary-dark-2);
}
</style>

<script setup>
import Navbar from '../components/Navbar.vue'
import Sidebar from '../components/Sidebar.vue'
import PostDetail from './PostDetail.vue'
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import request from '../utils/request'
import { getThumbnailUrl } from '../utils/image'
import { Star, UserFilled, View, Refresh, CircleClose } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import authStore from '../stores/auth'
import homeStore from '../stores/home'

const router = useRouter()
// Use store state for persistence
const posts = computed(() => homeStore.state.posts)
const tags = ref([])
const activeTag = computed(() => homeStore.state.activeTag)
const isAuthenticated = computed(() => authStore.state.isAuthenticated)
const page = computed(() => homeStore.state.page)
const pageSize = ref(10)
const loading = ref(false)
const hasMore = computed(() => homeStore.state.hasMore)
const showPostDetail = ref(false)
const selectedPostId = ref(null)
const scrollContent = ref(null)
const scrollbarRef = ref(null)
const clickedRect = ref(null)
const fetchId = ref(0)
const columnCount = ref(5)

const updateColumnCount = () => {
    const width = window.innerWidth
    if (width < 768) {
        columnCount.value = 2
    } else if (width < 1000) {
        columnCount.value = 3
    } else if (width < 1450) {
        columnCount.value = 4
    } else {
        columnCount.value = 5
    }
}

const columns = computed(() => {
    const cols = Array.from({ length: columnCount.value }, () => [])
    posts.value.forEach((post, index) => {
        cols[index % columnCount.value].push(post)
    })
    return cols
})

const handleScroll = ({ scrollTop }) => {
    homeStore.setScrollTop(scrollTop)
    
    if (loading.value || !hasMore.value) return
    
    if (scrollContent.value) {
        const contentHeight = scrollContent.value.clientHeight
        const windowHeight = window.innerHeight 
        
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
    homeStore.reset() // Keep activeTag, clear posts
    homeStore.setActiveTag(tagName)
    await fetchPosts(tagName)
}

const loadMore = () => {
    if (loading.value || !hasMore.value) return
    fetchPosts(null, true)
}

const fetchPosts = async (tag = null, isLoadMore = false, force = false) => {
    if (loading.value && !force) return
    
    const currentFetchId = Date.now()
    fetchId.value = currentFetchId
    
    loading.value = true
    try {
        let url = '/posts'
        const targetTag = tag || activeTag.value
        const currentPage = isLoadMore ? page.value : 1
        
        let params = {
            page: currentPage,
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
        
        const res = await request.get(url, { params })
        
        if (fetchId.value !== currentFetchId) return

        let newPosts = []
        if (res.data && Array.isArray(res.data)) {
            newPosts = res.data
        } else if (res.data && res.data.records) {
             newPosts = res.data.records
        }
        
        if (newPosts.length < pageSize.value) {
            homeStore.setHasMore(false)
        }
        
        if (isLoadMore) {
            homeStore.appendPosts(newPosts)
            homeStore.setPage(currentPage + 1)
        } else {
            homeStore.setPosts(newPosts)
            homeStore.setPage(2)
            homeStore.setHasMore(newPosts.length >= pageSize.value)
        }
        
    } catch (err) {
        if (fetchId.value === currentFetchId) {
            console.error('Failed to fetch posts', err)
        }
    } finally {
        if (fetchId.value === currentFetchId) {
            loading.value = false
        }
    }
}

const refreshPosts = async () => {
    homeStore.reset()
    await fetchPosts(null, false, true)
}

const handlePostUpdate = (updatedFields) => {
    homeStore.updatePost(updatedFields)
}

const openPost = (post, event) => {
    if (event && event.currentTarget) {
        clickedRect.value = event.currentTarget.getBoundingClientRect()
    }
    selectedPostId.value = post.id
    showPostDetail.value = true
}

const closePost = () => {
    showPostDetail.value = false
    selectedPostId.value = null
    clickedRect.value = null
}

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

const handleDislike = async (post) => {
    if (!authStore.state.isAuthenticated) {
        ElMessage.warning('请先登录')
        router.push('/login')
        return
    }
    try {
        await request.post(`/posts/${post.id}/dislike`)
        homeStore.removePost(post.id)
    } catch (err) {
        console.error('Failed to dislike post', err)
        ElMessage.error('操作失败')
    }
}

onMounted(async () => {
    updateColumnCount()
    window.addEventListener('resize', updateColumnCount)
    
    await fetchTags()
    
    if (posts.value.length === 0) {
        await fetchPosts()
    } else {
        nextTick(() => {
            if (scrollbarRef.value) {
                scrollbarRef.value.setScrollTop(homeStore.state.scrollTop)
            }
        })
    }
})

onUnmounted(() => {
    window.removeEventListener('resize', updateColumnCount)
})
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
  gap: 10px;
  padding: 0 0 30px; /* Reduced top padding for alignment with sidebar */
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
.waterfall-grid {
  display: flex;
  gap: 20px;
  width: 100%;
  align-items: flex-start;
}
.column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.post-card {
  cursor: pointer;
  margin-bottom: 0; /* Handled by gap */
  transition: transform 0.2s;
}
.post-card:hover {
  transform: translateY(-4px);
}
.cover-image {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 16px;
  margin-bottom: 10px;
  background-color: var(--border-color);
  min-height: 1px; /* Ensure element has height context during loading */
}
/* Overlay logic needs update since we use img tag now */
.post-card {
    position: relative;
}
.post-card::after {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0,0,0,0.03);
    opacity: 0;
    transition: opacity 0.2s;
    border-radius: 12px;
    pointer-events: none;
}
.post-card:hover::after {
    opacity: 1;
}
.card-info {
    padding: 0 4px;
}
.post-title {
  font-size: 14px;
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
.dislike-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: var(--text-color-secondary);
  cursor: pointer;
}
.dislike-btn:hover {
  color: var(--el-color-danger);
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
