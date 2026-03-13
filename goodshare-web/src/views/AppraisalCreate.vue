<template>
  <div class="appraisal-create-container">
    <div class="main-content">
        <div class="create-card">
            <div class="card-header">
                <el-button link @click="router.back()">
                    <el-icon :size="20"><ArrowLeft /></el-icon>
                </el-button>
                <h2>发布鉴别请求</h2>
            </div>
            
            <el-form :model="form" label-position="top" class="appraisal-form">
                <el-form-item>
                    <template #label>
                        <div class="form-label">
                            <el-icon><Picture /></el-icon>
                            <span>上传图片 (细节图越多越好)</span>
                        </div>
                    </template>
                    <div class="upload-wrapper">
                        <el-upload
                            v-model:file-list="fileList"
                            action="#"
                            :http-request="customUpload"
                            list-type="picture-card"
                            :on-preview="handlePictureCardPreview"
                            :on-remove="handleRemove"
                            :on-success="handleUploadSuccess"
                            multiple
                            class="custom-uploader"
                        >
                            <el-icon><Plus /></el-icon>
                        </el-upload>
                    </div>
                    <el-dialog v-model="dialogVisible">
                        <img w-full :src="dialogImageUrl" alt="Preview Image" style="width: 100%" />
                    </el-dialog>
                </el-form-item>

                <el-form-item>
                    <template #label>
                        <div class="form-label">
                            <el-icon><Goods /></el-icon>
                            <span>商品名称</span>
                        </div>
                    </template>
                    <el-input 
                        v-model="form.productName" 
                        placeholder="例如：Air Jordan 1 High OG Chicago" 
                        size="large"
                    />
                </el-form-item>

                <el-form-item>
                    <template #label>
                        <div class="form-label">
                            <el-icon><Document /></el-icon>
                            <span>描述说明</span>
                        </div>
                    </template>
                    <el-input 
                        v-model="form.description" 
                        type="textarea" 
                        :rows="6" 
                        placeholder="描述购买渠道、价格等信息，有助于大家判断..." 
                        resize="none"
                    />
                </el-form-item>

                <el-form-item class="form-actions">
                    <el-button type="primary" size="large" @click="submit" :loading="submitting" round class="submit-btn">发布请求</el-button>
                    <el-button size="large" @click="$router.back()" round>取消</el-button>
                </el-form-item>
            </el-form>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import request from '../utils/request'
import authStore from '../stores/auth'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, ArrowLeft, Picture, Goods, Document } from '@element-plus/icons-vue'
import { compressImage } from '../utils/compress'

const router = useRouter()
const fileList = ref([])
const dialogImageUrl = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)

const uploadHeaders = computed(() => ({
    'Authorization': `Bearer ${authStore.state.token}`
}))

const form = ref({
    productName: '',
    description: '',
    images: []
})

const handlePictureCardPreview = (uploadFile) => {
  dialogImageUrl.value = uploadFile.url
  dialogVisible.value = true
}

const handleRemove = (uploadFile, uploadFiles) => {
  // handled by v-model
}

const handleUploadSuccess = (response, uploadFile) => {
    // Ensure the file object in fileList gets the response
    uploadFile.response = response
    uploadFile.url = (typeof response === 'string' ? response : response.url)
}

const customUpload = async (options) => {
    const { file, onSuccess, onError } = options
    
    try {
        ElMessage.info('正在处理图片...')
        const compressedFile = await compressImage(file)
        
        // Update file object size to reflect compression
        if (compressedFile.size !== file.size) {
            try {
                file.size = compressedFile.size
            } catch (e) {}
        }

        const formData = new FormData()
        formData.append('file', compressedFile)
        
        const res = await request.post('/upload', formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        })

        const response = res.data
        
        // Manual sync to fileList
        const uploadFile = fileList.value.find(f => f.uid === file.uid)
        if (uploadFile) {
            uploadFile.response = response
            uploadFile.url = (typeof response === 'string' ? response : response.url)
            uploadFile.status = 'success'
        }
        
        onSuccess(response)
        ElMessage.success('图片上传成功')
    } catch (err) {
        console.error('Upload failed', err)
        onError(err)
        if (err.response && err.response.status === 413) {
            ElMessage.error('图片文件过大，请尝试上传更小的图片')
        } else {
            ElMessage.error('图片上传失败，请重试')
        }
    }
}

const submit = async () => {
    if (!form.value.productName) {
        ElMessage.warning('请输入商品名称')
        return
    }
    if (fileList.value.length === 0) {
        ElMessage.warning('请至少上传一张图片')
        return
    }

    submitting.value = true
    try {
        const imageUrls = fileList.value
            .map(file => {
                if (file.response) {
                    return typeof file.response === 'string' ? file.response : file.response.url
                }
                if (file.raw && file.raw.response) {
                    const rawResp = file.raw.response
                    return typeof rawResp === 'string' ? rawResp : rawResp.url
                }
                return file.url
            })
            .filter(u => u && typeof u === 'string' && !u.startsWith('data:') && !u.startsWith('blob:'))
        
        await request.post('/appraisals', {
            productName: form.value.productName,
            description: form.value.description,
            images: JSON.stringify(imageUrls)
        })
        ElMessage.success('发布成功')
        router.push('/appraisals')
    } catch (error) {
        console.error(error)
    } finally {
        submitting.value = false
    }
}
</script>

<style scoped>
.appraisal-create-container {
  display: flex;
  min-height: 100vh;
  background-color: var(--bg-color);
  justify-content: center;
}

.main-content {
  width: 100%;
  max-width: 1000px; /* Increased for better centering */
  padding: 40px 20px;
  padding-left: calc(40px + var(--sidebar-width)); /* Adjusted padding */
  display: flex;
  justify-content: center;
}

.create-card {
    background: var(--bg-color-overlay);
    padding: 40px 50px;
    border-radius: 16px;
    width: 100%;
    max-width: 800px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.05);
    border: 1px solid var(--border-color);
    margin-bottom: 40px; /* Add bottom margin */
}

.card-header {
    display: flex;
    align-items: center;
    margin-bottom: 30px;
    padding-bottom: 20px;
    border-bottom: 1px solid var(--border-color);
}

.card-header h2 {
    margin: 0;
    margin-left: 10px;
    font-size: 20px;
    font-weight: 600;
    color: var(--text-color);
}

.appraisal-form {
    margin-top: 20px;
}

.form-label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
    color: var(--text-color);
    margin-bottom: 8px;
}

.upload-wrapper {
    background: var(--bg-color);
    padding: 20px;
    border-radius: 8px;
    border: 1px dashed var(--border-color);
}

.custom-uploader :deep(.el-upload--picture-card) {
    background-color: var(--bg-color-overlay);
    border: 1px dashed var(--border-color);
    transition: all 0.3s;
}

.custom-uploader :deep(.el-upload--picture-card:hover) {
    border-color: var(--el-color-primary);
    background-color: var(--hover-bg);
}

.form-actions {
    margin-top: 40px;
    display: flex;
    justify-content: flex-end;
}

.submit-btn {
    min-width: 120px;
}
</style>
