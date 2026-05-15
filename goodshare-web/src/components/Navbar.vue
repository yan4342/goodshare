<template>
  <div class="navbar">
    <div class="navbar-content">
      <div class="logo" v-if="!isAuthenticated" @click="$router.push('/')">GoodShare</div>
      <div class="search-bar" :class="{ 'full-width': isAuthenticated }">
        <el-autocomplete
          v-model="searchQuery"
          :fetch-suggestions="querySearchAsync"
          placeholder="搜索你感兴趣的内容..."
          class="search-input"
          popper-class="navbar-search-popper"
          :trigger-on-focus="true"
          @select="handleSelect"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #default="{ item }">
            <div v-if="item.type === 'header'" class="suggest-header">
                <el-icon color="#ff2442"><Trophy /></el-icon>
                <span>全站热搜</span>
            </div>
            <div v-else-if="item.type === 'hot'" class="suggest-item hot-suggest-item">
              <span class="hot-rank" :class="{ 'top-3': item.rank <= 3 }">{{ item.rank }}</span>
              <span class="hot-keyword">{{ item.value }}</span>
            </div>
            <div v-else class="suggest-item" tabindex="0">
              {{ getPrefix(item.value) }}<em class="suggest_high_light">{{ getMatchText(item.value) }}</em>{{ getSuffix(item.value) }}
            </div>
          </template>
        </el-autocomplete>
      </div>
      <div class="menu-items">
        <template v-if="!isAuthenticated">
             <el-button type="primary" round class="login-btn" @click="$router.push('/login')">登录</el-button>
        </template>
        <div v-else class="user-actions">
           <el-dropdown trigger="click" @command="handleCommand">
            <div class="avatar-wrapper">
                <el-avatar :size="48" icon="UserFilled" :src="userAvatar" />
            </div>
            <template #dropdown>
                <el-dropdown-menu>
                    <el-dropdown-item disabled>{{ username }}</el-dropdown-item>
                    <el-dropdown-item v-if="isAdmin" command="reindex">重建搜索索引 (Admin)</el-dropdown-item>
                    <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
            </template>
           </el-dropdown>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, UserFilled, Trophy } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
import request from '../utils/request'

const router = useRouter()
const searchQuery = ref('')
const isAuthenticated = computed(() => authStore.isAuthenticated)
const userAvatar = computed(() => authStore.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png')
const username = computed(() => authStore.user?.nickname || authStore.user?.username)
const isAdmin = computed(() => authStore.user?.role?.includes('ADMIN') || false)

const hotKeywords = ref([])

const fetchHotKeywords = async () => {
  try {
    const res = await request.get('/search/hot')
    hotKeywords.value = res.data || []
  } catch (err) {
    console.error('Failed to fetch hot keywords', err)
  }
}

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ path: '/search', query: { q: searchQuery.value } })
  }
}

const querySearchAsync = async (queryString, cb) => {
  if (!queryString || queryString.trim().length < 1) {
    // Show hot keywords when empty
    if (hotKeywords.value.length === 0) {
      await fetchHotKeywords()
    }
    const hotItems = hotKeywords.value.map((item, index) => ({
      value: item.keyword,
      type: 'hot',
      rank: index + 1,
      count: item.searchCount
    }))
    // Add a header item
    if (hotItems.length > 0) {
        hotItems.unshift({ value: '全站热搜', type: 'header' })
    }
    cb(hotItems)
    return
  }
  try {
    const res = await request.get('/search/suggest', { params: { query: queryString } })
    const suggestions = res.data.map(title => ({ value: title, type: 'suggest' }))
    cb(suggestions)
  } catch (error) {
    console.error('Fetch suggestions failed:', error)
    cb([])
  }
}

const handleSelect = (item) => {
  if (item.type === 'header') return
  searchQuery.value = item.value
  handleSearch()
}

const getPrefix = (text) => {
  if (!searchQuery.value) return ''
  const q = searchQuery.value.toLowerCase()
  const t = text.toLowerCase()
  const index = t.indexOf(q)
  if (index === -1) return ''
  return text.substring(0, index)
}

const getMatchText = (text) => {
  if (!searchQuery.value) return ''
  const q = searchQuery.value.toLowerCase()
  const t = text.toLowerCase()
  const index = t.indexOf(q)
  if (index === -1) return ''
  // Return the original casing for the match
  return text.substring(index, index + searchQuery.value.length)
}

const getSuffix = (text) => {
  if (!searchQuery.value) return text
  const q = searchQuery.value.toLowerCase()
  const t = text.toLowerCase()
  const index = t.indexOf(q)
  if (index === -1) return text
  return text.substring(index + searchQuery.value.length)
}

const handleCommand = async (command) => {
    if (command === 'logout') {
        authStore.logout()
        router.push('/login')
    } else if (command === 'reindex') {
        try {
            await request.post('/posts/reindex')
            ElMessage.success('索引重建成功')
        } catch (error) {
            console.error(error)
            ElMessage.error('索引重建失败：需要管理员权限')
        }
    }
}
</script>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: var(--bg-color-overlay);
  width: 100%;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.3s;
}
.navbar-content {
  width: 100%;
  box-sizing: border-box;
  max-width: 1728px; /* Wide screen support */
  padding: 0 19px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.menu-items {
    flex-shrink: 0;
}
.logo {
  color: #ff2442;
  font-size: 22px;
  font-weight: 900;
  cursor: pointer;
  letter-spacing: -1px;
}
.search-bar {
  flex: 1;
  max-width: 500px;
    margin: 0 auto;
    margin-top: 12px;
  display: flex;
  justify-content: center;
  align-items: center;
}
.search-bar.full-width {
    margin: 0 auto; /* Remove left margin if logo is gone */
    max-width: 600px; /* Maybe wider */

}
.search-input {
    --el-input-height: 52px;
}
.search-input :deep(.el-input__wrapper) {
  border-radius: 999px !important;
  background-color: var(--hover-bg);
  box-shadow: none;
  padding-left: 15px;
  transition: background-color 0.3s;
  height: 52px; /* Ensure wrapper has correct height */
}
.search-input :deep(.el-input__inner) {
    height: 100%; /* Fill the wrapper */
    font-size: 16px;
    line-height: 52px;
}
.login-btn, .publish-btn {
  background-color: #ff2442;
  border-color: #ff2442;
  font-weight: 600;
}
.login-btn:hover, .publish-btn:hover {
  background-color: #e61e3a;
  border-color: #e61e3a;
}
.avatar-wrapper {
  position: relative;
  display: inline-block;
  margin-left: 16px;
  margin-right: 16px;
}
.avatar-wrapper:hover .avatar-menu {
  display: block;
}
.avatar-menu {
  position: absolute;
  top: 50px;
  right: 0;
  background-color: var(--bg-color);
  border-radius: 4px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  padding: 8px 0;
  display: none;
}

.hot-search-container {
  padding: 5px;
}
.hot-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-color);
}
.hot-item {
  display: flex;
  align-items: center;
  padding: 8px 8px;
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
  font-size: 14px;
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

.suggest-item {
  display: flex;
  align-items: center;
  font-size: 14px;
  color: var(--text-color);
  width: 100%;
}

.suggest_high_light {
  color: #ff2442;
  font-style: normal;
  font-weight: 600;
}
</style>

<style>
/* Global styles for search suggestion popper to bypass scoped style isolation */
.navbar-search-popper .suggest-item {
  display: flex;
  align-items: center;
  font-size: 14px;
  color: var(--text-color);
  width: 100%;
  line-height: normal;
  padding: 4px 0;
}

.navbar-search-popper .suggest_high_light {
  color: #ff2442;
  font-style: normal;
  font-weight: 600;
}

/* Force border radius for search input */
.navbar .search-input .el-input__wrapper {
  border-radius: 999px !important;
}
</style>

<style scoped>.suggest_high_light {
  color: #ff2442;
  font-style: normal;
  font-weight: 600;
}

.navbar-search-popper .suggest-header {
    padding: 8px 12px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text-color);
    display: flex;
    align-items: center;
    gap: 6px;
    border-bottom: 1px solid var(--border-color);
    margin-bottom: 4px;
    pointer-events: none; /* Make header unclickable */
}

.navbar-search-popper .hot-suggest-item {
    padding: 6px 0;
}

.navbar-search-popper .hot-rank {
  width: 20px;
  font-weight: 700;
  font-style: italic;
  margin-right: 10px;
  text-align: center;
  color: #999;
  font-size: 14px;
}

.navbar-search-popper .hot-rank.top-3 {
  color: #ff2442;
}

.navbar-search-popper .hot-keyword {
    color: var(--text-color);
}
</style>
