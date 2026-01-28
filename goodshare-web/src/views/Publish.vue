<template>
  <div class="publish-container">
    <Sidebar />
    <div class="publish-layout">
      <!-- Left: Form -->
      <div class="publish-card form-section">
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

          <el-form-item label="AI 创作助手">
            <div class="ai-input-group">
                <el-input 
                    v-model="aiKeyword" 
                    placeholder="输入商品名或主题，AI帮您写笔记（例如：iPhone 16 使用体验）" 
                    maxlength="50" 
                    clearable
                    @keyup.enter="handleAiGenerate"
                >
                    <template #append>
                        <el-button @click="handleAiGenerate" :loading="aiLoading">
                            <el-icon class="ai-icon"><MagicStick /></el-icon>
                            一键生成
                        </el-button>
                    </template>
                </el-input>
            </div>
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
              <div class="style-group">
                  <div class="style-label">背景风格</div>
                  <div class="cover-styles">
                      <div 
                        v-for="(style, index) in coverStyles" 
                        :key="index"
                        class="style-option"
                        :class="{ active: selectedCoverStyle === index }"
                        @click="selectedCoverStyle = index"
                        :style="{ background: style.type === 'solid' ? style.colors[0] : `linear-gradient(135deg, ${style.colors[0]}, ${style.colors[1]})` }"
                      >
                        <span class="style-name" :style="{ color: style.type === 'solid' && style.colors[0] === '#ffffff' ? '#333' : 'white' }">{{ style.name }}</span>
                        <el-icon v-if="selectedCoverStyle === index" class="check-icon"><Check /></el-icon>
                      </div>
                  </div>
              </div>

              <div class="style-group" style="margin-top: 20px;">
                  <div class="style-label">文字样式</div>
                  <div class="cover-styles">
                      <div 
                        v-for="(style, index) in textStyles" 
                        :key="index"
                        class="style-option"
                        :class="{ active: selectedTextStyle === index }"
                        @click="selectedTextStyle = index"
                        :style="{ background: '#f5f7fa' }"
                      >
                        <span class="style-name" :style="{ 
                            color: style.color, 
                            fontFamily: style.font, 
                            fontWeight: style.weight,
                            textShadow: style.shadow ? `1px 1px 2px ${style.shadowColor || 'rgba(0,0,0,0.5)'}` : 'none',
                            fontSize: '16px'
                        }">Abc</span>
                        <el-icon v-if="selectedTextStyle === index" class="check-icon"><Check /></el-icon>
                      </div>
                  </div>
              </div>
          </el-form-item>

          <div class="action-buttons">
               <el-button type="primary" class="submit-btn" round @click="prePublish" :loading="loading">发布笔记</el-button>
          </div>
        </el-form>
      </div>

      <!-- Live Preview Panel -->
      <div class="publish-preview-panel">
          <div class="post-detail-preview">
             <div class="content-flex">
                 <!-- Left: Image Section -->
                 <div v-if="fileList.length > 0 || previewCoverUrl" class="image-section">
                     <el-carousel v-if="fileList.length > 0" height="100%" arrow="hover" :autoplay="false" indicator-position="none">
                         <el-carousel-item v-for="(file, index) in fileList" :key="index">
                             <div class="image-wrapper" :style="{ backgroundImage: `url('${file.url || (file.response && file.response.url)}')` }"></div>
                         </el-carousel-item>
                     </el-carousel>
                     <div v-else class="image-wrapper" :style="{ backgroundImage: `url('${previewCoverUrl}')` }"></div>
                 </div>

                 <!-- Right: Info -->
                 <div class="info-section" :class="{ 'full-width': fileList.length === 0 && !previewCoverUrl }">
                     <!-- Author Header -->
                     <div class="author-header">
                         <el-avatar :size="40" src="https://placehold.co/100x100?text=Me" />
                         <span class="username">我</span>
                         <el-button type="primary" round size="small" class="follow-btn">关注</el-button>
                     </div>

                     <!-- Scrollable Content -->
                     <div class="scrollable-content">
                         <h1 class="post-title">{{ form.title || '标题' }}</h1>
                         <div class="post-text ql-editor" v-html="form.content"></div>
                         
                         <div class="tags-list">
                             <span v-for="tag in form.tags" :key="tag" class="tag">#{{ tag }}</span>
                         </div>
                         
                         <div class="date">刚刚</div>
                         
                         <div class="comments-section">
                             <div class="comment-count">共 0 条评论</div>
                             <div class="no-comments">暂无评论，快来抢沙发吧~</div>
                         </div>
                     </div>

                     <!-- Bottom Actions -->
                     <div class="bottom-actions">
                         <div class="interaction-bar">
                             <div class="action-btn">
                                 <el-icon :size="24"><Star /></el-icon>
                                 <span>点赞</span>
                             </div>
                             <div class="action-btn">
                                 <el-icon :size="24"><Collection /></el-icon>
                                 <span>收藏</span>
                             </div>
                             <!-- <div class="action-btn">
                                 <el-icon :size="24"><ChatDotRound /></el-icon>
                                 <span>评论</span>
                             </div> -->
                         </div>
                         <div class="comment-input-area">
                             <el-input placeholder="说点什么..." class="comment-input">
                                 <template #append>
                                     <el-button>发送</el-button>
                                 </template>
                             </el-input>
                         </div>
                     </div>
                 </div>
             </div>
          </div>
          <div class="preview-tip">实时详情预览</div>
      </div>
    </div>

    <!-- Preview Dialog -->
    <el-dialog v-model="showPreview" title="发布预览" width="900px" align-center class="preview-dialog" :show-close="true">
        <div class="preview-content-flex">
            <!-- Left: Image Section -->
            <div class="preview-image-section">
                <el-carousel v-if="previewData.images && previewData.images.length > 1" trigger="click" height="100%" :autoplay="false" arrow="always">
                    <el-carousel-item v-for="(img, index) in previewData.images" :key="index">
                        <div class="preview-image-wrapper" :style="{ backgroundImage: `url('${img}')` }"></div>
                    </el-carousel-item>
                </el-carousel>
                <div v-else-if="previewData.images && previewData.images.length === 1" class="preview-image-wrapper" :style="{ backgroundImage: `url('${previewData.images[0]}')` }"></div>
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
                        <!-- <div class="preview-action-btn">
                            <el-icon :size="24"><ChatDotRound /></el-icon>
                            <span>评论</span>
                        </div> -->
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
        <template #footer>
            <div class="dialog-footer">
                <el-button @click="showPreview = false">返回修改</el-button>
                <el-button type="primary" @click="confirmPublish" :loading="loading">确认发布</el-button>
            </div>
        </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Plus, Check, Star, StarFilled, Collection, CollectionTag, ChatDotRound, MagicStick } from '@element-plus/icons-vue'
import request from '../utils/request'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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
const previewCoverUrl = ref('')
const aiKeyword = ref('')
const aiLoading = ref(false)
let quill = null

const form = ref({
  title: '',
  content: '',
  coverUrl: '',
  tags: []
})

const previewData = ref({
    images: []
})

const handleAiGenerate = async () => {
    if (!aiKeyword.value.trim()) {
        ElMessage.warning('请输入商品名或主题')
        return
    }

    aiLoading.value = true
    try {
        // Use Fetch API for streaming response
        const token = localStorage.getItem('token')
        const response = await fetch('/api/ai/generate-stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            },
            body: JSON.stringify({ keyword: aiKeyword.value })
        })

        if (!response.ok) {
            throw new Error('AI request failed')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let accumulatedContent = ''
        let buffer = ''
        
        // Clear current content if needed, or just append? 
        // Let's start fresh for the generated part
        if (quill) {
             quill.root.innerHTML = '<p>AI 正在思考...</p>'
        }

        let isDone = false
        while (!isDone) {
            const { done, value } = await reader.read()
            if (done) break
            
            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split('\n')
            buffer = lines.pop() || '' // Keep the last partial line
            
            for (const line of lines) {
                if (line.trim().startsWith('data:')) {
                    const content = line.trim().slice(5) // Remove "data:"
                    if (content.includes('[DONE]')) {
                        isDone = true
                        break
                    }
                    accumulatedContent += content
                    
                    if (quill) {
                        // Update Quill content in real-time
                        // Since we request HTML, we set innerHTML
                        quill.root.innerHTML = accumulatedContent
                    } else {
                        form.value.content = accumulatedContent
                    }
                }
            }
        }
        
        // Process any remaining buffer
        if (!isDone && buffer.trim().startsWith('data:')) {
             const content = buffer.trim().slice(5)
             if (content.includes('[DONE]')) {
                 isDone = true
             } else {
                 accumulatedContent += content
             }
        }
        
        if (quill) {
             quill.root.innerHTML = accumulatedContent
        } else {
             form.value.content = accumulatedContent
        }

        // Auto fill title if empty
        if (!form.value.title) {
            form.value.title = aiKeyword.value + ' 测评分享'
        }
        
        ElMessage.success('AI 生成完成！')

    } catch (error) {
        console.error('AI Generation failed', error)
        ElMessage.error('AI 生成失败，请稍后重试')
    } finally {
        aiLoading.value = false
    }
}

const selectedCoverStyle = ref(0)
const selectedTextStyle = ref(0)

const textStyles = [
    { name: '经典白', color: '#FFFFFF', font: 'sans-serif', weight: 'bold', shadow: true },
    { name: '极简黑', color: '#000000', font: 'sans-serif', weight: 'bold', shadow: false },
    { name: '衬线雅', color: '#FFFFFF', font: 'serif', weight: 'bold', shadow: true },
    { name: '活力黄', color: '#FFD700', font: 'sans-serif', weight: '900', shadow: true, shadowColor: 'rgba(0,0,0,0.8)' },
    { name: '清新绿', color: '#E0FFEB', font: 'monospace', weight: 'bold', shadow: true, shadowColor: '#004d00' },
    { name: '霓虹粉', color: '#FF00FF', font: 'sans-serif', weight: 'bold', shadow: true, shadowColor: '#00FFFF', glow: true },
    { name: '描边黑', color: '#FFFFFF', font: 'Impact, sans-serif', weight: 'bold', stroke: '#000000', strokeWidth: 3 }
]

const coverStyles = [
    { name: '粉嫩', type: 'gradient', colors: ['#FF9A9E', '#FECFEF'], decoration: 'circles' },
    { name: '紫罗兰', type: 'gradient', colors: ['#a18cd1', '#fbc2eb'], decoration: 'circles' },
    { name: '清新', type: 'gradient', colors: ['#84fab0', '#8fd3f4'], decoration: 'circles' },
    { name: '暗黑', type: 'gradient', colors: ['#434343', '#000000'], decoration: 'grid' },
    { name: '日落', type: 'gradient', colors: ['#fa709a', '#fee140'], decoration: 'lines' },
    { name: '幽蓝', type: 'gradient', colors: ['#30cfd0', '#330867'], decoration: 'bubbles' },
    { name: '纯净白', type: 'solid', colors: ['#ffffff'], decoration: 'border', defaultTextIndex: 1 },
    { name: '复古纸张', type: 'solid', colors: ['#f4e4bc'], decoration: 'noise', defaultTextIndex: 1 },
    { name: '科技蓝', type: 'gradient', colors: ['#000428', '#004e92'], decoration: 'grid' },
    { name: '派对', type: 'solid', colors: ['#FFF5E6'], decoration: 'confetti', defaultTextIndex: 1 },
    { name: '几何', type: 'gradient', colors: ['#2E3192', '#1BFFFF'], decoration: 'geometric' },
    { name: '赛博', type: 'solid', colors: ['#000000'], decoration: 'neon', defaultTextIndex: 5 }
]

const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { Authorization: 'Bearer ' + token } : {}
})

const drawCanvasContent = (ctx, w, h, text) => {
    const bgStyle = coverStyles[selectedCoverStyle.value]
    const txtStyle = textStyles[selectedTextStyle.value]

    // 1. Draw Background
    if (bgStyle.type === 'solid') {
        ctx.fillStyle = bgStyle.colors[0]
        ctx.fillRect(0, 0, w, h)
    } else {
        const grd = ctx.createLinearGradient(0, 0, w, h)
        grd.addColorStop(0, bgStyle.colors[0])
        grd.addColorStop(1, bgStyle.colors[1])
        ctx.fillStyle = grd
        ctx.fillRect(0, 0, w, h)
    }
    
    // 2. Draw Decorations
    ctx.save()
    if (bgStyle.decoration === 'circles' || bgStyle.decoration === 'bubbles') {
        ctx.fillStyle = 'rgba(255,255,255,0.1)'
        const count = bgStyle.decoration === 'bubbles' ? 30 : 50
        for(let i=0; i<count; i++) {
            ctx.beginPath()
            const r = Math.random() * (bgStyle.decoration === 'bubbles' ? 80 : 50)
            ctx.arc(Math.random()*w, Math.random()*h, r, 0, 2*Math.PI)
            ctx.fill()
        }
    } else if (bgStyle.decoration === 'grid') {
        ctx.strokeStyle = 'rgba(255,255,255,0.1)'
        ctx.lineWidth = 2
        const step = 50
        // Vertical lines
        for(let x=0; x<=w; x+=step) {
            ctx.beginPath()
            ctx.moveTo(x, 0)
            ctx.lineTo(x, h)
            ctx.stroke()
        }
        // Horizontal lines
        for(let y=0; y<=h; y+=step) {
            ctx.beginPath()
            ctx.moveTo(0, y)
            ctx.lineTo(w, y)
            ctx.stroke()
        }
    } else if (bgStyle.decoration === 'lines') {
        ctx.strokeStyle = 'rgba(255,255,255,0.15)'
        ctx.lineWidth = 3
        for(let i=0; i<20; i++) {
            ctx.beginPath()
            const x = Math.random() * w
            const y = Math.random() * h
            ctx.moveTo(x, y)
            ctx.lineTo(x + 200, y + 200)
            ctx.stroke()
        }
    } else if (bgStyle.decoration === 'border') {
        ctx.strokeStyle = txtStyle.color === '#FFFFFF' ? 'rgba(255,255,255,0.8)' : 'rgba(0,0,0,0.8)'
        ctx.lineWidth = 20
        ctx.strokeRect(20, 20, w-40, h-40)
        ctx.lineWidth = 2
        ctx.strokeRect(50, 50, w-100, h-100)
    } else if (bgStyle.decoration === 'noise') {
        ctx.fillStyle = 'rgba(0,0,0,0.05)'
        for(let i=0; i<5000; i++) {
            ctx.fillRect(Math.random()*w, Math.random()*h, 2, 2)
        }
    } else if (bgStyle.decoration === 'confetti') {
        const colors = ['#FFC700', '#FF0000', '#2E3192', '#009E00', '#FF00FF']
        for(let i=0; i<100; i++) {
            ctx.fillStyle = colors[Math.floor(Math.random() * colors.length)]
            ctx.save()
            ctx.translate(Math.random() * w, Math.random() * h)
            ctx.rotate(Math.random() * Math.PI * 2)
            ctx.fillRect(0, 0, 8 + Math.random() * 8, 4 + Math.random() * 4)
            ctx.restore()
        }
    } else if (bgStyle.decoration === 'geometric') {
        for(let i=0; i<15; i++) {
            ctx.fillStyle = `rgba(255,255,255,${0.05 + Math.random() * 0.1})`
            ctx.beginPath()
            ctx.moveTo(Math.random() * w, Math.random() * h)
            ctx.lineTo(Math.random() * w, Math.random() * h)
            ctx.lineTo(Math.random() * w, Math.random() * h)
            ctx.fill()
        }
    } else if (bgStyle.decoration === 'neon') {
        ctx.strokeStyle = '#00FFFF'
        ctx.shadowColor = '#00FFFF'
        ctx.shadowBlur = 20
        ctx.lineWidth = 2
        for(let i=0; i<5; i++) {
            ctx.beginPath()
            const y = Math.random() * h
            ctx.moveTo(0, y)
            ctx.bezierCurveTo(w/3, y - 100, 2*w/3, y + 100, w, y)
            ctx.stroke()
        }
        ctx.strokeStyle = '#FF00FF'
        ctx.shadowColor = '#FF00FF'
        for(let i=0; i<5; i++) {
            ctx.beginPath()
            const x = Math.random() * w
            ctx.moveTo(x, 0)
            ctx.bezierCurveTo(x - 100, h/3, x + 100, 2*h/3, x, h)
            ctx.stroke()
        }
        ctx.shadowBlur = 0
    }
    ctx.restore()

    // 3. Draw Text
    ctx.fillStyle = txtStyle.color
    ctx.font = `${txtStyle.weight} 56px ${txtStyle.font}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    
    if (txtStyle.glow) {
        ctx.shadowColor = txtStyle.shadowColor || 'white'
        ctx.shadowBlur = 20
    } else if (txtStyle.shadow) {
        ctx.shadowColor = txtStyle.shadowColor || 'rgba(0,0,0,0.3)'
        ctx.shadowBlur = 10
        ctx.shadowOffsetX = 2
        ctx.shadowOffsetY = 2
    } else {
        ctx.shadowColor = 'transparent'
        ctx.shadowBlur = 0
        ctx.shadowOffsetX = 0
        ctx.shadowOffsetY = 0
    }

    // Wrap text
    const words = text.split('')
    let line = ''
    const lines = []
    const maxWidth = w - 120
    const lineHeight = 70

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
        if (txtStyle.stroke) {
            ctx.lineWidth = txtStyle.strokeWidth || 2
            ctx.strokeStyle = txtStyle.stroke
            ctx.strokeText(lines[i], w / 2, startY + (i * lineHeight))
        }
        ctx.fillText(lines[i], w / 2, startY + (i * lineHeight))
    }
    
    // 4. Add "GoodShare" watermark
    ctx.font = `24px ${txtStyle.font}`
    ctx.shadowBlur = 0 // Remove shadow for watermark
    ctx.shadowOffsetX = 0
    ctx.shadowOffsetY = 0
    ctx.fillStyle = txtStyle.color
    ctx.globalAlpha = 0.6
    ctx.fillText('GoodShare', w / 2, h - 50)
    ctx.globalAlpha = 1.0
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

const generateAndUploadCover = async (text) => {
    const canvas = document.createElement('canvas')
    const width = 600
    const height = 800
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    
    drawCanvasContent(ctx, width, height, text)
    
    return new Promise((resolve, reject) => {
        canvas.toBlob(async (blob) => {
            if (!blob) {
                reject(new Error('Canvas creation failed'))
                return
            }
            const formData = new FormData()
            formData.append('file', blob, 'cover.jpg')
            
            try {
                const res = await request.post('/upload', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                })
                resolve(res.data ? res.data.url : '') 
            } catch (err) {
                reject(err)
            }
        }, 'image/jpeg', 0.8)
    })
}

// Real-time preview cover update
const updatePreviewCover = async () => {
    if (fileList.value.length === 0 && !hasContentImage.value) {
        previewCoverUrl.value = await generateCoverDataUrl(form.value.title || '无标题')
    }
}
let debounceTimer = null
watch([() => form.value.title, selectedCoverStyle, selectedTextStyle], () => {
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(updatePreviewCover, 300)
}, { immediate: true })

watch(() => fileList.value.length, () => {
    updatePreviewCover()
})



watch(selectedCoverStyle, (newVal) => {
    const style = coverStyles[newVal]
    if (style.defaultTextIndex !== undefined) {
        selectedTextStyle.value = style.defaultTextIndex
    }
})



// Check for images in content
watch(() => form.value.content, (newVal) => {
    const imgRegex = /<img[^>]+src="([^">]+)"/g
    hasContentImage.value = imgRegex.test(newVal)
})

const route = useRoute()

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

        // Load Emoji
        loadStyle('https://unpkg.com/quill-emoji@0.2.0/dist/quill-emoji.css')
        await loadScript('https://unpkg.com/quill-emoji@0.2.0/dist/quill-emoji.js')

        // Initialize Quill
        if (window.Quill && editorContainer.value) {
            quill = new window.Quill(editorContainer.value, {
                theme: 'snow',
                placeholder: '填写正文...',
                modules: {
                    toolbar: {
                        container: [
                            [{ 'header': [1, 2, false] }],
                            ['bold', 'italic', 'underline', 'strike'],
                            [{ 'list': 'ordered'}, { 'list': 'bullet' }],
                            ['link', 'image'],
                            ['emoji']
                        ],
                        handlers: { 'emoji': function() {} }
                    },
                    "emoji-toolbar": true,
                    "emoji-textarea": false,
                    "emoji-shortname": true,
                }
            })
            
            quill.on('text-change', () => {
                form.value.content = quill.root.innerHTML
            })

            // Load existing post if in edit mode
            if (route.query.id) {
                try {
                    const res = await request.get('/posts/' + route.query.id)
                    const post = res.data
                    form.value.title = post.title
                    form.value.content = post.content
                    if (quill) {
                         // Use clipboard or insertHTML to set content safely
                         quill.root.innerHTML = post.content
                    }
                    
                    if (post.tags) {
                        form.value.tags = post.tags.map(t => t.name)
                    }

                    if (post.images) {
                        try {
                            const imgs = JSON.parse(post.images)
                            fileList.value = imgs.map((url, index) => ({ 
                                name: 'img_' + index, 
                                url: url,
                                response: { url: url } // Mock response structure for uniformity
                            }))
                        } catch(e) {
                            console.error("Failed to parse images", e)
                        }
                    } else if (post.coverUrl) {
                         // If only coverUrl and no images list (legacy or text post with cover)
                         // But usually we treat fileList as sources.
                         // If it's a generated cover, we might leave fileList empty.
                    }
                    
                    form.value.coverUrl = post.coverUrl
                } catch (err) {
                    console.error('Failed to load post', err)
                    ElMessage.error('加载帖子失败')
                }
            }

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

const prePublish = async () => {
  if (!form.value.title) {
      ElMessage.warning('请填写标题')
      return
  }

  // Extract URLs from fileList
  const urls = fileList.value
    .map(file => file.response?.url) // 只获取服务器返回的 url
    .filter(u => u && !u.startsWith('data:')); // 过滤掉所有 data: URI
  const isContentEmpty = !form.value.content || form.value.content === '<p><br></p>' || form.value.content.trim() === '';

  if (isContentEmpty && urls.length === 0) {
      ElMessage.warning('请填写正文或上传图片')
      return
  }

  if (form.value.tags.length === 0) {
      ElMessage.warning('请至少选择一个标签')
      return
  }
  
  await handlePreview()
}

const confirmPublish = async () => {
  // Extract URLs from fileList
  const urls = fileList.value
    .map(file => file.response?.url) // 只获取服务器返回的 url
    .filter(u => u && !u.startsWith('data:')); // 过滤掉所有 data: URI

  loading.value = true
  try {
    // 1. Check for uploaded images
    if (urls.length === 0) {
        // 2. Check for content images
        const contentImg = getFirstContentImage(form.value.content)
        if (contentImg) {
            form.value.coverUrl = contentImg
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
    
    if (!form.value.coverUrl && urls.length > 0) {
        form.value.coverUrl = urls[0]
    } else if (!form.value.coverUrl) {
        form.value.coverUrl = ''
    }

    await request.post('/posts', form.value)
    ElMessage.success('发布成功')
    router.push('/')
  } catch (error) {
    loading.value = false
  } finally {
    loading.value = false
    showPreview.value = false
  }
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
    width: 100%;
    background: var(--bg-color-overlay);
    padding: 40px;
    border-radius: 8px;
    box-shadow: 0 2px 12px 0 rgba(0,0,0,0.05);
    transition: background-color 0.3s;
}
.publish-layout {
  display: flex;
  max-width: 1200px;
  margin: 0 auto;
  gap: 40px;
  align-items: flex-start;
  width: 100%;
}

.form-section {
  flex: 1;
  min-width: 0;
}

.publish-preview-panel {
  width: 320px;
  flex-shrink: 0;
  position: sticky;
  top: 100px;
}

/* Live Preview Panel (Sidebar) */
.post-detail-preview {
    background: var(--bg-color-overlay);
    border-radius: 12px;
    overflow: hidden;
    box-shadow: 0 4px 12px rgba(0,0,0,0.05);
    border: 1px solid var(--border-color);
    display: flex;
    flex-direction: column;
    max-height: calc(100vh - 140px);
}

.post-detail-preview .content-flex {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
}

.post-detail-preview .image-section {
    width: 100%;
    height: 320px;
    flex-shrink: 0;
    background: #000;
    position: relative;
}

.post-detail-preview .image-wrapper {
    width: 100%;
    height: 100%;
    background-size: contain;
    background-repeat: no-repeat;
    background-position: center;
}

.post-detail-preview :deep(.el-carousel) {
    width: 100%;
    height: 100%;
}

.post-detail-preview .info-section {
    padding: 16px;
    background: var(--bg-color-overlay);
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
}

.post-detail-preview .info-section.full-width {
    height: 100%;
}

.post-detail-preview .author-header {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
    flex-shrink: 0;
}

.post-detail-preview .username {
    margin-left: 8px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text-color);
    flex: 1;
}

.post-detail-preview .scrollable-content {
    flex: 1;
    margin-bottom: 12px;
}

.post-detail-preview .post-title {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 8px;
    color: var(--text-color);
    line-height: 1.4;
}

.post-detail-preview .post-text {
    font-size: 14px;
    color: var(--text-color);
    line-height: 1.5;
    margin-bottom: 12px;
}

.post-detail-preview .post-text :deep(img) {
    max-width: 100%;
    border-radius: 4px;
}

.post-detail-preview .tags-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 8px;
}

.post-detail-preview .tag {
    color: var(--el-color-primary);
    font-size: 12px;
}

.post-detail-preview .date {
    font-size: 12px;
    color: var(--text-color-secondary);
    margin-bottom: 12px;
}

.post-detail-preview .comments-section {
    margin-top: 12px;
    border-top: 1px solid var(--border-color);
    padding-top: 12px;
}

.post-detail-preview .comment-count {
    font-size: 12px;
    color: var(--text-color-secondary);
    margin-bottom: 8px;
}

.post-detail-preview .no-comments {
    text-align: center;
    color: var(--text-color-secondary);
    font-size: 12px;
    padding: 10px 0;
}

.post-detail-preview .bottom-actions {
    margin-top: 12px;
    flex-shrink: 0;
}

.post-detail-preview .interaction-bar {
    display: flex;
    justify-content: space-around;
    margin-bottom: 12px;
}

.post-detail-preview .action-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    color: var(--text-color);
    cursor: pointer;
    opacity: 0.8;
}

.post-detail-preview .action-btn span {
    font-size: 12px;
    margin-top: 2px;
}

.post-detail-preview .comment-input-area {
    display: flex;
    gap: 8px;
}

.preview-tip {
    margin-top: 20px;
    color: var(--text-color-secondary);
    font-size: 12px;
    text-align: center;
}

.page-title {
    margin-bottom: 30px;
    font-size: 24px;
    font-weight: 600;
    color: var(--text-color);
}
.dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    padding-top: 20px;
    border-top: 1px solid var(--border-color);
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
.image-uploader :deep(.el-upload-list__item-thumbnail) {
    width: 100%;
    height: 100%;
    object-fit: cover;
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
