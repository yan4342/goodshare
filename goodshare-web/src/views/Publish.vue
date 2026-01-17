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

        <el-button type="primary" class="submit-btn" round @click="submitPost" :loading="loading">发布笔记</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
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
let quill = null

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

  form.value.imageUrls = urls
  // Set first image as cover if exists
  if (urls.length > 0) {
      form.value.coverUrl = urls[0]
  } else {
      form.value.coverUrl = ''
  }

  loading.value = true
  try {
    await request.post('/posts', form.value)
    ElMessage.success('发布成功')
    router.push('/')
  } catch (error) {
    // Error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.publish-container {
  display: flex;
  justify-content: center;
  padding: 40px;
  padding-left: 280px; /* 40px padding + 240px sidebar */
  background-color: var(--bg-color);
  min-height: 100vh;
  box-sizing: border-box;
  transition: background-color 0.3s;
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
.submit-btn {
    width: 100%;
    margin-top: 30px;
    height: 44px;
    font-size: 16px;
    background-color: #ff2442;
    border-color: #ff2442;
}
.submit-btn:hover {
    background-color: #e61e3a;
    border-color: #e61e3a;
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
