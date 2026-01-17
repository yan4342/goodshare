<template>
  <div class="me-container">
      <Sidebar />
    <!-- Profile Header -->
    <div class="profile-header">
      <div class="profile-info">
        <div class="avatar-wrapper">
          <el-upload
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
        </div>
        <div class="user-details">
          <h1 class="username">{{ userProfile.nickname || userProfile.username }}</h1>
          <p class="bio">{{ userProfile.bio || '这个人很懒，什么都没有写~' }}</p>
          <div class="stats">
            <div class="stat-item">
              <span class="count">{{ userProfile.followingCount || 0 }}</span>
              <span class="label">关注</span>
            </div>
            <div class="stat-item">
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
            <el-button round @click="showEditProfile = true">编辑资料</el-button>
            <el-button circle><el-icon><Setting /></el-icon></el-button>
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
                <div v-for="post in posts" :key="post.id" class="post-card" :class="{ 'no-image': !getCoverUrl(post) }" @click="$router.push(`/post/${post.id}`)">
                    <div v-if="getCoverUrl(post)" class="card-image" :style="{ backgroundImage: `url(${getCoverUrl(post)})` }">
                        <div class="delete-btn" @click.stop="handleDelete(post)">
                            <el-icon><Delete /></el-icon>
                        </div>
                    </div>
                    <div class="card-content">
                        <h3 class="card-title">{{ post.title }}</h3>
                        <div class="card-footer">
                            <div class="author">
                                <el-avatar :size="20" :src="post.user?.avatarUrl || defaultAvatar" />
                                <span>{{ post.user?.username }}</span>
                            </div>
                            <div class="likes">
                                <el-icon><Star /></el-icon>
                                <span>{{ post.likeCount || 0 }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </el-tab-pane>
        <el-tab-pane label="收藏" name="favorites">
            <div v-if="favorites.length === 0" class="empty-state">
                <el-empty description="还没有收藏过笔记哦" />
            </div>
            <div v-else class="masonry-grid">
                <div v-for="post in favorites" :key="post.id" class="post-card" @click="$router.push(`/post/${post.id}`)">
                    <div class="card-image" :style="{ backgroundImage: `url(${getCoverUrl(post)})` }"></div>
                    <div class="card-content">
                        <h3 class="card-title">{{ post.title }}</h3>
                        <div class="card-footer">
                            <div class="author">
                                <el-avatar :size="20" :src="post.user?.avatarUrl || defaultAvatar" />
                                <span>{{ post.user?.username }}</span>
                            </div>
                            <div class="likes">
                                <el-icon><Star /></el-icon>
                                <span>{{ post.likeCount || 0 }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </el-tab-pane>
        <el-tab-pane label="赞过" name="likes">
            <el-empty description="开发中..." />
        </el-tab-pane>
      </el-tabs>
    </div>

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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import request from '../utils/request'
import authStore from '../stores/auth'
import { Camera, Setting, Star, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Sidebar from "../components/Sidebar.vue";
import { VueCropper } from 'vue-cropper'
import 'vue-cropper/dist/index.css'


const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const userProfile = ref({})
const posts = ref([])
const favorites = ref([])
const activeTab = ref('posts')
const showEditProfile = ref(false)
const totalLikes = ref(0) // Need backend support or calc from posts
const showCropper = ref(false)
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
        const res = await request.get('/profile')
        userProfile.value = res.data
        editForm.username = res.data.username
        editForm.nickname = res.data.nickname
        editForm.bio = res.data.bio
        
        // Fetch posts after profile to use ID
        if (res.data.id) {
            fetchMyPosts(res.data.id)
            fetchMyFavorites()
        }
    } catch (err) {
        console.error('Failed to load profile', err)
        ElMessage.error('加载个人信息失败')
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

const fetchMyFavorites = async () => {
    try {
        const res = await request.get('/favorites')
        favorites.value = res.data
    } catch (err) {
        console.error('Failed to load favorites', err)
    }
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
            
            ElMessage.success('头像更新成功')
            showCropper.value = false
        } catch (err) {
            console.error('Upload failed', err)
            ElMessage.error('头像上传失败')
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

const compressImage = (file) => {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.readAsDataURL(file);
        reader.onload = (e) => {
            const img = new Image();
            img.src = e.target.result;
            img.onload = () => {
                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');

                // Max dimensions for avatar (500x500 is sufficient for display)
                const MAX_WIDTH = 500;
                const MAX_HEIGHT = 500;
                let width = img.width;
                let height = img.height;

                if (width > height) {
                    if (width > MAX_WIDTH) {
                        height *= MAX_WIDTH / width;
                        width = MAX_WIDTH;
                    }
                } else {
                    if (height > MAX_HEIGHT) {
                        width *= MAX_HEIGHT / height;
                        height = MAX_HEIGHT;
                    }
                }

                canvas.width = width;
                canvas.height = height;
                ctx.drawImage(img, 0, 0, width, height);

                // Convert to JPEG for better compression
                canvas.toBlob((blob) => {
                    if (blob) {
                        // Rename to .jpg
                        const newName = file.name.replace(/\.[^/.]+$/, "") + ".jpg";
                        const newFile = new File([blob], newName, {
                            type: 'image/jpeg',
                            lastModified: Date.now(),
                        });
                        resolve(newFile);
                    } else {
                        reject(new Error('Canvas to Blob failed'));
                    }
                }, 'image/jpeg', 0.8);
            };
            img.onerror = (err) => reject(err);
        };
        reader.onerror = (err) => reject(err);
    });
};

const beforeAvatarUpload = async (rawFile) => {
    const isJPG = rawFile.type === 'image/jpeg' || rawFile.type === 'image/jpg';
    const isPNG = rawFile.type === 'image/png';

    if (!isJPG && !isPNG) {
        ElMessage.error('头像必须是 JPG 或 PNG 格式!')
        return false
    }

    // Check if file is larger than 2MB, if so try to compress
    if (rawFile.size / 1024 / 1024 > 2) {
        try {
            ElMessage.info('图片较大，正在自动压缩...')
            const compressedFile = await compressImage(rawFile);
            
            // Check compressed size
            if (compressedFile.size / 1024 / 1024 > 2) {
                 ElMessage.error('图片压缩后仍然超过 2MB，请更换图片');
                 return false;
            }
            return compressedFile;
        } catch (e) {
            console.error('Compression failed', e);
            ElMessage.error('图片处理失败');
            return false;
        }
    }
    
    // Even if < 2MB, we can optionally resize to optimize if it's still somewhat large (e.g. > 500KB)
    // but strict requirement was "fit limits". 
    // Let's stick to the limit logic to be safe.
    
    return true
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
        showEditProfile.value = false
        ElMessage.success('保存成功')
    } catch (err) {
        ElMessage.error('保存失败')
    }
}

const getCoverUrl = (post) => {
    if (post.coverUrl) return post.coverUrl
    if (post.images) {
        try {
            const imgs = JSON.parse(post.images)
            if (Array.isArray(imgs) && imgs.length > 0) return imgs[0]
        } catch (e) {}
    }
    return 'https://placehold.co/300x400?text=No+Image'
}

const handleTabClick = (tab) => {
    if (tab.props.name === 'favorites') {
        fetchMyFavorites()
    } else if (tab.props.name === 'posts' && userProfile.value.id) {
        fetchMyPosts(userProfile.value.id)
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
    column-count: 4;
    column-gap: 20px;
}

.post-card {
    break-inside: avoid;
    background: var(--bg-color-overlay);
    border-radius: 12px;
    overflow: hidden;
    margin-bottom: 20px;
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
    padding: 12px;
}

.card-title {
    font-size: 14px;
    margin: 0 0 8px 0;
    line-height: 1.4;
    display: -webkit-box;
    -webkit-line-clamp: 2;
     line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    color: var(--text-color);
}

.post-card.no-image .card-title {
    -webkit-line-clamp: 6;
    font-size: 15px;
    margin-top: 8px;
}

.card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;
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

@media (max-width: 768px) {
    .masonry-grid {
        column-count: 2;
        column-gap: 10px;
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
</style>