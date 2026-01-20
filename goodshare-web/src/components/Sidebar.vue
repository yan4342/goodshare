<template>
  <div class="sidebar">
    <div class="logo-container">
      <span class="logo-text">GoodShare</span>
    </div>
    
    <div class="menu-items">
      <div class="menu-item" :class="{ active: route.path === '/' }" @click="router.push('/')">
        <el-icon :size="24"><Compass /></el-icon>
        <span class="label">发现</span>
      </div>
      
      <div class="menu-item" :class="{ active: route.path === '/publish' }" @click="router.push('/publish')">
        <el-icon :size="24"><Plus /></el-icon>
        <span class="label">发布</span>
      </div>
      
      <div class="menu-item" :class="{ active: route.path === '/notification' }" @click="router.push('/notification')">
        <el-icon :size="24"><Bell /></el-icon>
        <span class="label">通知</span>
      </div>
      
      <div class="menu-item" :class="{ active: route.path === '/price-compare' }" @click="router.push('/price-compare')">
        <el-icon :size="24"><Goods /></el-icon>
        <span class="label">比价</span>
      </div>

      <div class="menu-item" :class="{ active: route.path === '/me' }" @click="router.push('/me')">
        <el-icon :size="24"><User /></el-icon>
        <span class="label">我</span>
      </div>
    </div>

    <div class="bottom-menu">
      <el-popover
        placement="right-end"
        :width="240"
        trigger="click"
        popper-class="more-popover"
      >
        <template #reference>
          <div class="menu-item">
            <el-icon :size="24"><MoreFilled /></el-icon>
            <span class="label">更多</span>
          </div>
        </template>
        
        <div class="more-menu">
          <div class="menu-option">
            <div class="option-label">
              <el-icon><Moon /></el-icon>
              <span>深色模式</span>
            </div>
            <el-switch v-model="isDark" @change="toggleDark" />
          </div>
          <el-divider style="margin: 8px 0" />
          <div class="menu-option hover-item" @click="handleLogout">
            <div class="option-label">
              <el-icon><SwitchButton /></el-icon>
              <span>退出登录</span>
            </div>
          </div>
        </div>
      </el-popover>
    </div>
  </div>
</template>

<script setup>
import { Compass, Plus, Bell, MoreFilled, User, Moon, SwitchButton } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { ref, onMounted } from 'vue'
import authStore from '../stores/auth'


const router = useRouter()
const route = useRoute()
const isDark = ref(false)

// Check initial state
onMounted(() => {
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'dark' || (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    isDark.value = true
    document.documentElement.classList.add('dark')
  } else {
    isDark.value = false
    document.documentElement.classList.remove('dark')
  }
})

const toggleDark = (value) => {
  if (value) {
    document.documentElement.classList.add('dark')
    localStorage.setItem('theme', 'dark')
  } else {
    document.documentElement.classList.remove('dark')
    localStorage.setItem('theme', 'light')
  }
}

const handleLogout = () => {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100vh;
  background-color: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  padding: 0 20px;
  position: fixed;
  left: 0;
  top: 0;
  z-index: 1001;
  transition: width 0.3s, background-color 0.3s, border-color 0.3s;
  overflow: hidden; /* Hide overflow content when resizing */
  box-sizing: border-box;
}

.logo-container {
  height: 72px;
  display: flex;
  align-items: center;
  padding-left: 12px;
  margin-bottom: 20px;
  white-space: nowrap; /* Prevent text wrapping */
}

.logo-text {
  color: #ff2442;
  font-size: 32px; 
  font-family: 'Billabong', cursive, sans-serif;
  font-weight: 900;
  letter-spacing: -1px;
}

.menu-items {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.menu-item {
  display: flex;
  align-items: center;
  height: 50px;
  padding: 0 12px;
  border-radius: 32px;
  cursor: pointer;
  transition: background-color 0.2s, padding 0.3s;
  color: var(--text-color);
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.bottom-menu {
  margin-top: auto;
  margin-bottom: 24px;
  width: 100%;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .sidebar {
    padding: 0 12px;
  }
  .logo-container {
    padding-left: 0;
    justify-content: center;
  }
  .logo-text {
    display: none; /* Hide logo text on small screens */
  }
  .menu-item {
    padding: 0;
    justify-content: center;
    border-radius: 50%;
    width: 56px; /* Icon size + padding roughly */
    margin: 0 auto;
  }
  .menu-item .label {
    display: none; /* Hide labels */
  }
  .menu-item .el-icon {
    margin-right: 0;
  }
}


.menu-item:hover {
  background-color: var(--hover-bg);
}

.menu-item.active {
  background-color: var(--hover-bg);
  color: var(--text-color); /* Usually active item has primary color or bold text, keeping simple for now or use primary if needed */
}
/* If active needs to be distinct in dark mode */
.menu-item.active .el-icon {
    color: var(--el-color-primary);
}

/* Popover styles */
.more-menu {
  padding: 4px 0;
 
}

.menu-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 8px;
  color: var(--text-color);
}

.menu-option.hover-item:hover {
  background-color: var(--hover-bg);
}

.option-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.menu-item .el-icon {
  margin-right: 12px;
}

.label {
  line-height: 1;
}
</style>
