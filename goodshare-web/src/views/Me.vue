<template>
  <div class="me-container">
    <!-- Profile Header -->
    <div class="profile-header">
      <div v-if="!isCurrentUser" class="header-nav" style="margin-bottom: 20px;">
          <el-button link @click="router.back()">
            <el-icon :size="24"><ArrowLeft /></el-icon>
            <span style="font-size: 16px; margin-left: 4px;">返回</span>
          </el-button>
      </div>
      <div class="profile-info">
        <div class="avatar-wrapper">
          <el-upload
            v-if="isCurrentUser"
            ref="uploadRef"
            class="avatar-uploader"
            action=""
            :show-file-list="false"
            :auto-upload="false"
            :on-change="handleFileChange"
          >
            <el-avatar :size="100" :src="userProfile.avatarUrl || defaultAvatar" class="avatar" />
            <div class="avatar-mask">
              <el-icon><Camera /></el-icon>
            </div>
          </el-upload>
          <el-avatar v-else :size="100" :src="userProfile.avatarUrl || defaultAvatar" class="avatar" />
        </div>
        <div class="user-details">
          <h1 class="username">{{ userProfile.nickname || userProfile.username }}</h1>
          <p class="bio">{{ userProfile.bio || '这个人很懒，什么都没有写~' }}</p>
          <div class="stats">
            <div class="stat-item" @click="showFollowingList = true">
              <span class="count">{{ userProfile.followingCount || 0 }}</span>
              <span class="label">关注</span>
            </div>
            <div class="stat-item" @click="showFollowerList = true">
              <span class="count">{{ userProfile.followerCount || 0 }}</span>
              <span class="label">粉丝</span>
            </div>
            <div class="stat-item">
              <span class="count">{{ totalLikes || 0 }}</span>
              <span class="label">获赞</span>
            </div>
          </div>
        </div>
        <div class="actions">
            <template v-if="isCurrentUser">
                <el-button round @click="showEditProfile = true">编辑资料</el-button>
                <el-button circle @click="openSettings"><el-icon><Setting /></el-icon></el-button>
            </template>
            <template v-else>
                <el-button 
                    :type="isFollowing ? 'default' : 'primary'" 
                    round 
                    @click="toggleFollow"
                >
                    {{ isFollowing ? '已关注' : '关注' }}
                </el-button>
                <el-button circle @click="goToChat"><el-icon><ChatDotRound /></el-icon></el-button>
            </template>
        </div>
      </div>
    </div>

    <!-- Content Tabs -->
    <div class="content-tabs">
      <el-tabs v-model="activeTab" @tab-click="handleTabClick">
        <el-tab-pane label="笔记" name="posts">
            <div v-if="posts.length === 0" class="empty-state">
                <el-empty description="还没有发布过笔记哦" />
            </div>
            <div v-else class="masonry-grid">
                <div v-for="post in posts" :key="post.id" class="post-card" :class="{ 'no-image': !getCoverUrl(post) }" @click="handlePostClick(post, $event)">
                    <div v-if="getCoverUrl(post)" class="card-image" :style="{ backgroundImage: `url('${getCoverUrl(post)}')` }">
                        <div v-if="post.status === 2" class="status-badge rejected">未通过</div>
                        <div v-if="post.status === 0" class="status-badge pending">审核中</div>
                        <div class="delete-btn" @click.stop="handleDelete(post)">
                            <el-icon><Delete /></el-icon>
                        </div>
                    </div>
                    <div class="card-content">
                        <div v-if="!getCoverUrl(post) && post.status === 2" class="status-badge-inline rejected">未通过</div>
                        <div v-if="!getCoverUrl(post) && post.status === 0" class="status-badge-inline pending">审核中</div>
                        <h3 class="card-title">{{ post.title }}</h3>
                        <div class="card-footer">
                            <div class="author">
                                <el-avatar :size="16" :src="userProfile.avatarUrl || defaultAvatar" />
                                <span>{{ userProfile.nickname || userProfile.username }}</span>
                            </div>
                            <div class="stats" style="display: flex; gap: 8px; align-items: center;">
                                <div class="likes" style="display: flex; align-items: center; color: #666; font-size: 12px;">
                                    <el-icon><Star /></el-icon>
                                    <span style="margin-left: 2px;">{{ post.likeCount || 0 }}</span>
                                </div>
                                <div class="comments" style="display: flex; align-items: center; color: #666; font-size: 12px;">
                                    <el-icon><ChatDotRound /></el-icon>
                                    <span style="margin-left: 2px;">{{ post.commentCount || 0 }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </el-tab-pane>

        <el-tab-pane label="鉴定" name="appraisals">
            <div v-if="myAppraisals.length === 0" class="empty-state">
                <el-empty description="还没有发布过鉴定哦" />
            </div>
            <div v-else class="masonry-grid">
                <div v-for="item in myAppraisals" :key="item.id" class="post-card" @click="$router.push(`/appraisals/${item.id}`)">
                    <div v-if="getAppraisalCover(item)" class="card-image" :style="{ backgroundImage: `url('${getAppraisalCover(item)}')` }">
                        <div v-if="isCurrentUser" class="delete-btn" @click.stop="handleDeleteAppraisal(item)">
                            <el-icon><Delete /></el-icon>
                        </div>
                    </div>
                    <div class="card-content">
                        <h3 class="card-title">{{ item.productName }}</h3>
                        <div class="card-footer">
                            <div class="author">
                                <el-avatar :size="16" :src="userProfile.avatarUrl || defaultAvatar" />
                                <span>{{ userProfile.nickname || userProfile.username }}</span>
                            </div>
                            <div class="likes">
                                <span style="font-size: 12px; color: #666;">
                                    <span style="color: #67c23a">真 {{ item.realVotes }}</span> / 
                                    <span style="color: #f56c6c">假 {{ item.fakeVotes }}</span>
                                </span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </el-tab-pane>

        <el-tab-pane label="收藏" name="favorites" v-if="isCurrentUser">
            <div v-if="favorites.length === 0" class="empty-state">
                <el-empty description="还没有收藏过笔记哦" />
            </div>
            <div v-else class="masonry-grid">
                <div v-for="post in favorites" :key="post.id" class="post-card" :class="{ 'no-image': !getCoverUrl(post) }" @click="handlePostClick(post, $event)">
                    <div v-if="getCoverUrl(post)" class="card-image" :style="{ backgroundImage: `url('${getCoverUrl(post)}')` }"></div>
                    <div class="card-content">
                        <h3 class="card-title">{{ post.title }}</h3>
                        <div class="card-footer">
                            <div class="author">
                                <el-avatar :size="16" :src="post.user?.avatarUrl || defaultAvatar" />
                                <span>{{ post.user?.username }}</span>
                            </div>
                            <div class="stats" style="display: flex; gap: 8px; align-items: center;">
                                <div class="likes" style="display: flex; align-items: center; color: #666; font-size: 12px;">
                                    <el-icon><Star /></el-icon>
                                    <span style="margin-left: 2px;">{{ post.likeCount || 0 }}</span>
                                </div>
                                <div class="comments" style="display: flex; align-items: center; color: #666; font-size: 12px;">
                                    <el-icon><ChatDotRound /></el-icon>
                                    <span style="margin-left: 2px;">{{ post.commentCount || 0 }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </el-tab-pane>
        <el-tab-pane label="赞过" name="likes" v-if="isCurrentUser">
            <div v-if="likedPosts.length === 0" class="empty-state">
                <el-empty description="还没有赞过笔记哦" />
            </div>
            <div v-else class="masonry-grid">
                <div v-for="post in likedPosts" :key="post.id" class="post-card" :class="{ 'no-image': !getCoverUrl(post) }" @click="handlePostClick(post, $event)">
                    <div v-if="getCoverUrl(post)" class="card-image" :style="{ backgroundImage: `url('${getCoverUrl(post)}')` }"></div>
                    <div class="card-content">
                        <h3 class="card-title">{{ post.title }}</h3>
                        <div class="card-footer">
                            <div class="author">
                                <el-avatar :size="16" :src="post.user?.avatarUrl || defaultAvatar" />
                                <span>{{ post.user?.username }}</span>
                            </div>
                            <div class="stats" style="display: flex; gap: 8px; align-items: center;">
                                <div class="likes" style="display: flex; align-items: center; color: #666; font-size: 12px;">
                                    <el-icon><Star /></el-icon>
                                    <span style="margin-left: 2px;">{{ post.likeCount || 0 }}</span>
                                </div>
                                <div class="comments" style="display: flex; align-items: center; color: #666; font-size: 12px;">
                                    <el-icon><ChatDotRound /></el-icon>
                                    <span style="margin-left: 2px;">{{ post.commentCount || 0 }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    
    <PostDetail 
      v-if="showPostDetail" 
      :post-id="selectedPostId"
      :origin-rect="clickedRect" 
      @close="closePost"
      @update="handlePostUpdate" 
    />

    <!-- Settings Dialog -->
    <el-dialog v-model="showSettings" title="分区推荐权重设置" width="560px">
        <div class="settings-section">
            <div v-if="loadingWeights" class="loading-state">
               <div style="text-align:center; padding: 20px;">加载中...</div>
            </div>
            <div v-else-if="tagWeights.length === 0" class="empty-state">
              <el-empty description="还没有设置推荐权重哦" />
            </div>
            <div v-else class="weight-list">
              <div class="weight-header">
                <span class="tag-name">分区</span>
                <span class="slider-label">偏好</span>
                <span class="partition-weight">分区权重</span>
                <span class="partition-share">占比</span>
              </div>
              <div v-for="weight in tagWeights" :key="weight.tagId" class="weight-item">
                <span class="tag-name">{{ weight.tagName }}</span>
                <el-slider class="weight-slider" v-model="weight.weight" :min="0.5" :max="2.0" :step="0.1" @change="handleWeightChange(weight)" />
                <span class="partition-weight">{{ getPartitionWeight(weight).toFixed(2) }}</span>
                <span class="partition-share">{{ formatPartitionShare(weight) }}</span>
              </div>
            </div>
        </div>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="showSettings = false">关闭</el-button>
            </span>
        </template>
    </el-dialog>

    <!-- Edit Profile Dialog -->
    <el-dialog v-model="showEditProfile" title="编辑资料" width="500px">
        <el-form :model="editForm" label-width="80px">
            <el-form-item label="昵称">
                <el-input v-model="editForm.nickname" placeholder="请输入昵称" maxlength="50" show-word-limit />
            </el-form-item>
            <el-form-item label="简介">
                <el-input v-model="editForm.bio" type="textarea" :rows="3" maxlength="100" show-word-limit />
            </el-form-item>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="showEditProfile = false">取消</el-button>
                <el-button type="primary" @click="saveProfile">保存</el-button>
            </span>
        </template>
    </el-dialog>

    <!-- Cropper Dialog -->
    <el-dialog v-model="showCropper" title="裁剪头像" width="600px" append-to-body destroy-on-close>
        <div class="cropper-content">
            <vue-cropper ref="cropper" v-bind="cropperOption" />
        </div>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="showCropper = false">取消</el-button>
                <el-button type="primary" @click="confirmCrop">确认</el-button>
            </span>
        </template>
    </el-dialog>

    <!-- Following List Dialog -->
    <el-dialog v-model="showFollowingList" title="关注列表" width="400px">
        <div v-if="followingList.length === 0" class="empty-state">
            <el-empty description="还没有关注任何人" />
        </div>
        <div v-else class="user-list">
            <div v-for="user in followingList" :key="user.id" class="user-list-item" @click="goToUser(user.id)">
                <el-avatar :size="40" :src="user.avatarUrl || defaultAvatar" />
                <span class="list-username">{{ user.nickname || user.username }}</span>
            </div>
        </div>
    </el-dialog>

    <!-- Follower List Dialog -->
    <el-dialog v-model="showFollowerList" title="粉丝列表" width="400px">
        <div v-if="followerList.length === 0" class="empty-state">
            <el-empty description="还没有粉丝" />
        </div>
        <div v-else class="user-list">
            <div v-for="user in followerList" :key="user.id" class="user-list-item" @click="goToUser(user.id)">
                <el-avatar :size="40" :src="user.avatarUrl || defaultAvatar" />
                <span class="list-username">{{ user.nickname || user.username }}</span>
            </div>
        </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../utils/request'
import authStore from '../stores/auth'
import homeStore from '../stores/home'
import { Camera, Setting, Star, Delete, ChatDotRound, Plus, Check, ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VueCropper } from 'vue-cropper'
import 'vue-cropper/dist/index.css'
import { getThumbnailUrl } from '../utils/image'
import { compressImage } from '../utils/compress'
import PostDetail from './PostDetail.vue'

const router = useRouter()
const route = useRoute()
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const userProfile = ref({})
const posts = ref([])
const myAppraisals = ref([])
const favorites = ref([])
const likedPosts = ref([])
const tagWeights = ref([])
const loadingWeights = ref(false)
const activeTab = ref('posts')
const showEditProfile = ref(false)
const showSettings = ref(false)
const totalLikes = ref(0)
const showCropper = ref(false)
const isFollowing = ref(false)
const showFollowingList = ref(false)
const showFollowerList = ref(false)
const followingList = ref([])
const followerList = ref([])

// Post Detail Animation related refs
const showPostDetail = ref(false)
const selectedPostId = ref(null)
const clickedRect = ref(null)

const isCurrentUser = computed(() => {
    return !route.params.id || (authStore.state.user && route.params.id == authStore.state.user.id)
})

const cropperImg = ref('')
const cropper = ref(null)
const uploadRef = ref(null)
const cropperOption = reactive({
    img: '',
    outputSize: 0.8,
    outputType: 'jpeg',
    info: true,
    full: false,
    canMove: true,
    canMoveBox: true,
    fixedBox: false,
    original: false,
    autoCrop: true,
    autoCropWidth: 200,
    autoCropHeight: 200,
    centerBox: false,
    high: true,
    maxImgSize: 2000
})
const tempFile = ref(null)

const editForm = reactive({
    username: '',
    nickname: '',
    bio: ''
})

const uploadHeaders = computed(() => ({
    'Authorization': `Bearer ${authStore.state.token}`
}))

const fetchUserProfile = async () => {
    try {
        const userId = route.params.id
        let res
        
        if (userId && userId != authStore.state.user?.id) {
             res = await request.get(`/users/${userId}`)
             // Check follow status if logged in
             if (authStore.state.isAuthenticated) {
                 checkFollowStatus(userId)
             }
        } else {
            res = await request.get('/profile')
            authStore.setUser(res.data)
             editForm.username = res.data.username
            editForm.nickname = res.data.nickname
            editForm.bio = res.data.bio
        }
        
        userProfile.value = res.data
        
        // Fetch posts after profile to use ID
        if (res.data.id) {
            fetchMyPosts(res.data.id)
            fetchMyAppraisals(res.data.id)
            if (isCurrentUser.value) {
                fetchMyFavorites()
                fetchLikedPosts()
            }
        }
    } catch (err) {
        console.error('Failed to load profile', err)
        ElMessage.error('加载用户信息失败')
    }
}

const checkFollowStatus = async (userId) => {
    try {
        const res = await request.get(`/users/${userId}/is-following`)
        isFollowing.value = res.data.isFollowing
    } catch (error) {
        console.error('Failed to check follow status', error)
    }
}

const toggleFollow = async () => {
    if (!authStore.state.isAuthenticated) {
        router.push('/login')
        return
    }
    
    try {
        if (isFollowing.value) {
            await request.post(`/users/${userProfile.value.id}/unfollow`)
            isFollowing.value = false
            userProfile.value.followerCount = Math.max(0, (userProfile.value.followerCount || 0) - 1)
            ElMessage.success('已取消关注')
        } else {
            await request.post(`/users/${userProfile.value.id}/follow`)
            isFollowing.value = true
            userProfile.value.followerCount = (userProfile.value.followerCount || 0) + 1
            ElMessage.success('关注成功')
        }
    } catch (error) {
        console.error('Follow action failed', error)
        ElMessage.error('操作失败')
    }
}

const goToChat = () => {
    if (!authStore.state.isAuthenticated) {
        router.push('/login')
        return
    }
    router.push({
        path: '/chat',
        query: { userId: userProfile.value.id }
    })
}

watch(() => route.params.id, () => {
    fetchUserProfile()
})

watch(showFollowingList, (val) => {
    if (val) fetchFollowing()
})

watch(showFollowerList, (val) => {
    if (val) fetchFollowers()
})

const fetchFollowing = async () => {
    try {
        const userId = userProfile.value.id
        const res = await request.get(`/users/${userId}/following`)
        followingList.value = res.data
    } catch (e) {
        console.error(e)
    }
}

const fetchFollowers = async () => {
    try {
        const userId = userProfile.value.id
        const res = await request.get(`/users/${userId}/followers`)
        followerList.value = res.data
    } catch (e) {
        console.error(e)
    }
}

const goToUser = (id) => {
    showFollowingList.value = false
    showFollowerList.value = false
    if (authStore.state.user && id == authStore.state.user.id) {
        router.push('/me')
    } else {
        router.push(`/user/${id}`)
    }
}


const fetchMyPosts = async (userId) => {
    try {
        const res = await request.get(`/posts/user/${userId}`)
        posts.value = res.data
    } catch (err) {
        console.error('Failed to load posts', err)
    }
}

const fetchMyAppraisals = async (userId) => {
    try {
        const res = await request.get(`/appraisals/user/${userId}`)
        myAppraisals.value = res.data.records
    } catch (err) {
        console.error('Failed to load appraisals', err)
    }
}

const fetchMyFavorites = async () => {
    try {
        const res = await request.get('/favorites')
        favorites.value = res.data
    } catch (err) {
        console.error('Failed to load favorites', err)
    }
}

const fetchLikedPosts = async () => {
    try {
        const res = await request.get('/likes')
        likedPosts.value = res.data
    } catch (err) {
        console.error('Failed to load liked posts', err)
    }
}

const openSettings = () => {
    showSettings.value = true
    fetchTagWeights()
}

const partitionBase = 2.0
const totalPartitionBoost = computed(() => {
    return tagWeights.value.reduce((sum, item) => {
        const boost = Math.max(0, (item.weight - 1) * partitionBase)
        return sum + boost
    }, 0)
})

const getPartitionWeight = (weight) => {
    return Math.max(0, (weight.weight - 1) * partitionBase)
}

const formatPartitionShare = (weight) => {
    const total = totalPartitionBoost.value
    if (total <= 0) return '0.0%'
    const share = getPartitionWeight(weight) / total
    return `${(share * 100).toFixed(1)}%`
}

const fetchTagWeights = async () => {
    loadingWeights.value = true
    try {
        const res = await request.get('/user/weights')
        tagWeights.value = res.data
    } catch (err) {
        console.error('Failed to load tag weights', err)
        ElMessage.error('加载推荐权重失败')
    } finally {
        loadingWeights.value = false
    }
}

const handleWeightChange = async (weight) => {
    try {
        await request.post('/user/weights', {
            tagId: weight.tagId,
            weight: weight.weight
        })
        ElMessage.success('权重更新成功')
        // Clear home store cache to ensure recommendations are refreshed
        homeStore.reset()
    } catch (error) {
        console.error('Failed to update weight', error)
        ElMessage.error('权重更新失败')
        // Revert? simpler to just refresh or let it be
    }
}

const getAppraisalCover = (item) => {
    if (!item.images) return null
    try {
        const imgs = JSON.parse(item.images)
        return Array.isArray(imgs) && imgs.length > 0 ? imgs[0] : null
    } catch (e) {
        return null
    }
}

const handleDeleteAppraisal = (item) => {
    ElMessageBox.confirm(
        '确定要删除这个鉴定请求吗？',
        '提示',
        {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning',
        }
    ).then(async () => {
        try {
            // Re-use admin endpoint? No, normal users need a delete endpoint
            // Currently AppraisalService.deleteAppraisal is public, but controller?
            // AppraisalController doesn't have delete. I should add it.
            // Wait, for now I can try using the admin one if I am admin, but normal user?
            // I need to add DELETE /api/appraisals/{id} to AppraisalController.
            await request.delete(`/appraisals/${item.id}`)
            ElMessage.success('删除成功')
            fetchMyAppraisals(userProfile.value.id)
        } catch (error) {
            console.error(error)
            ElMessage.error('删除失败')
        }
    })
}

const handleFileChange = (file) => {
    console.log('handleFileChange triggered', file)
    if (!file || !file.raw) {
        console.error('No file raw data')
        return
    }
    const rawFile = file.raw
    console.log('File type:', rawFile.type)
    
    const isJPG = rawFile.type === 'image/jpeg' || rawFile.type === 'image/jpg'
    const isPNG = rawFile.type === 'image/png'

    if (!isJPG && !isPNG) {
        ElMessage.error('头像必须是 JPG 或 PNG 格式!')
        return
    }

    // Read file and open cropper
    const reader = new FileReader()
    reader.onload = (e) => {
        console.log('File read success')
        cropperOption.img = e.target.result
        tempFile.value = rawFile
        showCropper.value = true
        console.log('showCropper set to true')
    }
    reader.onerror = (e) => {
        console.error('File read error', e)
        ElMessage.error('读取文件失败')
    }
    reader.readAsDataURL(rawFile)
}

const confirmCrop = () => {
    console.log('confirmCrop clicked')
    if (!cropper.value) {
        console.error('Cropper instance not found')
        return
    }
    cropper.value.getCropBlob(async (blob) => {
        console.log('Blob obtained', blob)
        
        // Optimistic Update: Show new avatar immediately before upload finishes
        const originalAvatarUrl = userProfile.value.avatarUrl
        const localPreviewUrl = URL.createObjectURL(blob)
        
        // Update local state
        userProfile.value.avatarUrl = localPreviewUrl
        // Update global store (Navbar will update immediately)
        if (authStore.state.user) {
            authStore.setUser({
                ...authStore.state.user,
                avatarUrl: localPreviewUrl
            })
        }

        try {
            // Compress if needed
            let finalBlob = blob;
            if (blob.size / 1024 / 1024 > 2) {
                ElMessage.info('图片较大，正在压缩...')
                // Convert Blob to File for compressImage
                const file = new File([blob], tempFile.value.name, { type: blob.type })
                const compressedFile = await compressImage(file)
                finalBlob = compressedFile
            }
            
            // Upload
            const formData = new FormData()
            // Ensure filename has extension
            let filename = tempFile.value.name
            if (!filename.endsWith('.jpg') && !filename.endsWith('.png')) {
                filename += '.jpg'
            }
            formData.append('file', finalBlob, filename)
            
            const res = await request.post('/upload', formData, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                }
            })
            
            // Handle success
            const url = res.data.url || res.data // Adjust based on actual response structure
            userProfile.value.avatarUrl = url
            
            // Update profile
             await request.put('/profile', {
                avatarUrl: url,
                bio: userProfile.value.bio,
                email: userProfile.value.email
            })
            
            // Update auth store with real URL
            if (authStore.state.user) {
                authStore.setUser({
                    ...authStore.state.user,
                    avatarUrl: url
                })
            }

            ElMessage.success('头像更新成功')
            showCropper.value = false
            
            // Release object URL with a slight delay to allow DOM update
            setTimeout(() => {
                URL.revokeObjectURL(localPreviewUrl)
            }, 1000)
            
        } catch (err) {
            console.error('Upload failed', err)
            if (err.response && err.response.status === 413) {
                ElMessage.error('图片文件过大，请尝试上传更小的图片')
            } else {
                ElMessage.error('头像上传失败')
            }
            
            // Revert on failure
            userProfile.value.avatarUrl = originalAvatarUrl
            if (authStore.state.user) {
                authStore.setUser({
                    ...authStore.state.user,
                    avatarUrl: originalAvatarUrl
                })
            }
        }
    })
}

const handleAvatarSuccess = async (response, uploadFile) => {
    // response might be { url: "..." } or just string
    const url = response.url || response
    userProfile.value.avatarUrl = url
    // Update backend immediately
    try {
        await request.put('/profile', {
            avatarUrl: url,
            bio: userProfile.value.bio,
            email: userProfile.value.email
        })
        ElMessage.success('头像更新成功')
    } catch (err) {
        ElMessage.error('头像保存失败')
    }
}

const saveProfile = async () => {
    try {
        await request.put('/profile', {
            nickname: editForm.nickname,
            bio: editForm.bio,
            avatarUrl: userProfile.value.avatarUrl,
            email: userProfile.value.email
        })
        userProfile.value.bio = editForm.bio
        userProfile.value.nickname = editForm.nickname
        
        // Update auth store
        if (authStore.state.user) {
            authStore.setUser({
                ...authStore.state.user,
                nickname: editForm.nickname,
                bio: editForm.bio
            })
        }

        showEditProfile.value = false
        ElMessage.success('保存成功')
    } catch (err) {
        ElMessage.error('保存失败')
    }
}

const getCoverUrl = (post) => {
    let url = null
    if (post.coverUrl && !post.coverUrl.includes('placehold.co')) {
        url = post.coverUrl
    } else if (post.images) {
        try {
            const imgs = typeof post.images === 'string' ? JSON.parse(post.images) : post.images
            if (Array.isArray(imgs) && imgs.length > 0) url = imgs[0]
        } catch (e) {}
    }
    return getThumbnailUrl(url)
}

const handlePostClick = (post, event) => {
    if (post.status === 2) {
        // Rejected post, go to edit
        router.push(`/publish?id=${post.id}`)
    } else {
        // Normal post, open detail with animation
        if (event && event.currentTarget) {
            clickedRect.value = event.currentTarget.getBoundingClientRect()
        }
        selectedPostId.value = post.id
        showPostDetail.value = true
    }
}

const closePost = () => {
    showPostDetail.value = false
    selectedPostId.value = null
    clickedRect.value = null
}

const handlePostUpdate = (updatedFields) => {
    // Update local lists
    const updateList = (list) => {
        const index = list.findIndex(p => p.id === updatedFields.id)
        if (index !== -1) {
            list[index] = { ...list[index], ...updatedFields }
        }
    }
    
    updateList(posts.value)
    updateList(favorites.value)
    updateList(likedPosts.value)
    
    // Also update home store if needed
    homeStore.updatePost(updatedFields)
}

const handleTabClick = (tab) => {
    if (tab.props.name === 'favorites') {
        fetchMyFavorites()
    } else if (tab.props.name === 'posts' && userProfile.value.id) {
        fetchMyPosts(userProfile.value.id)
    } else if (tab.props.name === 'likes') {
        fetchLikedPosts()
    }
}

const handleDelete = async (post) => {
    try {
        await ElMessageBox.confirm(
            '确定要删除这条笔记吗？此操作无法撤销。',
            '提示',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning',
            }
        )
        
        await request.delete(`/posts/${post.id}`)
        ElMessage.success('删除成功')
        // Remove from list
        posts.value = posts.value.filter(p => p.id !== post.id)
    } catch (err) {
        if (err !== 'cancel') {
            console.error('Delete failed', err)
            ElMessage.error('删除失败')
        }
    }
}

onMounted(() => {
    fetchUserProfile()
})
</script>

<style scoped>
.me-container {
    width: 100%;
    padding: 20px;
    padding-left: calc(20px + var(--sidebar-width));
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    align-items: center;
}

.profile-header, .content-tabs {
    width: 100%;
    max-width: 960px;
}

.profile-header {
    background: var(--bg-color-overlay);
    padding: 30px;
    border-radius: 12px;
    margin-bottom: 20px;
    display: flex;
    justify-content: center;
    transition: background-color 0.3s;
}

.profile-info {
    display: flex;
    align-items: flex-start;
    gap: 40px;
}

.settings-section {
    padding: 20px;
}

.weight-list {
    display: flex;
    flex-direction: column;
    gap: 15px;
    max-width: 600px;
}

.weight-header {
    display: flex;
    align-items: center;
    gap: 20px;
    color: var(--text-color-secondary);
    font-size: 12px;
}

.weight-item {
    display: flex;
    align-items: center;
    gap: 20px;
}

.tag-name {
    width: 80px;
    font-weight: bold;
}

.slider-label {
    flex: 1;
}

.weight-slider {
    flex: 1;
}

.partition-weight {
    width: 70px;
    text-align: right;
    color: var(--text-color-secondary);
}

.partition-share {
    width: 60px;
    text-align: right;
    color: var(--text-color-secondary);
}

.avatar-wrapper {
    position: relative;
    cursor: pointer;
}

.avatar-mask {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    border-radius: 50%;
    background: rgba(0,0,0,0.5);
    display: flex;
    justify-content: center;
    align-items: center;
    opacity: 0;
    transition: opacity 0.3s;
    color: #fff;
    font-size: 24px;
}

.avatar-wrapper:hover .avatar-mask {
    opacity: 1;
}

.user-details {
    flex: 1;
}

.username {
    font-size: 24px;
    font-weight: 600;
    margin: 0 0 10px 0;
    color: var(--text-color);
}

.bio {
    color: var(--text-color-secondary);
    font-size: 14px;
    margin-bottom: 20px;
    white-space: pre-wrap;
}

.stats {
    display: flex;
    gap: 20px;
}

.stat-item {
    text-align: center;
    cursor: pointer;
}

.stat-item .count {
    display: block;
    font-size: 18px;
    font-weight: 600;
    color: var(--text-color);
}

.stat-item .label {
    font-size: 12px;
    color: var(--text-color-secondary);
}

.actions {
    display: flex;
    gap: 10px;
}

.content-tabs {
    background: var(--bg-color-overlay);
    padding: 20px;
    border-radius: 12px;
    min-height: 500px;
    transition: background-color 0.3s;
}

/* Masonry Grid (Reused) */
.masonry-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
}

.post-card {
    background: var(--bg-color-overlay);
    border-radius: 12px;
    overflow: hidden;
    box-shadow: 0 4px 12px rgba(0,0,0,0.05);
    transition: transform 0.2s, background-color 0.3s;
    cursor: pointer;
    border: 1px solid var(--border-color);
}

.post-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 8px 20px rgba(0,0,0,0.1);
}

.card-image {
    width: 100%;
    padding-top: 133%; /* 3:4 aspect ratio */
    background-size: cover;
    background-position: center;
    position: relative;
}

.delete-btn {
    position: absolute;
    top: 8px;
    right: 8px;
    background: rgba(0, 0, 0, 0.5);
    color: white;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
    cursor: pointer;
    opacity: 0;
    transition: opacity 0.3s;
}

.post-card:hover .delete-btn {
    opacity: 1;
}

.delete-btn:hover {
    background: rgba(245, 108, 108, 0.9); /* Red color on hover */
}

.card-content {
    padding: 10px;
}

.card-title {
    font-size: 13px;
    margin: 0 0 6px 0;
    line-height: 1.4;
    display: -webkit-box;
    -webkit-line-clamp: 2;
     line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    color: var(--text-color);
}

.post-card.no-image {
    min-height: 80px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
}

.post-card.no-image .card-title {
    -webkit-line-clamp: 6;
    line-clamp: 6;
    font-size: 13px;
    margin-top: 6px;
    flex: 1;
}

.card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 11px;
    color: var(--text-color-secondary);
}

.author {
    display: flex;
    align-items: center;
    gap: 4px;
}

.likes {
    display: flex;
    align-items: center;
    gap: 2px;
}

@media (max-width: 1024px) {
    .masonry-grid {
        grid-template-columns: repeat(3, 1fr);
    }
}

@media (max-width: 768px) {
    .masonry-grid {
        grid-template-columns: repeat(2, 1fr);
        gap: 10px;
    }
    
    .profile-info {
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: 20px;
    }
    
    .stats {
        justify-content: center;
    }
}

.cropper-content {
    height: 400px;
    width: 100%;
}

.status-badge {
    position: absolute;
    top: 8px;
    left: 8px;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 10px;
    color: white;
    font-weight: bold;
    z-index: 2;
}
.status-badge.rejected {
    background: #f56c6c;
}
.status-badge.pending {
    background: #e6a23c;
}

.status-badge-inline {
    display: inline-block;
    padding: 1px 5px;
    border-radius: 3px;
    font-size: 10px;
    color: white;
    margin-bottom: 4px;
}
.status-badge-inline.rejected {
    background: #f56c6c;
}
.status-badge-inline.pending {
    background: #e6a23c;
}

:deep(.el-tabs__nav-scroll) {
    display: flex;
    justify-content: center;
}

.user-list {
    max-height: 400px;
    overflow-y: auto;
}

.user-list-item {
    display: flex;
    align-items: center;
    padding: 10px;
    cursor: pointer;
    transition: background-color 0.2s;
    border-radius: 8px;
}

.user-list-item:hover {
    background-color: var(--bg-color);
}

.list-username {
    margin-left: 12px;
    font-weight: 500;
    color: var(--text-color);
}
</style>
