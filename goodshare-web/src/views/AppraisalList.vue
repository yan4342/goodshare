<template>
  <div class="appraisal-list-container">
    <Sidebar />
    <div class="main-content">
      <div class="header">
        <h2>鉴别大厅</h2>
        <el-button type="primary" @click="$router.push('/appraisals/create')">发布鉴别</el-button>
      </div>

      <div class="appraisal-list" v-loading="loading">
        <el-empty v-if="!loading && appraisals.length === 0" description="暂无鉴别请求" />
        
        <div v-for="item in appraisals" :key="item.id" class="appraisal-item" @click="$router.push(`/appraisals/${item.id}`)">
          <div class="item-header">
             <div class="user-info">
                <el-avatar :size="40" :src="item.user?.avatarUrl || 'https://placehold.co/100'" />
                <div class="user-meta">
                    <span class="username">{{ item.user?.nickname || '未知用户' }}</span>
                    <span class="time">{{ formatDate(item.createdAt) }}</span>
                </div>
             </div>
             <div class="vote-stats">
                <span class="real-tag">👍 真 {{ item.realVotes || 0 }}</span>
                <span class="fake-tag">👎 假 {{ item.fakeVotes || 0 }}</span>
             </div>
             <div class="vote-result-bar">
                <div class="bar real-bar" :style="{ width: getRealPercent(item) + '%' }"></div>
                <div class="bar fake-bar" :style="{ width: getFakePercent(item) + '%' }"></div>
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
                />
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
import Sidebar from '../components/Sidebar.vue'
import request from '../utils/request'

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
}

.main-content {
  flex: 1;
  padding: 20px 40px;
  margin-left: 272px; /* Sidebar width */
  max-width: 1200px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.appraisal-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.appraisal-item {
  background: var(--bg-color-overlay);
  border-radius: 12px;
  padding: 24px;
  cursor: pointer;
  transition: box-shadow 0.2s;
  border: 1px solid var(--border-color);
}

.appraisal-item:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-meta {
  display: flex;
  flex-direction: column;
}

.username {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-color);
}

.time {
  font-size: 12px;
  color: var(--text-color-secondary);
}

.vote-stats {
  display: flex;
  gap: 12px;
  align-items: center;
}

.vote-result-bar {
    display: flex;
    height: 6px;
    width: 100px; /* Fixed width for the bar in the header */
    border-radius: 3px;
    overflow: hidden;
    background: var(--border-color);
}

.real-bar {
    background: #67c23a;
    transition: width 0.3s ease;
}

.fake-bar {
    background: #f56c6c;
    transition: width 0.3s ease;
}

.real-tag, .fake-tag {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.real-tag {
  color: #67c23a;
  background: var(--bg-color);
}

.fake-tag {
  color: #f56c6c;
  background: var(--bg-color);
}

.item-content {
  margin-bottom: 16px;
}

.product-name {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: bold;
  color: var(--text-color);
}

.description-preview {
  font-size: 15px;
  color: var(--text-color-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-images {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.image-wrapper {
  position: relative;
  width: 100%;
  padding-bottom: 100%; /* 1:1 Aspect Ratio */
}

.list-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.pagination {
    margin-top: 30px;
    display: flex;
    justify-content: center;
}
</style>
