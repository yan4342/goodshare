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
                <el-avatar :size="32" icon="UserFilled" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
            </div>
            <template #dropdown>
                <el-dropdown-menu>
                    <el-dropdown-item command="logout">退出登录</el-dropdown-item>
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

const handleSearch = () => {
  console.log('Searching for:', searchQuery.value)
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
  background: white;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.navbar-content {
  width: 100%;
  max-width: 1728px; /* Wide screen support */
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.logo {
  color: #ff2442;
  font-size: 28px;
  font-weight: 900;
  cursor: pointer;
  letter-spacing: -1px;
}
.search-bar {
  flex: 1;
  max-width: 500px;
  margin: 0 40px;
}
.search-bar.full-width {
    margin: 0 0; /* Remove left margin if logo is gone */
    max-width: 600px; /* Maybe wider */
}
.search-input :deep(.el-input__wrapper) {
  border-radius: 20px;
  background-color: #f5f5f5;
  box-shadow: none;
  padding-left: 15px;
}
.search-input :deep(.el-input__inner) {
    height: 40px;
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
</style>
