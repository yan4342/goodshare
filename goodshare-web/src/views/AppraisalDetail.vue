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
            <!-- 评论区 -->
            <el-divider />
            <div class="comments-section" id="comments">
              <div class="comments-header">
                  <div class="comment-count">共 {{ comments.length }} 条评论</div>
                  <div class="comment-sort">
                      <span :class="{ active: currentSort === 'hot' }" @click="changeSort('hot')">最热</span>
                      <span class="divider">|</span>
                      <span :class="{ active: currentSort === 'desc' }" @click="changeSort('desc')">最新</span>
                      <span class="divider">|</span>
                      <span :class="{ active: currentSort === 'asc' }" @click="changeSort('asc')">最早</span>
                  </div>
              </div>
              <div v-loading="commentsLoading" class="comments-list">
                <div v-if="comments.length === 0" class="no-comments">暂无评论，快来抢沙发吧~</div>
                <div v-for="comment in comments" :key="comment.id" class="comment-group">
                  <!-- 父评论 -->
                  <div class="comment-item">
                      <div class="avatar-wrapper">
                          <el-avatar :size="32" :src="comment.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" class="comment-avatar" />
                      </div>
                      <div class="comment-content">
                        <div class="comment-user">
                            <span class="username">{{ comment.user?.nickname || comment.user?.username || '用户' }}</span>
                        </div>
                        <div class="comment-text" @click="handleReply(comment)">{{ comment.content }}</div>
                        <div class="comment-footer">
                            <span class="comment-date">{{ formatDate(comment.createdAt) }}</span>
                            <div class="comment-actions">
                                <span class="action-item" @click="handleReply(comment)">回复</span>
                            </div>
                        </div>
                      </div>
                  </div>
                  <!-- 二级回复 -->
                  <div v-if="comment.replies && comment.replies.length > 0" class="replies-list">
                    <div v-for="reply in comment.replies" :key="reply.id" class="comment-item is-reply">
                        <div class="avatar-wrapper">
                            <el-avatar :size="24" :src="reply.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" class="comment-avatar" />
                        </div>
                        <div class="comment-content">
                            <div class="comment-user">
                                <span class="username">{{ reply.user?.nickname || reply.user?.username || '用户' }}</span>
                                <span v-if="reply.parentId && reply.parentId !== comment.id" class="reply-target">
                                    <span class="reply-text">回复</span> <span class="at-user">@{{ getParentUser(reply.parentId) || '用户' }}</span>
                                </span>
                            </div>
                            <div class="comment-text" @click="handleReply(reply)">{{ reply.content }}</div>
                            <div class="comment-footer">
                                <span class="comment-date">{{ formatDate(reply.createdAt) }}</span>
                                <div class="comment-actions">
                                    <span class="action-item" @click="handleReply(reply)">回复</span>
                                </div>
                            </div>
                        </div>
                    </div>
                  </div>
                </div>
              </div>
              <!-- 评论输入区 -->
              <div class="comment-input-wrapper">
                <div v-if="replyTo" class="reply-badge">
                    <span>回复 @{{ replyTo.user?.nickname || replyTo.user?.username }}:</span>
                    <el-icon class="close-reply" @click="cancelReply"><Close /></el-icon>
                </div>
                <div class="comment-input-area">
                    <el-popover placement="top" :width="300" trigger="click">
                        <template #reference>
                            <el-button circle class="emoji-btn">
                                <span style="font-size: 18px; line-height: 1;">😀</span>
                            </el-button>
                        </template>
                        <div class="emoji-picker">
                            <span v-for="emoji in emojis" :key="emoji" class="emoji-item" @click="addEmoji(emoji)">{{ emoji }}</span>
                        </div>
                    </el-popover>
                    <el-input v-model="newComment" placeholder="说点什么..." class="comment-input" @keyup.enter="submitComment">
                        <template #append>
                            <el-button @click="submitComment" :loading="submitting">发送</el-button>
                        </template>
                    </el-input>
                </div>
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
import { Close } from '@element-plus/icons-vue'
import { parseServerTime } from '../utils/time'

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
    const d = parseServerTime(date)
    if (!d) return ''
    const now = new Date()
    const diff = now.getTime() - d.getTime()
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

// 评论区相关
const comments = ref([])
const commentsLoading = ref(false)
const currentSort = ref('hot')
const newComment = ref('')
const submitting = ref(false)
const replyTo = ref(null)
const emojis = [
    '😀', '😃', '😄', '😁', '😆', '😅', '🤣', '😂', '🙂', '🙃',
    '😉', '😊', '😇', '🥰', '😍', '🤩', '😘', '😗', '😚', '😙',
    '😋', '😛', '😜', '🤪', '😝', '🤑', '🤗', '🤭', '🤫', '🤔',
    '🤐', '🤨', '😐', '😑', '😶', '😏', '😒', '🙄', '😬', '🤥',
    '😌', '😔', '😪', '🤤', '😴', '😷', '🤒', '🤕', '🤢', '🤮',
    '🤧', '🥵', '🥶', '🥴', '😵', '🤯', '🤠', '🥳', '😎', '🤓',
    '🧐', '😕', '😟', '🙁', '😮', '😯', '😲', '😳', '🥺', '😦',
    '😧', '😨', '😰', '😥', '😢', '😭', '😱', '😖', '😣', '😞',
    '😓', '😩', '😫', '🥱', '😤', '😡', '😠', '🤬', '😈', '👿',
    '💀', '☠️', '💩', '🤡', '👹', '👺', '👻', '👽', '👾', '🤖',
    '😺', '😸', '😹', '😻', '😼', '😽', '🙀', '😿', '😾', '👋',
    '🤚', '🖐', '✋', '🖖', '👌', '🤏', '✌️', '🤞', '🤟', '🤘',
    '🤙', '👈', '👉', '👆', '🖕', '👇', '👍', '👎', '✊', '👊',
    '🤛', '🤜', '👏', '🙌', '👐', '🤲', '🤝', '🙏', '💅', '💪'
]

const fetchComments = async () => {
    commentsLoading.value = true
    try {
        const res = await request.get(`/appraisals/${route.params.id}/comments`, { params: { sort: currentSort.value } })
        comments.value = res.data
    } catch (err) {
        console.error('Failed to load comments', err)
    } finally {
        commentsLoading.value = false
    }
}

const changeSort = (sortType) => {
    if (currentSort.value === sortType) return
    currentSort.value = sortType
    fetchComments()
}

const handleReply = (comment) => {
    replyTo.value = comment
    newComment.value = ''
    const inputEl = document.querySelector('.comment-input input')
    if (inputEl) inputEl.focus()
}

const cancelReply = () => {
    replyTo.value = null
}

const addEmoji = (emoji) => {
    newComment.value += emoji
}

const getParentUser = (parentId) => {
    if (!parentId) return null
    for (const c of comments.value) {
        if (c.id === parentId) return c.user?.nickname || c.user?.username
        if (c.replies) {
            const reply = c.replies.find(r => r.id === parentId)
            if (reply) return reply.user?.nickname || reply.user?.username
        }
    }
    return null
}

const submitComment = async () => {
    if (!newComment.value.trim()) return
    submitting.value = true
    try {
        await request.post(`/appraisals/${route.params.id}/comments`, {
            content: newComment.value,
            parentId: replyTo.value?.id
        })
        newComment.value = ''
        replyTo.value = null
        ElMessage.success('评论成功')
        fetchComments()
    } catch (err) {
        console.error('Failed to submit comment', err)
        ElMessage.error('评论失败')
    } finally {
        submitting.value = false
    }
}

onMounted(() => {
    loadDetail()
    fetchComments()
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


.date {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-color-secondary);
}
.comments-section {
    margin-top: 20px;
}

.comments-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
}

.comment-count {
    font-size: 14px;
    color: var(--text-color-secondary);
}

.comment-sort {
    font-size: 12px;
    color: var(--text-color-secondary);
    display: flex;
    align-items: center;
    gap: 8px;
}

.comment-sort span {
    cursor: pointer;
    transition: color 0.2s;
}

.comment-sort span.active {
    color: var(--text-color);
    font-weight: bold;
}

.comment-sort .divider {
    color: var(--border-color);
    cursor: default;
}

.no-comments {
    text-align: center;
    color: var(--text-color-secondary);
    padding: 20px 0;
    font-size: 13px;
}
.comment-group {
    margin-bottom: 24px;
    border-bottom: 1px solid var(--border-color);
    padding-bottom: 24px;
}
.comment-group:last-child {
    border-bottom: none;
    padding-bottom: 0;
}
.replies-list {
    margin-top: 12px;
    padding-left: 42px; /* Indent replies */
}
.comment-item {
    display: flex;
    gap: 12px;
    align-items: flex-start;
}
.comment-item.is-reply {
    margin-left: 0; /* Reset margin since we use padding in container */
    margin-bottom: 16px;
    background: transparent;
    border: none;
    padding: 0;
}
.comment-item.is-reply:last-child {
    margin-bottom: 0;
}
.avatar-wrapper {
    border-radius: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
}
.comment-avatar {
    flex-shrink: 0;
    cursor: pointer;
}
.comment-content {
    flex: 1;
}
.comment-user {
    font-size: 13px;
    color: var(--text-color-secondary);
    margin-bottom: 4px;
    display: flex;
    align-items: center;
    gap: 6px;
}
.author-badge {
    background: #ff2442;
    color: white;
    font-size: 10px;
    padding: 1px 4px;
    border-radius: 4px;
    transform: scale(0.9);
}
.comment-text {
    font-size: 14px;
    color: var(--text-color);
    line-height: 1.5;
    margin-bottom: 6px;
    cursor: pointer;
}
.comment-footer {
    display: flex;
    align-items: center;
    gap: 16px;
    font-size: 12px;
    color: var(--text-color-secondary);
}
.reply-target {
    margin-left: 0;
    color: var(--text-color-secondary);
}
.reply-text {
    margin-right: 4px;
}
.at-user {
    color: var(--el-color-primary);
    font-weight: 500;
}
.comment-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 4px;
}
.comment-actions {
    display: flex;
    gap: 16px;
    font-size: 12px;
    color: var(--text-color-secondary);
}
.action-item {
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 4px;
    transition: color 0.2s;
}
.action-item:hover {
    color: var(--el-color-primary);
}
.action-item.liked {
    color: #ff2442;
}

.comment-input-wrapper {
    background: var(--bg-color-overlay);
    border-top: 1px solid var(--border-color);
    transition: background-color 0.3s, border-color 0.3s;
}
.reply-badge {
    padding: 8px 20px 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
    color: var(--text-color-secondary);
}
.close-reply {
    cursor: pointer;
    padding: 4px;
}
.close-reply:hover {
    color: var(--text-color);
}
.comment-input-area {
  padding: 16px 20px;
  background: transparent; /* Changed from var(--bg-color-overlay) */
  /* border-top removed */
  display: flex;
  align-items: center;
  gap: 10px;
}

.emoji-btn {
    border: none;
    font-size: 20px;
}

.emoji-picker {
    display: grid;
    grid-template-columns: repeat(8, 1fr);
    gap: 5px;
    max-height: 200px;
    overflow-y: auto;
}

.emoji-item {
    cursor: pointer;
    font-size: 20px;
    text-align: center;
    padding: 5px;
    border-radius: 4px;
    color: var(--text-color);
}

.emoji-item:hover {
    background-color: var(--hover-bg);
}

.comment-input {
  flex: 1;
}
</style>
