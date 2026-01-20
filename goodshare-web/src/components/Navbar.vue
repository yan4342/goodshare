<template>
  <div class="navbar">
    <div class="navbar-content">
      <div class="logo" v-if="!isAuthenticated" @click="$router.push('/')">GoodShare</div>
      <div class="search-bar" :class="{ 'full-width': isAuthenticated }">
        <el-input
          v-model="searchQuery"
          placeholder="搜索你感兴趣的内容..."
          class="search-input"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
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
import { ref, computed } from 'vue'
import { Search, UserFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import authStore from '../stores/auth'

const router = useRouter()
const searchQuery = ref('')
const isAuthenticated = computed(() => authStore.state.isAuthenticated)
const userAvatar = computed(() => authStore.state.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png')
const username = computed(() => authStore.state.user?.nickname || authStore.state.user?.username)

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ path: '/search', query: { q: searchQuery.value } })
  }
}

const handleCommand = (command) => {
    if (command === 'logout') {
        authStore.logout()
        router.push('/login')
    }
}
</script>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: var(--bg-color-overlay);
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.3s;
  margin-left: var(--sidebar-width*0.5);
}
.navbar-content {
  width: 100%;
  max-width: 1728px; /* Wide screen support */
  padding: 0 19px;
  display: flex;
  align-items: center;
  justify-content: space-between;
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
.search-input :deep(.el-input__wrapper) {
  border-radius: 25px;
  background-color: var(--bg-color);
  box-shadow: none;
  padding-left: 15px;
  transition: background-color 0.3s;
}
.search-input :deep(.el-input__inner) {
    height: 40px;
    font-size: 14px;
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
</style>
