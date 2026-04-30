<template>
  <div class="post-detail-container">
    <transition name="fade">
      <div v-if="visible" class="overlay" @click="handleClose"></div>
    </transition>
    <transition
      @enter="onEnter"
      @leave="onLeave"
      :css="false"
    >
      <div v-if="visible" class="modal-content" ref="modalContent">
        <div class="content-flex">
        <!-- Left: Image Section -->
        <div v-if="imageList.length > 0" class="image-section">
          <el-carousel 
            v-if="imageList.length > 1" 
            ref="carousel"
            trigger="click" 
            height="100%" 
            :autoplay="false" 
            arrow="always"
            :initial-index="0"
          >
            <el-carousel-item v-for="(img, index) in imageList" :key="index">
              <div class="carousel-item-wrapper">
                <img :src="img" class="blurred-bg" />
                <img :src="img" class="post-image" alt="Post image" />
              </div>
            </el-carousel-item>
          </el-carousel>
          <template v-else>
            <img :src="imageList[0]" class="blurred-bg" />
            <img :src="imageList[0]" class="post-image" alt="Post image" />
          </template>
        </div>
        
        <!-- Right: Info -->
        <div class="info-section" :class="{ 'full-width': !post.coverUrl }">
          <!-- Author Header -->
          <div class="author-header">
            <div class="avatar-container" @click="goToUser" :class="getAvatarClass(post.user?.activeStyle)">
                <el-avatar 
                    :size="40" 
                    :src="post.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" 
                    class="clickable-avatar"
                />
            </div>
            <span class="username clickable-username" :class="getNameClass(post.user?.activeStyle)" @click="goToUser">{{ post.user?.username || '用户' }}</span>
            <el-tag v-if="post.user?.level" :type="getLevelTagType(post.user?.level)" effect="dark" size="small" class="level-tag" style="margin-left: 8px; margin-right: 8px;">Lv.{{ post.user?.level }}</el-tag>
            <el-button 
                v-if="(post.userId || post.user?.id) !== authStore.state.user?.id"
                :type="isFollowing ? 'default' : 'primary'"  
                round 
                size="small" 
                class="follow-btn"
                @click="toggleFollow"
                :loading="followLoading"
            >
                {{ isFollowing ? '已关注' : '关注' }}
            </el-button>
          </div>
          
          <!-- Scrollable Content -->
          <div class="scrollable-content">
            <h1 class="post-title">{{ post.title }}</h1>
            <div class="post-text" v-html="post.content"></div>
            
            <div class="tags-list">
              <span v-for="tag in post.tags" :key="tag.id" class="tag" @click="handleTagClick(tag.name)">#{{ tag.name }}</span>
            </div>
            
            <div class="meta-row">
                <div class="date">{{ formatDate(post.createdAt) }}</div>
                <div class="view-count">
                    <el-icon><View /></el-icon> <span>{{ post.viewCount || 0 }}</span>
                </div>
            </div>
            
            <el-divider />
            
            <!-- Comments Section -->
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
              
              <!-- Root Comments -->
              <div v-for="comment in comments" :key="comment.id" class="comment-group">
                <!-- Parent Comment -->
                <div class="comment-item">
                    <div class="avatar-wrapper" :class="getAvatarClass(comment.user?.activeStyle)">
                        <el-avatar :size="32" :src="comment.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" class="comment-avatar" @click="goToUser(comment.userId)"/>
                    </div>
                    <div class="comment-content">
                    <div class="comment-user">
                        <span class="username" :class="getNameClass(comment.user?.activeStyle)" @click="goToUser(comment.userId)">{{ comment.user?.username || '用户' }}</span>
                        <span v-if="comment.user?.id === post.userId" class="author-badge">作者</span>
                    </div>
                    <div class="comment-text" @click="handleReply(comment)">{{ comment.content }}</div>
                    <div class="comment-footer">
                        <span class="comment-date">{{ formatDate(comment.createdAt) }}</span>
                        <div class="comment-actions">
                            <span class="action-item" @click="toggleCommentLike(comment)" :class="{ liked: comment.isLiked }">
                                <el-icon><component :is="comment.isLiked ? 'StarFilled' : 'Star'" /></el-icon>
                                <span v-if="comment.likeCount > 0">{{ comment.likeCount }}</span>
                            </span>
                            <span class="action-item" @click="handleReply(comment)">
                                回复
                            </span>
                        </div>
                    </div>
                    </div>
                </div>

                <!-- Replies (Nested) -->
                <div v-if="comment.replies && comment.replies.length > 0" class="replies-list">
                    <div v-for="reply in comment.replies" :key="reply.id" class="comment-item is-reply">
                        <div class="avatar-wrapper" :class="getAvatarClass(reply.user?.activeStyle)">
                            <el-avatar :size="24" :src="reply.user?.avatarUrl || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" class="comment-avatar" @click="goToUser(reply.userId)"/>
                        </div>
                        <div class="comment-content">
                            <div class="comment-user">
                                <span class="username" :class="getNameClass(reply.user?.activeStyle)" @click="goToUser(reply.userId)">{{ reply.user?.username || '用户' }}</span>
                                <span v-if="reply.user?.id === post.userId" class="author-badge">作者</span>
                                <span v-if="reply.parentId && reply.parentId !== comment.id" class="reply-target">
                                    <span class="reply-text">回复</span> <span class="at-user">@{{ getParentUser(reply.parentId) || '用户' }}</span>
                                </span>
                            </div>
                            <div class="comment-text" @click="handleReply(reply)">{{ reply.content }}</div>
                            <div class="comment-footer">
                                <span class="comment-date">{{ formatDate(reply.createdAt) }}</span>
                                <div class="comment-actions">
                                    <span class="action-item" @click="toggleCommentLike(reply)" :class="{ liked: reply.isLiked }">
                                        <el-icon><component :is="reply.isLiked ? 'StarFilled' : 'Star'" /></el-icon>
                                        <span v-if="reply.likeCount > 0">{{ reply.likeCount }}</span>
                                    </span>
                                    <span class="action-item" @click="handleReply(reply)">
                                        回复
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
              </div>
            </div>
          </div>
          </div>
          
          <!-- Bottom Actions -->
          <div class="bottom-actions">
            <div class="interaction-bar">
                <div class="action-btn" @click="toggleLike" :class="{ active: isLiked }">
                    <el-icon :size="24"><StarFilled v-if="isLiked" /><Star v-else /></el-icon>
                    <span>{{ likeCount > 0 ? likeCount : '点赞' }}</span>
                </div>
                <div class="action-btn" @click="toggleFavorite" :class="{ active: isFavorited }">
                    <el-icon :size="24"><Collection v-if="!isFavorited" /><CollectionTag v-else /></el-icon>
                    <span>{{ isFavorited ? '已收藏' : '收藏' }}</span>
                </div>

            </div>
            <div class="comment-input-wrapper">
                <div v-if="replyTo" class="reply-badge">
                    <span>回复 @{{ replyTo.user?.username }}:</span>
                    <el-icon class="close-reply" @click="cancelReply"><Close /></el-icon>
                </div>
                <div class="comment-input-area">
                     <el-popover
                        placement="top"
                        :width="300"
                        trigger="click"
                     >
                    <template #reference>
                        <el-button circle class="emoji-btn">
                            <span style="font-size: 18px; line-height: 1;">😀</span>
                        </el-button>
                    </template>
                    <div class="emoji-picker">
                        <span 
                            v-for="emoji in emojis" 
                            :key="emoji" 
                            class="emoji-item"
                            @click="addEmoji(emoji)"
                        >{{ emoji }}</span>
                    </div>
                 </el-popover>
                 <el-input 
                    v-model="newComment" 
                    placeholder="说点什么..." 
                    class="comment-input"
                    @keyup.enter="submitComment"
                 >
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
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'
import { Close, Star, StarFilled, Collection, ChatDotRound, CollectionTag, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import authStore from '../stores/auth'
import { getAvatarClass, getNameClass, getLevelTagType } from '../utils/style'
import { parseServerTime } from '../utils/time'

const props = defineProps({
    postId: {
        type: [String, Number],
        default: null
    },
    originRect: {
        type: Object,
        default: null
    },
    focusComment: {
        type: Boolean,
        default: false
    }
})

const emit = defineEmits(['close', 'update'])

const onEnter = (el, done) => {
    if (!props.originRect) {
        done()
        return
    }
    
    const { top, left, width, height } = props.originRect
    const rect = el.getBoundingClientRect()
    
    const scaleX = width / rect.width
    const scaleY = height / rect.height
    
    const originCenterX = left + width / 2
    const originCenterY = top + height / 2
    
    const finalCenterX = rect.left + rect.width / 2
    const finalCenterY = rect.top + rect.height / 2
    
    const deltaX = originCenterX - finalCenterX
    const deltaY = originCenterY - finalCenterY
    
    el.style.transform = `translate(${deltaX}px, ${deltaY}px) scale(${scaleX}, ${scaleY})`
    el.style.transformOrigin = 'center center'
    el.style.opacity = '0'
    
    // Force reflow
    el.offsetHeight
    
    el.style.transition = 'transform 0.3s cubic-bezier(0.2, 0, 0.2, 1), opacity 0.3s ease'
    el.style.transform = 'translate(0, 0) scale(1, 1)'
    el.style.opacity = '1'
    
    const finishTransition = () => {
        done()
        // Trigger resize after transition to ensure correct dimensions
        // Use multiple attempts to be robust against rendering delays
        const triggerResize = () => {
             if (carousel.value) {
                 if (typeof carousel.value.resize === 'function') {
                     carousel.value.resize()
                 } else {
                     window.dispatchEvent(new Event('resize'))
                 }
                 // Force update if needed
                 if (carousel.value.setActiveItem) {
                    carousel.value.setActiveItem(0)
                 }
             }
        }
        
        triggerResize()
        setTimeout(triggerResize, 100)
        setTimeout(triggerResize, 300)
    }
    
    el.addEventListener('transitionend', finishTransition, { once: true })
    // Fallback if transitionend fails
    setTimeout(finishTransition, 350)
}

const onLeave = (el, done) => {
    if (!props.originRect) {
        done()
        return
    }
    
    const { top, left, width, height } = props.originRect
    const rect = el.getBoundingClientRect()
    
    const scaleX = width / rect.width
    const scaleY = height / rect.height
    
    const originCenterX = left + width / 2
    const originCenterY = top + height / 2
    
    const finalCenterX = rect.left + rect.width / 2
    const finalCenterY = rect.top + rect.height / 2
    
    const deltaX = originCenterX - finalCenterX
    const deltaY = originCenterY - finalCenterY
    
    el.style.transition = 'transform 0.3s cubic-bezier(0.4, 0, 1, 1), opacity 0.3s ease'
    el.style.transform = `translate(${deltaX}px, ${deltaY}px) scale(${scaleX}, ${scaleY})`
    el.style.opacity = '0'
    
    el.addEventListener('transitionend', done, { once: true })
}

const route = useRoute()
const router = useRouter()
const visible = ref(false)
const carousel = ref(null)
const post = ref({})
const comments = ref([])
const newComment = ref('')
const submitting = ref(false)
const isLiked = ref(false)
const likeCount = ref(0)
const isFavorited = ref(false)
const isFollowing = ref(false)
const followLoading = ref(false)
const currentSort = ref('hot')
const commentsLoading = ref(false)

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

const addEmoji = (emoji) => {
    newComment.value += emoji
}

const goToUser = async () => {
    try {
        const authorId = post.value.userId || post.value.user?.id
        
        if (!authorId) {
            ElMessage.warning('无法获取用户信息')
            return
        }
        
        if (authStore.state.user?.id === authorId) {
            await router.push('/me')
        } else {
            await router.push(`/user/${authorId}`)
        }
    } catch (e) {
        console.error('Navigation error:', e)
    }
}

const imageList = computed(() => {
    if (post.value.images) {
        try {
            const imgs = JSON.parse(post.value.images)
            if (Array.isArray(imgs) && imgs.length > 0) {
                return imgs
            }
        } catch (e) {
            console.error('Failed to parse images', e)
        }
    }
    if (post.value.coverUrl) {
        return [post.value.coverUrl]
    }
    return []
})

watch(imageList, () => {
    nextTick(() => {
        if (carousel.value) {
            if (typeof carousel.value.resize === 'function') {
                carousel.value.resize()
            } else {
                window.dispatchEvent(new Event('resize'))
            }
        }
    })
})

const handleTagClick = (tagName) => {
    handleClose()
    router.push({ path: '/search', query: { tag: tagName } })
}

const formatDate = (dateStr) => {
    const date = parseServerTime(dateStr)
    if (!date) return ''
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString()
}

const fetchPost = async (id) => {
    try {
        const res = await request.get(`/posts/${id}`)
        post.value = res.data
        emit('update', { id: post.value.id, viewCount: post.value.viewCount })
        const authorId = post.value.userId || post.value.user?.id
        if (authorId) {
            checkFollowStatus(authorId)
        }
    } catch (err) {
        console.error('Failed to load post', err)
        ElMessage.error('加载帖子失败')
    }
}

const fetchComments = async (id) => {
    commentsLoading.value = true
    try {
        const res = await request.get(`/posts/${id}/comments`, {
            params: { sort: currentSort.value }
        })
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
    fetchComments(post.value.id || props.postId || route.params.id)
}

const fetchLikeInfo = async (id) => {
    try {
        const countRes = await request.get(`/posts/${id}/likes/count`)
        likeCount.value = countRes.data
        
        const statusRes = await request.get(`/posts/${id}/likes/status`)
        isLiked.value = statusRes.data
    } catch (err) {
        console.error('Failed to load like info', err)
    }
}

const fetchFavoriteStatus = async (id) => {
    try {
        const res = await request.get(`/favorites/${id}/check`)
        isFavorited.value = res.data
    } catch (err) {
        console.error('Failed to load favorite status', err)
    }
}

const checkFollowStatus = async (authorId) => {
    if (!authStore.state.isAuthenticated || !authorId) return
    if (authStore.state.user?.id === authorId) return // Don't check for self

    try {
        const res = await request.get(`/users/${authorId}/is-following`)
        isFollowing.value = res.data.isFollowing
    } catch (err) {
        console.error('Failed to check follow status', err)
    }
}

const toggleFollow = async () => {
    if (!authStore.state.isAuthenticated) {
        ElMessage.warning('请先登录')
        router.push('/login')
        return
    }
    
    const authorId = post.value.userId || post.value.user?.id
    if (!authorId) {
        console.error('No author ID found for post:', post.value)
        ElMessage.warning('无法获取作者信息')
        return
    }
    
    followLoading.value = true
    try {
        if (isFollowing.value) {
            await request.post(`/users/${authorId}/unfollow`)
            isFollowing.value = false
            ElMessage.success('已取消关注')
        } else {
            await request.post(`/users/${authorId}/follow`)
            isFollowing.value = true
            ElMessage.success('关注成功')
        }
    } catch (err) {
        console.error('Failed to toggle follow', err)
        ElMessage.error('操作失败')
    } finally {
        followLoading.value = false
    }
}

const toggleLike = async () => {
    if (!post.value.id) return
    try {
        if (isLiked.value) {
            await request.delete(`/posts/${post.value.id}/likes`)
            isLiked.value = false
            likeCount.value--
        } else {
            await request.post(`/posts/${post.value.id}/likes`)
            isLiked.value = true
            likeCount.value++
        }
        emit('update', { id: post.value.id, likeCount: likeCount.value })
    } catch (err) {
        console.error('Failed to toggle like', err)
        ElMessage.error('操作失败')
    }
}

const toggleFavorite = async () => {
    if (!post.value.id) return
    try {
        if (isFavorited.value) {
            await request.delete(`/favorites/${post.value.id}`)
            isFavorited.value = false
        } else {
            await request.post(`/favorites/${post.value.id}`)
            isFavorited.value = true
        }
    } catch (err) {
        console.error('Failed to toggle favorite', err)
        ElMessage.error('操作失败')
    }
}

const replyTo = ref(null)

const handleReply = (comment) => {
    replyTo.value = comment
    newComment.value = '' // Optional: keep draft or clear? Clear is safer for context switch.
    // Focus input? We can use a ref for input if needed, but simple binding works for now.
    // Ideally focus the input element.
    const inputEl = document.querySelector('.comment-input input')
    if (inputEl) inputEl.focus()
}

const cancelReply = () => {
    replyTo.value = null
}

const toggleCommentLike = async (comment) => {
    if (!authStore.state.isAuthenticated) {
        ElMessage.warning('请先登录')
        return
    }
    try {
        if (comment.isLiked) {
            await request.delete(`/posts/${props.postId || route.params.id}/comments/${comment.id}/like`)
            comment.isLiked = false
            comment.likeCount = Math.max(0, (comment.likeCount || 0) - 1)
        } else {
            await request.post(`/posts/${props.postId || route.params.id}/comments/${comment.id}/like`)
            comment.isLiked = true
            comment.likeCount = (comment.likeCount || 0) + 1
        }
    } catch (err) {
        console.error('Failed to toggle comment like', err)
        ElMessage.error('操作失败')
    }
}

const getParentUser = (parentId) => {
    if (!parentId) return null
    // Flatten all comments including replies to find user
    for (const c of comments.value) {
        if (c.id === parentId) return c.user?.username
        if (c.replies) {
            const reply = c.replies.find(r => r.id === parentId)
            if (reply) return reply.user?.username
        }
    }
    return null
}

const submitComment = async () => {
    if (!newComment.value.trim()) return
    submitting.value = true
    try {
        await request.post(`/posts/${post.value.id}/comments`, {
            content: newComment.value,
            parentId: replyTo.value?.id
        })
        newComment.value = ''
        replyTo.value = null
        ElMessage.success('评论成功')
        fetchComments(post.value.id) // Refresh comments
    } catch (err) {
        console.error('Failed to submit comment', err)
        ElMessage.error('评论失败')
    } finally {
        submitting.value = false
    }
}

const recordView = async (id) => {
    try {
        await request.post(`/posts/${id}/view`)
    } catch (err) {
        // Ignore view record errors
        console.error('Failed to record view', err)
    }
}

onMounted(() => {
    visible.value = true
    const id = props.postId || route.params.id
    if (id) {
        recordView(id)
        fetchPost(id)
        fetchComments(id)
        fetchLikeInfo(id)
        fetchFavoriteStatus(id)
    }
    
    if (props.focusComment) {
        setTimeout(() => {
            const commentsSection = document.getElementById('comments')
            if (commentsSection) {
                commentsSection.scrollIntoView({ behavior: 'smooth' })
                // Also focus input if possible
                const inputEl = document.querySelector('.comment-input input')
                if (inputEl) inputEl.focus()
            }
        }, 500) // Wait for transition and fetch
    }
})

const handleClose = () => {
    visible.value = false
    setTimeout(() => {
        if (props.postId) {
            emit('close')
        } else {
            router.back()
        }
    }, 300)
}
</script>

<style scoped>
.post-detail-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 2000;
  display: flex;
  justify-content: center;
  align-items: center;
}
.overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: brightness(90%);
  -webkit-backdrop-filter: brightness(90%);
}
.modal-content {
  position: relative;
  width: 1000px;
  height: 90%;
  background: var(--bg-color-overlay);
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  transition: background-color 0.3s;
}
.close-btn {
  position: absolute;
  top: 20px;
  right: 20px; /* Changed from left to right to avoid overlap with avatar */
  z-index: 2001;
  background: rgba(0,0,0,0.1);
  border-radius: 50%;
  width: 40px;
  height: 40px;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  color: var(--text-color); /* Changed from white to adapt to both themes/backgrounds */
  transition: background 0.3s, color 0.3s;
}
.close-btn:hover {
    background: rgba(0,0,0,0.3);
}
.content-flex {
  display: flex;
  width: 100%;
  height: 100%;
}
.image-section {
  flex: 1.5;
  background: #000; /* Fallback background */
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  min-width: 0; /* Fix flex overflow */
}

.blurred-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: blur(20px) brightness(0.7);
  transform: scale(1.1);
  z-index: 0;
}

.image-section :deep(.el-carousel) {
    width: 100%;
    height: 100%;
    z-index: 1;
}

.image-section :deep(.el-carousel__container) {
    height: 100%;
}

.image-section :deep(.el-carousel__item) {
    height: 100%;
}
.carousel-item-wrapper {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100%;
    width: 100%;
    overflow: hidden;
    position: relative;
}
.post-image {
  position: relative;
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  z-index: 1;
}
/* Duplicates removed */
.info-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 24px;
  background: var(--bg-color-overlay);
  max-width: 50%; /* Default if image exists */
  transition: background-color 0.3s;
}
.info-section.full-width {
    flex: 1;
    max-width: 100%;
}
.author-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}
.avatar-container {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
}
.clickable-avatar {
    cursor: pointer;
    transition: opacity 0.3s;
}
.clickable-avatar:hover {
    opacity: 0.8;
}
.username {
  margin-left: 12px;
  font-weight: 600;
  flex: 1;
  color: var(--text-color);
}
.clickable-username {
    cursor: pointer;
}
.clickable-username:hover {
    color: var(--el-color-primary);
}
.scrollable-content {
  flex: 1;
  overflow-y: auto;
  padding-right: 10px;
}
.post-title {
  font-size: 18px;
  margin-bottom: 8px;
  color: var(--text-color);
}
.post-text {
  font-size: 14px;
  color: var(--text-color);
  line-height: 1.6;
  white-space: pre-wrap;
  margin-bottom: 20px;
}
.post-text :deep(a) {
    color: var(--el-color-primary);
    text-decoration: underline;
}
.post-text :deep(img) {
    max-width: 100%;
    border-radius: 8px;
}.tags-list {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag {
    color: var(--el-color-primary);
    cursor: pointer;
}
.meta-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 10px;
    color: var(--text-color-secondary);
    font-size: 12px;
}
.view-count {
    display: flex;
    align-items: center;
    gap: 4px;
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

.interaction-bar {
  display: flex;
  justify-content: space-around;
  margin-bottom: 12px;
}
.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  color: var(--text-color);
}
.action-btn.active {
    color: #ff2442;
}
.action-btn span {
    font-size: 12px;
    margin-top: 2px;
}

.bottom-actions {
  /* Border handled by wrapper/area now */
  border-top: 1px solid var(--border-color);
  padding-top: 12px;
  margin-top: 12px;
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

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.zoom-enter-active,
.zoom-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.zoom-enter-from,
.zoom-leave-to {
  transform: scale(0.9);
  opacity: 0;
}
</style>
