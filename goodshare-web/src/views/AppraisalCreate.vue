<template>
  <div class="appraisal-create-container">
    <Sidebar />
    <div class="main-content">
        <div class="create-card">
            <h2>发布鉴别请求</h2>
            <el-form :model="form" label-position="top">
                <el-form-item label="上传图片 (细节图越多越好)">
                    <el-upload
                            v-model:file-list="fileList"
                            action="/api/upload"
                            :headers="uploadHeaders"
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
import Sidebar from '../components/Sidebar.vue'
import request from '../utils/request'
import authStore from '../stores/auth'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

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
    // Assuming response is the URL string or object with url
    // Adjust based on your actual upload API response
    // If backend returns plain string:
    // uploadFile.url = response 
    // If backend returns object { url: '...' }:
    if (response && response.url) {
        uploadFile.url = response.url
    } else if (typeof response === 'string') {
        uploadFile.url = response
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
        const imageUrls = fileList.value.map(file => file.url || (file.response && file.response.url) || file.response)
        
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
