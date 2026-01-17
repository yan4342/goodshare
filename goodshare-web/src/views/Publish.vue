<template>
  <div class="publish-container">
    <Sidebar />
    <div class="publish-card">
      <h2 class="page-title">发布笔记</h2>
      
      <el-form :model="form" label-position="top">
        <!-- Image Upload -->
        <div class="upload-area">
          <el-upload
            v-model:file-list="fileList"
            class="image-uploader"
            action="/api/upload"
            :headers="uploadHeaders"
            list-type="picture-card"
            :on-preview="handlePictureCardPreview"
            :on-remove="handleRemove"
            :on-success="handleUploadSuccess"
            :before-upload="beforeUpload"
            multiple
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          
          <el-dialog v-model="dialogVisible">
            <img w-full :src="dialogImageUrl" alt="Preview Image" style="width: 100%" />
          </el-dialog>
        </div>

        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="填写标题会有更多赞哦~" maxlength="20" show-word-limit />
        </el-form-item>

        <el-form-item label="正文">
          <div ref="editorContainer" style="height: 300px;"></div>
        </el-form-item>

        <el-form-item label="标签">
           <el-select
              v-model="form.tags"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="请选择或输入标签"
              style="width: 100%"
           >
              <el-option
                v-for="item in availableTags"
                :key="item.id"
                :label="item.name"
                :value="item.name"
              />
           </el-select>
        </el-form-item>

        <el-form-item label="封面样式 (仅纯文字帖生效)" v-if="fileList.length === 0 && !hasContentImage">
            <div class="cover-styles">
                <div 
                  v-for="(style, index) in coverStyles" 
                  :key="index"
                  class="style-option"
                  :class="{ active: selectedCoverStyle === index }"
                  @click="selectedCoverStyle = index"
                  :style="{ background: `linear-gradient(135deg, ${style.colors[0]}, ${style.colors[1]})` }"
                >
                  <span class="style-name">{{ style.name }}</span>
                  <el-icon v-if="selectedCoverStyle === index" class="check-icon"><Check /></el-icon>
                </div>
            </div>
        </el-form-item>

        <div class="action-buttons">
             <el-button round @click="handlePreview">预览效果</el-button>
             <el-button type="primary" class="submit-btn" round @click="submitPost" :loading="loading">发布笔记</el-button>
        </div>
      </el-form>
    </div>

    <!-- Preview Dialog -->
    <el-dialog v-model="showPreview" title="发布预览" width="900px" align-center class="preview-dialog" :show-close="true">
        <div class="preview-content-flex">
            <!-- Left: Image Section -->
            <div class="preview-image-section">
                <el-carousel v-if="previewData.images && previewData.images.length > 1" trigger="click" height="100%" :autoplay="false" arrow="always">
                    <el-carousel-item v-for="(img, index) in previewData.images" :key="index">
                        <div class="preview-image-wrapper" :style="{ backgroundImage: `url(${img})` }"></div>
                    </el-carousel-item>
                </el-carousel>
                <div v-else-if="previewData.images && previewData.images.length === 1" class="preview-image-wrapper" :style="{ backgroundImage: `url(${previewData.images[0]})` }"></div>
                <div v-else class="preview-image-wrapper empty">
                    <span>无图片</span>
                </div>
            </div>

            <!-- Right: Info Section -->
            <div class="preview-info-section">
                <!-- Author Header -->
                <div class="preview-author-header">
                    <el-avatar :size="40" src="https://placehold.co/100x100?text=Me" />
                    <span class="preview-username">我</span>
                    <el-button type="primary" round size="small" class="preview-follow-btn">关注</el-button>
                </div>

                <!-- Scrollable Content -->
                <div class="preview-scrollable-content">
                    <h1 class="preview-post-title">{{ form.title || '无标题' }}</h1>
                    <div class="preview-post-text ql-editor" v-html="form.content"></div>
                    
                    <div class="preview-tags-list">
                        <span v-for="tag in form.tags" :key="tag" class="preview-tag">#{{ tag }}</span>
                    </div>
                    
                    <div class="preview-date">刚刚</div>
                    
                    <el-divider />
                    
                    <!-- Comments Section (Mock) -->
                    <div class="preview-comments-section">
                        <div class="preview-comment-count">共 0 条评论</div>
                        <div class="preview-no-comments">暂无评论，快来抢沙发吧~</div>
                    </div>
                </div>

                <!-- Bottom Actions -->
                <div class="preview-bottom-actions">
                    <div class="preview-interaction-bar">
                        <div class="preview-action-btn">
                            <el-icon :size="24"><Star /></el-icon>
                            <span>点赞</span>
                        </div>
                        <div class="preview-action-btn">
                            <el-icon :size="24"><Collection /></el-icon>
                            <span>收藏</span>
                        </div>
                        <div class="preview-action-btn">
                            <el-icon :size="24"><ChatDotRound /></el-icon>
                            <span>评论</span>
                        </div>
                    </div>
                    <div class="preview-comment-input-area">
                        <el-input placeholder="说点什么..." class="preview-comment-input">
                            <template #append>
                                <el-button>发送</el-button>
                            </template>
                        </el-input>
                    </div>
                </div>
            </div>
        </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Plus, Check, Star, StarFilled, Collection, CollectionTag, ChatDotRound } from '@element-plus/icons-vue'
import request from '../utils/request'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Sidebar from '../components/Sidebar.vue'

const router = useRouter()
const loading = ref(false)
const availableTags = ref([])
const editorContainer = ref(null)
const fileList = ref([])
const dialogImageUrl = ref('')
const dialogVisible = ref(false)
const showPreview = ref(false)
const hasContentImage = ref(false)
let quill = null

// Cover Styles
const selectedCoverStyle = ref(0)
const coverStyles = [
    { name: '粉嫩甜心', colors: ['#FF9A9E', '#FECFEF'] },
    { name: '梦幻紫罗兰', colors: ['#a18cd1', '#fbc2eb'] },
    { name: '清新薄荷', colors: ['#84fab0', '#8fd3f4'] },
    { name: '暗黑骑士', colors: ['#434343', '#000000'] },
    { name: '日落余晖', colors: ['#fa709a', '#fee140'] },
    { name: '深海幽蓝', colors: ['#30cfd0', '#330867'] }
]

const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { Authorization: 'Bearer ' + token } : {}
})

const form = ref({
  title: '',
  content: '',
  coverUrl: '',
  tags: []
})

const previewData = ref({
    images: []
})

// Check for images in content
watch(() => form.value.content, (newVal) => {
    const imgRegex = /<img[^>]+src="([^">]+)"/g
    hasContentImage.value = imgRegex.test(newVal)
})

onMounted(async () => {
    // Helper to load external scripts
    const loadScript = (src) => {
        return new Promise((resolve, reject) => {
            if (document.querySelector(`script[src^="${src}"]`)) {
                resolve()
                return
            }
            const script = document.createElement('script')
            script.src = src + '?t=' + new Date().getTime() // Cache busting
            script.onload = resolve
            script.onerror = reject
            document.head.appendChild(script)
        })
    }

    // Helper to load styles
    const loadStyle = (href) => {
        if (document.querySelector(`link[href^="${href}"]`)) return
        const link = document.createElement('link')
        link.rel = 'stylesheet'
        link.href = href
        document.head.appendChild(link)
    }

    try {
        // Load Quill resources dynamically
        loadStyle('/quill/quill.snow.css')
        await loadScript('/quill/quill.min.js')

        // Initialize Quill
        if (window.Quill && editorContainer.value) {
            quill = new window.Quill(editorContainer.value, {
                theme: 'snow',
                placeholder: '填写正文...',
                modules: {
                    toolbar: [
                        [{ 'header': [1, 2, false] }],
                        ['bold', 'italic', 'underline', 'strike'],
                        [{ 'list': 'ordered'}, { 'list': 'bullet' }],
                        ['link', 'image']
                    ]
                }
            })
            
            quill.on('text-change', () => {
                form.value.content = quill.root.innerHTML
            })
        } else {
            console.error('Quill init failed: ', { 
                hasQuill: !!window.Quill, 
                hasContainer: !!editorContainer.value 
            })
            ElMessage.error('编辑器初始化失败')
        }
    } catch (error) {
        console.error('Failed to load Quill resources', error)
        ElMessage.error('编辑器资源加载失败')
    }

    try {
        const res = await request.get('/tags')
        availableTags.value = res.data
    } catch (error) {
        console.error('Failed to fetch tags', error)
    }
})

const handleUploadSuccess = (response, uploadFile) => {
  // Element Plus handles fileList update automatically
}

const handleRemove = (uploadFile, uploadFiles) => {
  console.log(uploadFile, uploadFiles)
}

const handlePictureCardPreview = (uploadFile) => {
  dialogImageUrl.value = uploadFile.url || uploadFile.response.url
  dialogVisible.value = true
}

const beforeUpload = (file) => {
  const isJPG = file.type === 'image/jpeg' || file.type === 'image/png';
  if (!isJPG) {
    ElMessage.error('Avatar picture must be JPG format!')
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('Picture size can not exceed 10MB!')
  }
  return isJPG && isLt10M
}

const getFirstContentImage = (html) => {
    const imgRegex = /<img[^>]+src="([^">]+)"/
    const match = html.match(imgRegex)
    return match ? match[1] : null
}

const handlePreview = async () => {
    showPreview.value = true
    // Determine images for preview
    const uploadedUrls = fileList.value.map(file => file.response?.url || file.url).filter(u => u)
    
    if (uploadedUrls.length > 0) {
        previewData.value.images = uploadedUrls
    } else {
        const contentImg = getFirstContentImage(form.value.content)
        if (contentImg) {
             previewData.value.images = [contentImg]
        } else {
             // Generate a temporary cover for preview
             const cover = await generateCoverDataUrl(form.value.title || '无标题')
             previewData.value.images = [cover]
        }
    }
}

const submitPost = async () => {
  if (!form.value.title) {
      ElMessage.warning('请填写标题')
      return
  }
  
  // Extract URLs from fileList
  const urls = fileList.value.map(file => {
      if (file.response && file.response.url) {
          return file.response.url
      }
      return file.url
  }).filter(url => url)

  // Validate that there is either content or images
  const isContentEmpty = !form.value.content || form.value.content === '<p><br></p>' || form.value.content.trim() === '';
  if (isContentEmpty && urls.length === 0) {
      ElMessage.warning('请填写正文或上传图片')
      return
  }

  loading.value = true
  try {
    // 1. Check for uploaded images
    if (urls.length === 0) {
        // 2. Check for content images
        const contentImg = getFirstContentImage(form.value.content)
        if (contentImg) {
            urls.push(contentImg)
        } else {
            // 3. Generate cover
            try {
                const generatedCoverUrl = await generateAndUploadCover(form.value.title || '无标题')
                urls.push(generatedCoverUrl)
            } catch (e) {
                console.error('Cover generation failed', e)
            }
        }
    }

    form.value.imageUrls = urls
    // Set first image as cover if exists
    if (urls.length > 0) {
        form.value.coverUrl = urls[0]
    } else {
        form.value.coverUrl = ''
    }

    await request.post('/posts', form.value)
    ElMessage.success('发布成功')
    router.push('/')
  } catch (error) {
    // Error handled by interceptor
    loading.value = false
  } finally {
    loading.value = false
  }
}

const generateCoverDataUrl = async (text) => {
    const canvas = document.createElement('canvas')
    const width = 600
    const height = 800
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    
    drawCanvasContent(ctx, width, height, text)
    
    return canvas.toDataURL('image/jpeg', 0.8)
}

// Canvas Cover Generation
const generateAndUploadCover = async (text) => {
    const canvas = document.createElement('canvas')
    const width = 600
    const height = 800
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')

    drawCanvasContent(ctx, width, height, text)

    // Convert to Blob and Upload
    return new Promise((resolve, reject) => {
        canvas.toBlob(async (blob) => {
            if (!blob) {
                reject(new Error('Canvas is empty'))
                return
            }
            try {
                const formData = new FormData()
                formData.append('file', blob, `cover_${Date.now()}.jpg`)
                
                const res = await request.post('/upload', formData)
                resolve(res.data.url)
            } catch (err) {
                reject(err)
            }
        }, 'image/jpeg', 0.9)
    })
}

const drawCanvasContent = (ctx, w, h, text) => {
    const style = coverStyles[selectedCoverStyle.value]
    const grd = ctx.createLinearGradient(0, 0, w, h)
    grd.addColorStop(0, style.colors[0])
    grd.addColorStop(1, style.colors[1])
    
    ctx.fillStyle = grd
    ctx.fillRect(0, 0, w, h)
    
    // Add some noise/pattern
    ctx.fillStyle = 'rgba(255,255,255,0.1)'
    for(let i=0; i<50; i++) {
        ctx.beginPath()
        ctx.arc(Math.random()*w, Math.random()*h, Math.random()*50, 0, 2*Math.PI)
        ctx.fill()
    }

    // Draw Text
    ctx.fillStyle = '#FFFFFF'
    ctx.font = 'bold 48px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.shadowColor = 'rgba(0,0,0,0.3)'
    ctx.shadowBlur = 10
    ctx.shadowOffsetX = 2
    ctx.shadowOffsetY = 2

    // Wrap text
    const words = text.split('')
    let line = ''
    const lines = []
    const maxWidth = w - 100
    const lineHeight = 60

    for(let n = 0; n < words.length; n++) {
        const testLine = line + words[n]
        const metrics = ctx.measureText(testLine)
        const testWidth = metrics.width
        if (testWidth > maxWidth && n > 0) {
            lines.push(line)
            line = words[n]
        } else {
            line = testLine
        }
    }
    lines.push(line)

    const totalHeight = lines.length * lineHeight
    let startY = (h - totalHeight) / 2

    for(let i = 0; i < lines.length; i++) {
        ctx.fillText(lines[i], w / 2, startY + (i * lineHeight))
    }
    
    // Add "GoodShare" watermark
    ctx.font = '24px sans-serif'
    ctx.fillText('GoodShare', w / 2, h - 50)
}
</script>

<style scoped>
.publish-container {
  display: flex;
  justify-content: center;
  padding: 40px;
  padding-left: calc(40px + var(--sidebar-width));
  background-color: var(--bg-color);
  min-height: 100vh;
  box-sizing: border-box;
  transition: background-color 0.3s, padding-left 0.3s;
}
.publish-card {
    width: 800px;
    background: var(--bg-color-overlay);
    padding: 40px;
    border-radius: 8px;
    box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
    transition: background-color 0.3s;
}
.page-title {
    margin-bottom: 30px;
    font-size: 24px;
    font-weight: 600;
    color: var(--text-color);
}
.upload-area {
    margin-bottom: 30px;
}
/* Customize Element Plus Upload Picture Card */
.image-uploader :deep(.el-upload--picture-card) {
    width: 148px;
    height: 148px;
    line-height: 148px;
}
.image-uploader :deep(.el-upload-list--picture-card .el-upload-list__item) {
    width: 148px;
    height: 148px;
}
.action-buttons {
    display: flex;
    gap: 16px;
    margin-top: 30px;
}
.submit-btn {
    flex: 1;
    height: 44px;
    font-size: 16px;
    background-color: #ff2442;
    border-color: #ff2442;
}
.submit-btn:hover {
    background-color: #e61f3c;
    border-color: #e61f3c;
}

/* Cover Styles */
.cover-styles {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    margin-top: 8px;
}
.style-option {
    width: 100px;
    height: 60px;
    border-radius: 8px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    border: 2px solid transparent;
    transition: all 0.2s;
}
.style-option.active {
    border-color: #ff2442;
    transform: scale(1.05);
}
.style-name {
    color: white;
    font-size: 12px;
    font-weight: bold;
    text-shadow: 0 1px 2px rgba(0,0,0,0.3);
}
.check-icon {
    position: absolute;
    top: -8px;
    right: -8px;
    background: #ff2442;
    color: white;
    border-radius: 50%;
    padding: 2px;
    width: 16px;
    height: 16px;
}

/* Preview Dialog */
:deep(.preview-dialog .el-dialog__body) {
    padding: 0;
    margin: 0;
    height: 70vh;
    overflow: hidden;
}

.preview-content-flex {
    display: flex;
    width: 100%;
    height: 100%;
}

.preview-image-section {
    flex: 1.5;
    background: #000;
    position: relative;
    overflow: hidden;
}

.preview-image-wrapper {
    width: 100%;
    height: 100%;
    background-size: contain;
    background-repeat: no-repeat;
    background-position: center;
}

.preview-image-wrapper.empty {
    display: flex;
    justify-content: center;
    align-items: center;
    color: #999;
}

/* Ensure carousel fills the section */
.preview-image-section :deep(.el-carousel) {
    width: 100%;
    height: 100%;
}

.preview-info-section {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 24px;
    background: var(--bg-color-overlay);
    overflow: hidden;
}

.preview-author-header {
    display: flex;
    align-items: center;
    margin-bottom: 20px;
    flex-shrink: 0;
}

.preview-username {
    margin-left: 12px;
    font-weight: 600;
    flex: 1;
    color: var(--text-color);
}

.preview-scrollable-content {
    flex: 1;
    overflow-y: auto;
    padding-right: 10px;
}

.preview-post-title {
    font-size: 18px;
    margin-bottom: 8px;
    color: var(--text-color);
    line-height: 1.4;
}

.preview-post-text {
    font-size: 14px;
    color: var(--text-color);
    line-height: 1.6;
    margin-bottom: 20px;
    min-height: auto;
}

.preview-post-text :deep(img) {
    max-width: 100%;
    border-radius: 8px;
    margin: 8px 0;
}

.preview-tags-list {
    margin-top: 10px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.preview-tag {
    color: var(--el-color-primary);
    font-size: 14px;
}

.preview-date {
    margin-top: 10px;
    font-size: 12px;
    color: var(--text-color-secondary);
}

.preview-comments-section {
    margin-top: 20px;
}

.preview-comment-count {
    font-size: 14px;
    color: var(--text-color-secondary);
    margin-bottom: 12px;
}

.preview-no-comments {
    text-align: center;
    color: var(--text-color-secondary);
    padding: 20px 0;
    font-size: 13px;
}

.preview-bottom-actions {
    border-top: 1px solid var(--border-color);
    padding-top: 12px;
    margin-top: 12px;
    flex-shrink: 0;
}

.preview-interaction-bar {
    display: flex;
    justify-content: space-around;
    margin-bottom: 12px;
}

.preview-action-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    color: var(--text-color);
    opacity: 0.8;
}

.preview-action-btn span {
    font-size: 12px;
    margin-top: 2px;
}

.preview-comment-input-area {
    display: flex;
    align-items: center;
}

.preview-comment-input {
    width: 100%;
}

/* Quill Customization */
:deep(.ql-toolbar) {
    border-top-left-radius: 4px;
    border-top-right-radius: 4px;
    border-color: var(--border-color);
    width: 100%;
    box-sizing: border-box;
    background-color: var(--bg-color-overlay);
}
:deep(.ql-container) {
    border-bottom-left-radius: 4px;
    border-bottom-right-radius: 4px;
    border-color: var(--border-color);
    font-size: 16px;
    width: 100%;
    box-sizing: border-box;
    min-height: 200px; /* Ensure content area has height */
    background-color: var(--bg-color-overlay);
    color: var(--text-color);
}
:deep(.ql-editor) {
    min-height: 200px;
}
:deep(.ql-snow .ql-stroke) {
    stroke: var(--text-color);
}
:deep(.ql-snow .ql-fill) {
    fill: var(--text-color);
}
:deep(.ql-snow .ql-picker) {
    color: var(--text-color);
}
:deep(.ql-editor.ql-blank::before) {
    color: var(--text-color-secondary) !important;
    font-style: normal;
}
:deep(.ql-snow .ql-picker-options) {
    background-color: var(--bg-color-overlay) !important;
    border-color: var(--border-color) !important;
}
:deep(.ql-snow .ql-picker-item) {
    color: var(--text-color);
}
:deep(.ql-snow .ql-picker-label::before) {
    color: var(--text-color);
}
:deep(.ql-snow .ql-tooltip) {
    background-color: var(--bg-color-overlay) !important;
    border-color: var(--border-color) !important;
    color: var(--text-color) !important;
    box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1);
}
:deep(.ql-snow .ql-tooltip input[type=text]) {
    background-color: var(--bg-color) !important;
    border-color: var(--border-color) !important;
    color: var(--text-color) !important;
}
</style>
