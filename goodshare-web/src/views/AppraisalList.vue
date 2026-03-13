<template>
  <div class="appraisal-list-container">
    <div class="main-content">
      <div class="header">
        <h2 class="page-title">鉴别大厅</h2>
        <el-button type="primary" round class="create-btn" @click="$router.push('/appraisals/create')">
           <el-icon><Plus /></el-icon>
           <span>发布鉴别</span>
        </el-button>
      </div>

      <div class="appraisal-list" v-loading="loading">
        <el-empty v-if="!loading && appraisals.length === 0" description="暂无鉴别请求" />
        
        <div v-for="item in appraisals" :key="item.id" class="appraisal-item" @click="$router.push(`/appraisals/${item.id}`)">
          <div class="item-header">
             <div class="user-info">
                <div class="avatar-container">
                    <el-avatar :size="48" :src="item.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
                </div>
                <div class="user-meta">
                    <span class="username">{{ item.user?.nickname || item.user?.username || '未知用户' }}</span>
                    <span class="time">{{ formatDate(item.createdAt) }}</span>
                </div>
             </div>
             
             <div class="vote-info-group">
                 <div class="vote-stats">
                    <div class="vote-pill real">
                        <el-icon><Check /></el-icon>
                        <span class="label">真</span>
                        <span class="count">{{ item.realVotes || 0 }}</span>
                    </div>
                    <div class="vote-pill fake">
                        <el-icon><Close /></el-icon>
                        <span class="label">假</span>
                        <span class="count">{{ item.fakeVotes || 0 }}</span>
                    </div>
                 </div>
                 <div class="vote-progress-wrapper">
                    <div class="progress-track">
                        <div class="progress-bar real-bar" :style="{ width: getRealPercent(item) + '%' }"></div>
                        <div class="progress-bar fake-bar" :style="{ width: getFakePercent(item) + '%' }"></div>
                    </div>
                 </div>
             </div>
          </div>

          <div class="item-content">
             <h3 class="product-name">{{ item.productName }}</h3>
             <p class="description-preview">{{ item.description || '暂无描述' }}</p>
          </div>

          <div class="item-images" v-if="getImages(item).length > 0">
             <div 
                v-for="(img, index) in getImages(item).slice(0, 5)"
                :key="index"
                class="image-wrapper"
             >
                <el-image 
                    :src="img"
                    fit="cover"
                    class="list-image"
                    loading="lazy"
                >
                    <template #placeholder>
                        <div class="image-placeholder"></div>
                    </template>
                </el-image>
                <div v-if="index === 4 && getImages(item).length > 5" class="more-images-overlay">
                    +{{ getImages(item).length - 5 }}
                </div>
             </div>
          </div>
        </div>
      </div>
      
      <div class="pagination">
         <el-pagination
            background
            layout="prev, pager, next"
            :total="total"
            :page-size="pageSize"
            v-model:current-page="currentPage"
            @current-change="loadAppraisals"
         />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'
import { Plus, Check, Close, CaretTop, CaretBottom } from '@element-plus/icons-vue'

const appraisals = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)

const loadAppraisals = async () => {
  loading.value = true
  try {
    const res = await request.get('/appraisals', {
      params: {
        page: currentPage.value,
        size: pageSize.value
      }
    })
    appraisals.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const getImages = (item) => {
    if (!item.images) return []
    try {
        const imgs = JSON.parse(item.images)
        return Array.isArray(imgs) ? imgs : []
    } catch (e) {
        return []
    }
}

const getRealPercent = (item) => {
    const total = (item.realVotes || 0) + (item.fakeVotes || 0)
    if (total === 0) return 50
    return (item.realVotes / total) * 100
}

const getFakePercent = (item) => {
    const total = (item.realVotes || 0) + (item.fakeVotes || 0)
    if (total === 0) return 50
    return (item.fakeVotes / total) * 100
}

const formatDate = (date) => {
    if (!date) return ''
    const now = new Date()
    const d = new Date(date)
    const diff = now - d
    const seconds = Math.floor(diff / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    const days = Math.floor(hours / 24)

    if (days > 0) return `${days}天前`
    if (hours > 0) return `${hours}小时前`
    if (minutes > 0) return `${minutes}分钟前`
    return '刚刚'
}

onMounted(() => {
  loadAppraisals()
})
</script>

<style scoped>
.appraisal-list-container {
  display: flex;
  min-height: 100vh;
  background-color: var(--bg-color);
  justify-content: center;
}

.main-content {
  width: 100%;
  max-width: 1000px;
  padding: 40px 20px;
  padding-left: calc(40px + var(--sidebar-width));
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.page-title {
    font-size: 24px;
    font-weight: 600;
    color: var(--text-color);
    margin: 0;
}

.create-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 10px 20px;
    font-weight: 600;
}

.appraisal-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.appraisal-item {
  background: var(--bg-color-overlay);
  border-radius: 16px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid var(--border-color);
  position: relative;
  overflow: hidden;
}

.appraisal-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
  border-color: var(--el-color-primary-light-5);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-container {
    border: 2px solid var(--bg-color);
    border-radius: 50%;
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.username {
  font-weight: 600;
  font-size: 16px;
  color: var(--text-color);
}

.time {
  font-size: 12px;
  color: var(--text-color-secondary);
}

.vote-info-group {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 8px;
}

.vote-stats {
  display: flex;
  gap: 8px;
  align-items: center;
}

.vote-pill {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 4px 12px;
    border-radius: 20px;
    font-size: 13px;
    font-weight: 600;
    transition: all 0.2s;
}

.vote-pill.real {
    color: #67c23a;
    background-color: rgba(103, 194, 58, 0.1);
}

.vote-pill.fake {
    color: #f56c6c;
    background-color: rgba(245, 108, 108, 0.1);
}

.vote-progress-wrapper {
    width: 140px;
}

.progress-track {
    display: flex;
    height: 6px;
    width: 100%;
    border-radius: 3px;
    overflow: hidden;
    background: var(--border-color);
}

.progress-bar {
    height: 100%;
}

.real-bar {
    background: #67c23a;
    transition: width 0.3s ease;
}

.fake-bar {
    background: #f56c6c;
    transition: width 0.3s ease;
}

.item-content {
  margin-bottom: 16px;
  padding-left: 60px; /* Align with text under avatar */
}

.product-name {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-color);
  line-height: 1.4;
}

.description-preview {
  font-size: 15px;
  color: var(--text-color-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-images {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  padding-left: 60px;
}

.image-wrapper {
  position: relative;
  width: 100%;
  padding-bottom: 100%; /* 1:1 Aspect Ratio */
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.list-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  transition: transform 0.3s;
}

.image-wrapper:hover .list-image {
    transform: scale(1.05);
}

.image-placeholder {
    width: 100%;
    height: 100%;
    background-color: var(--hover-bg);
}

.more-images-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0,0,0,0.5);
    color: white;
    display: flex;
    justify-content: center;
    align-items: center;
    font-size: 18px;
    font-weight: bold;
    backdrop-filter: blur(2px);
}

.pagination {
    margin-top: 40px;
    display: flex;
    justify-content: center;
    padding-bottom: 40px;
}

@media (max-width: 768px) {
    .main-content {
        padding-left: 20px;
    }
    .item-content, .item-images {
        padding-left: 0;
    }
    .item-images {
        grid-template-columns: repeat(3, 1fr);
    }
    .image-wrapper:nth-child(n+4) {
        display: none;
    }
}
</style>
