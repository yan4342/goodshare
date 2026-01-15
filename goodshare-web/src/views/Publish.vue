<template>
  <div class="publish-container">
    <div class="publish-card">
      <h2 class="page-title">发布笔记</h2>
      
      <el-form :model="form" label-position="top">
        <!-- Image Upload -->
        <div class="upload-area">
          <el-upload
            class="image-uploader"
            action="/api/upload"
            :show-file-list="false"
            :on-success="handleUploadSuccess"
            :before-upload="beforeUpload"
          >
            <img v-if="form.coverUrl" :src="form.coverUrl" class="uploaded-image" />
            <div v-else class="upload-placeholder">
              <el-icon :size="48"><Plus /></el-icon>
              <span>上传图片</span>
            </div>
          </el-upload>
        </div>

        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="填写标题会有更多赞哦~" maxlength="20" show-word-limit />
        </el-form-item>

        <el-form-item label="正文">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            placeholder="填写正文..."
          />
        </el-form-item>

        <el-form-item label="标签">
           <el-input v-model="tagsInput" placeholder="空格分隔多个标签" @blur="parseTags" />
           <div class="tags-preview">
               <el-tag v-for="tag in form.tags" :key="tag" class="mr-2">{{ tag }}</el-tag>
           </div>
        </el-form-item>

        <el-button type="primary" class="submit-btn" round @click="submitPost" :loading="loading">发布笔记</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const tagsInput = ref('')

const form = ref({
  title: '',
  content: '',
  coverUrl: '',
  tags: []
})

const parseTags = () => {
    if (tagsInput.value.trim()) {
        form.value.tags = tagsInput.value.trim().split(/\s+/)
    }
}

const handleUploadSuccess = (response) => {
  form.value.coverUrl = response.url
}

const beforeUpload = (file) => {
  const isJPG = file.type === 'image/jpeg' || file.type === 'image/png';
  if (!isJPG) {
    ElMessage.error('Avatar picture must be JPG format!')
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    ElMessage.error('Avatar picture size can not exceed 2MB!')
  }
  return isJPG && isLt2M
}

const submitPost = async () => {
  if (!form.value.title || !form.value.coverUrl) {
      ElMessage.warning('请填写标题并上传图片')
      return
  }
  
  loading.value = true
  parseTags()
  
  try {
    // Note: Backend expects PostRequest with tags as Set<String>
    await axios.post('/api/posts', form.value)
    ElMessage.success('发布成功！')
    router.push('/')
  } catch (err) {
    ElMessage.error('发布失败，请重试')
    console.error(err)
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
  background-color: #f9f9f9;
  min-height: 100vh;
}

.publish-card {
  width: 800px;
  background: white;
  padding: 40px;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
}

.page-title {
    margin-bottom: 30px;
    text-align: center;
}

.upload-area {
    display: flex;
    justify-content: center;
    margin-bottom: 30px;
}

.image-uploader {
    border: 1px dashed #d9d9d9;
    border-radius: 8px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    width: 200px;
    height: 266px; /* 3:4 aspect ratio */
    display: flex;
    justify-content: center;
    align-items: center;
}

.image-uploader:hover {
    border-color: #ff2442;
}

.upload-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    color: #8c939d;
}

.uploaded-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.submit-btn {
    width: 100%;
    margin-top: 20px;
    background-color: #ff2442;
    border-color: #ff2442;
}

.tags-preview {
    margin-top: 10px;
    display: flex;
    gap: 10px;
}
</style>
