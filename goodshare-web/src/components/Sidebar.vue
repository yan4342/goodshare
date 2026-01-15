<template>
  <div class="sidebar">
    <div class="logo-container">
      <span class="logo-text">GoodShare</span>
    </div>
    
    <div class="menu-items">
      <div class="menu-item" :class="{ active: $route.path === '/' }" @click="$router.push('/')">
        <el-icon :size="24"><Compass /></el-icon>
        <span class="label">发现</span>
      </div>
      
      <div class="menu-item" :class="{ active: $route.path === '/publish' }" @click="$router.push('/publish')">
        <el-icon :size="24"><Plus /></el-icon>
        <span class="label">发布</span>
      </div>
      
      <div class="menu-item" :class="{ active: $route.path === '/notifications' }" @click="$router.push('/notifications')">
        <el-icon :size="24"><Bell /></el-icon>
        <span class="label">通知</span>
      </div>
      
      <div class="menu-item">
        <el-icon :size="24"><MoreFilled /></el-icon>
        <span class="label">更多</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Compass, Plus, Bell, MoreFilled } from '@element-plus/icons-vue'
</script>

<style scoped>
.sidebar {
  width: 240px; /* Or narrower like XHS */
  height: 100vh;
  background-color: #fff;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  padding: 0 16px;
  position: fixed;
  left: 0;
  top: 0;
  z-index: 1001;
}

.logo-container {
  height: 72px;
  display: flex;
  align-items: center;
  padding-left: 12px;
  margin-bottom: 20px;
}

.logo-text {
  color: #ff2442;
  font-size: 30px; /* Slightly larger */
  font-family: 'Billabong', cursive, sans-serif; /* Mocking the font style */
  font-weight: 900;
  letter-spacing: -1px;
}

.menu-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.menu-item {
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 12px;
  border-radius: 24px;
  cursor: pointer;
  transition: background-color 0.2s;
  color: #333;
  font-size: 16px;
  font-weight: 600;
}

.menu-item:hover {
  background-color: #f5f5f5;
}

.menu-item.active {
  background-color: #f5f5f5;
  color: #000;
}

.menu-item .el-icon {
  margin-right: 12px;
}

.label {
  line-height: 1;
}
</style>
