<template>
  <div class="home-container">
    <Sidebar v-if="isAuthenticated" />
    <div class="main-content" :class="{ 'with-sidebar': isAuthenticated }">
      
      <div class="content-body">
        <div class="header-section">
          <h2 class="page-title">全网比价</h2>
          <div class="search-box">
            <el-input
              v-model="searchKeyword"
              placeholder="输入商品名称（如：iPhone 15）"
              class="custom-input"
              size="large"
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
              <template #append>
                <el-button @click="handleSearch">比价</el-button>
              </template>
            </el-input>
            <el-button 
                type="success" 
                class="trend-btn" 
                size="large"
                round
                :disabled="!searchKeyword || !searched"
                @click="showHistory"
            >
                <el-icon><TrendCharts /></el-icon> 价格走势
            </el-button>
            <el-button 
                type="warning" 
                class="refresh-btn" 
                size="large"
                round
                :loading="loading"
                :disabled="!searchKeyword || !searched"
                @click="handleRefresh"
                style="margin-left: 10px;"
            >
                <el-icon><Refresh /></el-icon> 重新爬取
            </el-button>
          </div>
        </div>

        <div v-if="loading" class="loading-state">
          <el-skeleton :rows="5" animated />
        </div>

        <div v-else-if="results.length > 0" class="results-container">
          <div class="platform-legend">
            <span class="legend-item"><span class="dot taobao"></span>淘宝/天猫</span>
            <span class="legend-item"><span class="dot jd"></span>京东</span>
            <span class="legend-item"><span class="dot vip"></span>唯品会</span>
          </div>

          <div class="product-grid">
            <div v-for="(item, index) in results" :key="index" class="product-card" @click="openProduct(item.url)">
              <div class="product-image" :style="{ backgroundImage: `url('${item.imageUrl}')` }">
                <div class="platform-badge" :class="getPlatformClass(item.shopName)">{{ item.platform }}</div>
              </div>
              <div class="product-info">
                <h3 class="product-name" :title="item.title">{{ item.title }}</h3>
                <div class="product-meta">
                  <div class="price">¥{{ item.price }}</div>
                  <div class="shop" :style="{ color: getShopColor(item.shopName) }">{{ item.shopName }}</div>
                </div>
                <el-button type="primary" size="small" class="buy-btn" @click.stop="openProduct(item.url)">
                  去购买
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="searched" class="empty-state">
          <el-empty description="未找到相关商品，换个关键词试试" />
        </div>
        
        <div v-else class="welcome-state">
          <el-empty description="输入商品名称开始全网比价" image="https://cdn-icons-png.flaticon.com/512/2331/2331970.png" />
        </div>
      </div>
    </div>

    <!-- History Dialog -->
    <el-dialog
        v-model="historyDialogVisible"
        title="价格历史走势"
        width="70%"
        @opened="initChart"
    >
        <div ref="chartContainer" style="width: 100%; height: 400px;"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import Sidebar from '../components/Sidebar.vue'
import { ref, computed, nextTick } from 'vue'
import request from '../utils/request'
import { Search, TrendCharts, Refresh } from '@element-plus/icons-vue'
import authStore from '../stores/auth'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const isAuthenticated = computed(() => authStore.state.isAuthenticated)
const searchKeyword = ref('')
const results = ref([])
const loading = ref(false)
const searched = ref(false)

const historyDialogVisible = ref(false)
const chartContainer = ref(null)
let chartInstance = null

const handleSearch = async () => {
  if (!searchKeyword.value.trim()) return
  
  loading.value = true
  searched.value = true
  results.value = []
  
  try {
    const res = await request.get(`/prices/search`, {
      params: { keyword: searchKeyword.value },
      timeout: 120000 // Increase timeout to 120s for crawler
    })
    console.log('Price search results:', res.data)
    if (Array.isArray(res.data)) {
        // Force reactivity update
        results.value = [...res.data]
    } else {
        results.value = []
    }
  } catch (err) {
    console.error('Failed to compare prices', err)
    if (err.code === 'ECONNABORTED') {
        ElMessage.error('搜索耗时较长，请稍后重试或尝试再次搜索')
    } else {
        ElMessage.error('获取比价数据失败，请重试')
    }
  } finally {
    loading.value = false
  }
}

const showHistory = async () => {
    historyDialogVisible.value = true
}

const initChart = async () => {
    if (!chartContainer.value) return

    if (chartInstance) {
        chartInstance.dispose()
    }
    
    chartInstance = echarts.init(chartContainer.value)
    chartInstance.showLoading()
    
    try {
        const res = await request.get('/prices/history', {
            params: { keyword: searchKeyword.value }
        })
        const data = res.data
        
        const dates = data.map(item => item.date)
        const prices = data.map(item => item.minPrice)
        const avgPrices = data.map(item => item.avgPrice)
        
        const option = {
            tooltip: {
                trigger: 'axis'
            },
            legend: {
                data: ['最低价', '平均价']
            },
            grid: {
                left: '3%',
                right: '4%',
                bottom: '3%',
                containLabel: true
            },
            xAxis: {
                type: 'category',
                boundaryGap: false,
                data: dates
            },
            yAxis: {
                type: 'value',
                axisLabel: {
                    formatter: '¥{value}'
                }
            },
            series: [
                {
                    name: '最低价',
                    type: 'line',
                    data: prices,
                    smooth: true,
                    itemStyle: { color: '#67C23A' },
                    areaStyle: {
                        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                          { offset: 0, color: 'rgba(103, 194, 58, 0.5)' },
                          { offset: 1, color: 'rgba(103, 194, 58, 0.1)' }
                        ])
                    }
                },
                {
                    name: '平均价',
                    type: 'line',
                    data: avgPrices,
                    smooth: true,
                    itemStyle: { color: '#409EFF' }
                }
            ]
        }
        
        chartInstance.setOption(option)
    } catch (e) {
        console.error('Failed to load history', e)
    } finally {
        chartInstance.hideLoading()
    }
}

const openProduct = (url) => {
  if (!url || url === 'about:blank') {
    ElMessage.warning('该商品暂无购买链接')
    return
  }
  window.open(url, '_blank')
}

const getShopColor = (name) => {
  if (!name) return '#999'
  if (name.includes('京东')) return '#e1251b'
  if (name.includes('天猫') || name.includes('淘宝')) return '#ff5000'
  if (name.includes('唯品会')) return '#f10180'
  return '#666'
}

const getPlatformClass = (name) => {
  if (!name) return 'other'
  if (name.includes('京东')) return 'jd'
  if (name.includes('天猫') || name.includes('淘宝')) return 'taobao'
  if (name.includes('唯品会')) return 'vip'
  return 'other'
}
</script>

<style scoped>
/* Removed .trend-btn from here as it is now redefined below */
.loading-state {
  text-align: center;
  padding: 40px 0;
}
.loading-content {
  margin-bottom: 30px;
}
.loading-icon {
  font-size: 40px;
  color: var(--primary-color);
  margin-bottom: 16px;
}
.loading-text {
  font-size: 18px;
  color: var(--text-color);
  margin-bottom: 8px;
}
.loading-subtext {
  font-size: 14px;
  color: var(--text-color-secondary);
}

.home-container {
  min-height: 100vh;
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
    max-width: 1200px;
    margin: 0 auto;
    padding: 20px 32px;
}
.header-section {
  text-align: center;
  margin-bottom: 40px;
}
.page-title {
  font-size: 28px;
  margin-bottom: 20px;
  color: var(--text-color);
}
.search-box {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.custom-input {
    flex: 1;
}
.trend-btn {
    margin-left: 0;
    padding: 12px 20px;
    font-weight: bold;
    box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
    transition: all 0.3s;
}
.trend-btn:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(103, 194, 58, 0.4);
}
.custom-input :deep(.el-input__wrapper) {
  border-radius: 24px 0 0 24px;
  padding-left: 20px;
}
.custom-input :deep(.el-input-group__append) {
  border-radius: 0 24px 24px 0;
  background-color: var(--primary-color, #ff2442);
  color: white;
  border: none;
}
.custom-input :deep(.el-input-group__append button) {
  color: white;
}

.platform-legend {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  justify-content: center;
  color: var(--text-color-secondary);
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot.taobao { background-color: #ff5000; }
.dot.jd { background-color: #e1251b; }
.dot.vip { background-color: #f10180; }

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 24px;
}
.product-card {
  background: var(--bg-color);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
  transition: transform 0.2s;
  cursor: pointer;
  border: 1px solid var(--border-color);
}
.product-card:hover {
  transform: translateY(-4px);
}
.product-image {
  height: 240px;
  background-size: cover;
  background-position: center;
  position: relative;
}
.platform-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 8px;
  border-radius: 4px;
  color: white;
  font-size: 12px;
  font-weight: bold;
}
.platform-badge.taobao { background-color: #ff5000; }
.platform-badge.jd { background-color: #e1251b; }
.platform-badge.vip { background-color: #f10180; }

.product-info {
  padding: 16px;
}
.product-name {
  font-size: 16px;
  color: var(--text-color);
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.product-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.price {
  color: #ff2442;
  font-size: 20px;
  font-weight: bold;
.shop {
  font-size: 13px;
  margin-top: 4px;
  font-weight: 500;
} max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.buy-btn {
  width: 100%;
}
</style>
