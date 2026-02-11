<template>
  <div class="appraisal-create-container">
    <div class="main-content">
        <div class="create-card">
            <h2>发布鉴别请求</h2>
            <el-form :model="form" label-position="top">
                <el-form-item label="上传图片 (细节图越多越好)">
                    <el-upload
                            v-model:file-list="fileList"
                            action="#"
                            :http-request="customUpload"
                            list-type="picture-card"
                            :on-preview="handlePictureCardPreview"
                            :on-remove="handleRemove"
                            :on-success="handleUploadSuccess"
                            multiple
                        >
                        <el-icon><Plus /></el-icon>
                    </el-upload>
                    <el-dialog v-model="dialogVisible">
                        <img w-full :src="dialogImageUrl" alt="Preview Image" style="width: 100%" />
                    </el-dialog>
                </el-form-item>

                <el-form-item label="商品名称">
                    <el-input v-model="form.productName" placeholder="例如：Air Jordan 1 High OG Chicago" />
                </el-form-item>

                <el-form-item label="描述说明">
                    <el-input 
                        v-model="form.description" 
                        type="textarea" 
                        :rows="4" 
                        placeholder="描述购买渠道、价格等信息，有助于大家判断..." 
                    />
                </el-form-item>

                <el-form-item>
                    <el-button type="primary" @click="submit" :loading="submitting">发布</el-button>
                    <el-button @click="$router.back()">取消</el-button>
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
import { Plus } from '@element-plus/icons-vue'
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
}

.main-content {
  flex: 1;
  padding: 20px 40px;
  margin-left: 272px;
  display: flex;
  justify-content: center;
}

.create-card {
    background: var(--bg-color-overlay);
    padding: 40px;
    border-radius: 8px;
    width: 100%;
    max-width: 800px;
}
</style>
