<template>
  <div class="notifications-container">
    <Sidebar />
    <div class="notifications-card">
      <h2>消息通知</h2>
      
      <el-tabs v-model="activeTab" class="notification-tabs">
        <el-tab-pane label="收到的赞" name="likes">
            <div v-for="i in 5" :key="i" class="notification-item">
                <el-avatar :size="40" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
                <div class="notification-content">
                    <span class="username">用户{{ i }}</span>
                    <span class="action">赞了你的笔记</span>
                    <span class="time">2小时前</span>
                </div>
                <div class="post-preview"></div>
            </div>
        </el-tab-pane>
        
        <el-tab-pane label="新增关注" name="follows">
            <div class="empty-state">
                <el-empty description="暂无新关注" />
            </div>
        </el-tab-pane>
        
        <el-tab-pane label="评论" name="comments">
             <div class="empty-state">
                <el-empty description="暂无新评论" />
            </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import Sidebar from "../components/Sidebar.vue";

const activeTab = ref('likes')
</script>

<style scoped>
.notifications-container {
    display: flex;
    justify-content: center;
    padding: 40px;
    background-color: #f9f9f9;
    min-height: 100vh;
}

.notifications-card {
    width: 800px;
    background: white;
    padding: 30px;
    border-radius: 16px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.05);
}

.notification-item {
    display: flex;
    align-items: center;
    padding: 15px 0;
    border-bottom: 1px solid #f0f0f0;
}

.notification-content {
    flex: 1;
    margin-left: 15px;
    display: flex;
    flex-direction: column;
}

.username {
    font-weight: 600;
    font-size: 14px;
}

.action {
    color: #666;
    font-size: 14px;
}

.time {
    color: #999;
    font-size: 12px;
    margin-top: 4px;
}

.post-preview {
    width: 48px;
    height: 48px;
    background-color: #eee;
    border-radius: 4px;
}
</style>
