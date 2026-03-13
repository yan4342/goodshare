<template>
  <div class="appraisal-detail-container">
    <div class="main-content" v-loading="loading">
        <div v-if="appraisal" class="detail-card">
            <div class="page-header-wrapper">
                <el-page-header @back="goBack" title="返回">
                    <template #content>
                        <span class="text-large font-600 mr-3"> 鉴定详情 </span>
                    </template>
                </el-page-header>
            </div>

            <div class="header-section">
                <h1>{{ appraisal.productName }}</h1>
                <div class="meta-info">
                    <el-avatar :size="32" :src="appraisal.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" />
                    <span class="username">{{ appraisal.user?.nickname || appraisal.user?.username || '未知用户' }}</span>
                    <span class="time">{{ formatDate(appraisal.createdAt) }}</span>
                </div>
            </div>

            <div class="description">
                {{ appraisal.description || '暂无描述' }}
            </div>

            <div class="images-column">
                <el-image 
                    v-for="(img, index) in parseImages(appraisal.images)" 
                    :key="index"
                    :src="img"
                    :preview-src-list="parseImages(appraisal.images)"
                    fit="contain"
                    class="detail-image"
                />
            </div>

            <div class="voting-section">
                <div class="vote-buttons">
                    <button 
                        class="vote-btn real" 
                        :class="{ active: appraisal.currentUserVote === 1 }"
                        @click="handleVote(1)"
                    >
                        <el-icon class="vote-icon"><Check /></el-icon>
                        <span class="text">真</span>
                        <span class="count">{{ appraisal.realVotes }}</span>
                    </button>
                    
                    <button 
                        class="vote-btn fake" 
                        :class="{ active: appraisal.currentUserVote === 2 }"
                        @click="handleVote(2)"
                    >
                        <el-icon class="vote-icon"><Close /></el-icon>
                        <span class="text">假</span>
                        <span class="count">{{ appraisal.fakeVotes }}</span>
                    </button>
                </div>
                
                <div class="vote-result-bar">
                    <div class="bar real-bar" :style="{ width: realPercent + '%' }"></div>
                    <div class="bar fake-bar" :style="{ width: fakePercent + '%' }"></div>
                </div>
                <div class="vote-labels">
                    <span>{{ realPercent.toFixed(0) }}% 认为是真</span>
                    <span>{{ fakePercent.toFixed(0) }}% 认为是假</span>
                </div>
            </div>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import request from '../utils/request'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Close } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const appraisal = ref(null)
const loading = ref(false)

const realPercent = computed(() => {
    if (!appraisal.value) return 0
    const total = (appraisal.value.realVotes || 0) + (appraisal.value.fakeVotes || 0)
    if (total === 0) return 50
    return (appraisal.value.realVotes / total) * 100
})

const fakePercent = computed(() => {
    if (!appraisal.value) return 0
    const total = (appraisal.value.realVotes || 0) + (appraisal.value.fakeVotes || 0)
    if (total === 0) return 50
    return (appraisal.value.fakeVotes / total) * 100
})

const goBack = () => {
    router.back()
}

const loadDetail = async () => {
    loading.value = true
    try {
        const res = await request.get(`/appraisals/${route.params.id}`)
        appraisal.value = res.data
    } catch (error) {
        console.error(error)
    } finally {
        loading.value = false
    }
}

const parseImages = (jsonStr) => {
    try {
        return JSON.parse(jsonStr) || []
    } catch (e) {
        return []
    }
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

const handleVote = async (type) => {
    try {
        await request.post(`/appraisals/${appraisal.value.id}/vote`, { voteType: type })
        ElMessage.success('投票成功')
        // Refresh to get updated counts
        loadDetail()
    } catch (error) {
        console.error(error)
    }
}

onMounted(() => {
    loadDetail()
})
</script>

<style scoped>
.appraisal-detail-container {
  display: flex;
  min-height: 100vh;
  background-color: var(--bg-color);
}

.main-content {
  flex: 1;
  padding: 20px 40px;
  margin-left: 272px;
  display: flex;
  justify-content: center;
}

.detail-card {
    background: var(--bg-color-overlay);
    padding: 40px;
    border-radius: 8px;
    width: 100%;
    max-width: 800px;
}

.page-header-wrapper {
    margin-bottom: 20px;
    border-bottom: 1px solid #eee;
    padding-bottom: 20px;
}

.header-section {
    margin-bottom: 20px;
}

.meta-info {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #666;
    margin-top: 10px;
}

.description {
    font-size: 16px;
    line-height: 1.6;
    margin-bottom: 30px;
    white-space: pre-wrap;
}

.images-column {
    display: flex;
    flex-direction: column;
    gap: 20px;
    margin-bottom: 40px;
}

.detail-image {
    width: 100%;
    height: auto;
    border-radius: 4px;
    display: block;
}

.voting-section {
    background: var(--hover-bg);
    padding: 30px;
    border-radius: 12px;
    text-align: center;
}

.vote-buttons {
    display: flex;
    justify-content: center;
    gap: 40px;
    margin-bottom: 20px;
}

.vote-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 15px 30px;
    border: 2px solid #eee;
    background: var(--bg-color-overlay);
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.2s;
    min-width: 120px;
}

.vote-btn .vote-icon {
    font-size: 24px;
    margin-bottom: 5px;
}

.vote-btn .text {
    font-weight: bold;
    font-size: 18px;
}

.vote-btn .count {
    color: #999;
    margin-top: 5px;
}

.vote-btn:hover {
    transform: translateY(-2px);
}

.vote-btn.real.active, .vote-btn.real:hover {
    border-color: #67c23a;
    background: var(--hover-bg);
    color: #67c23a;
}

.vote-btn.fake.active, .vote-btn.fake:hover {
    border-color: #f56c6c;
    background: var(--hover-bg);
    color: #f56c6c;
}

.vote-result-bar {
    display: flex;
    height: 10px;
    border-radius: 5px;
    overflow: hidden;
    margin-bottom: 10px;
}

.real-bar {
    background: #67c23a;
}

.fake-bar {
    background: #f56c6c;
}

.vote-labels {
    display: flex;
    justify-content: space-between;
    font-size: 14px;
    color: #666;
}
</style>
