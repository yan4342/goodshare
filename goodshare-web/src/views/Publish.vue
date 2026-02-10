<template>
  <div class="publish-container">
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
                  <div class="cover-styles-grid">
                      <div 
                        v-for="(tpl, index) in coverTemplates" 
                        :key="index"
                        class="style-card"
                        :class="{ active: selectedTemplateIndex === index }"
                        @click="selectedTemplateIndex = index"
                      >
                        <div class="style-preview-mini" :class="tpl.id" :style="getMiniPreviewStyle(tpl.id)">
                            <span class="mini-text" v-if="tpl.id !== 'illustration' && tpl.id !== 'memo' && tpl.id !== 'border' && tpl.id !== 'book'">Aa</span>
                            <div v-if="tpl.id === 'illustration'" class="mini-img">🖼️</div>
                            <div v-if="tpl.id === 'memo'" class="mini-icon">📝</div>
                            <div v-if="tpl.id === 'book'" class="mini-icon">📖</div>
                            <div v-if="tpl.id === 'border'" class="mini-border-inner"></div>
                            <span v-if="tpl.id === 'border' || tpl.id === 'illustration' || tpl.id === 'memo' || tpl.id === 'book'" class="mini-text-small">Aa</span>
                        </div>
                        <span class="style-name">{{ tpl.name }}</span>
                        <el-icon v-if="selectedTemplateIndex === index" class="check-icon"><Check /></el-icon>
                      </div>
                  </div>
                  
                  <div class="color-picker-section" v-if="currentTemplateColors.length > 0">
                       <div class="color-label">配色方案</div>
                       <div class="color-options">
                           <div 
                                v-for="(color, index) in currentTemplateColors" 
                                :key="index" 
                                class="color-circle"
                                :class="{ active: selectedColorIndex === index }"
                                :style="{ background: color.preview || color.bg }"
                                @click="selectedColorIndex = index"
                           >
                                <el-icon v-if="selectedColorIndex === index"><Check /></el-icon>
                           </div>
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
                 <div class="image-section">
                     <el-carousel v-if="fileList.length > 0" height="100%" arrow="hover" :autoplay="false" indicator-position="none">
                         <el-carousel-item v-for="(file, index) in fileList" :key="index">
                             <div class="image-wrapper" :style="{ backgroundImage: `url('${file.url || (file.response && file.response.url)}')` }"></div>
                         </el-carousel-item>
                     </el-carousel>
                     <div v-else-if="previewCoverUrl" class="image-wrapper" :style="{ backgroundImage: `url('${previewCoverUrl}')` }"></div>
                     <div v-else class="image-wrapper placeholder">
                         <span>封面预览</span>
                     </div>
                 </div>

                 <!-- Right: Info -->
                 <div class="info-section">
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

    <!-- Hidden Canvas Capture Area -->
    <div class="canvas-capture-container">
        <div class="capture-content" ref="captureRef" :class="'template-' + currentTemplate.id">
             <!-- Basic -->
             <template v-if="currentTemplate.id === 'basic'">
                 <div class="bg-layer basic-bg" :style="getCurrentStyle('basic')"></div>
                 <div class="content-layer">
                     <div class="quote-icon" :style="{ color: getCurrentColor('basic', 'accent') }">“</div>
                     <div class="text-main" :style="{ color: getCurrentColor('basic', 'text') }">{{ form.title || '这是标题啊' }}</div>
                     <div class="underline-icon" :style="{ color: getCurrentColor('basic', 'accent') }">_</div>
                 </div>
             </template>

             <!--Now Book/Paper-->
             <template v-if="currentTemplate.id === 'book'">
                 <div class="bg-layer book-bg" :style="getCurrentStyle('book')">
                     <div class="book-texture"></div>
                 </div>
                 <div class="content-layer">
                     <div class="book-header">Chapter 1</div>
                     <div class="text-book">{{ form.title || '这是标题啊' }}</div>
                     <div class="book-footer">- GoodShare -</div>
                 </div>
             </template>

             <!-- Memo -->
             <template v-if="currentTemplate.id === 'memo'">
                 <div class="bg-layer memo-bg" :style="getCurrentStyle('memo')"></div>
                 <div class="content-layer">
                     <div class="memo-header">
                         <span class="memo-date">{{ new Date().toLocaleDateString() }}</span>
                         <span class="memo-dots">...</span>
                     </div>
                     <div class="text-handwriting">{{ form.title || '这是标题啊' }}</div>
                     <div class="sticker-icon">🐱</div>
                 </div>
             </template>

             <!-- Border -->
             <template v-if="currentTemplate.id === 'border'">
                 <div class="bg-layer border-bg" :style="getCurrentStyle('border')">
                     <div class="inner-card">
                         <div class="emoji-icon">😊</div>
                         <div class="text-main">{{ form.title || '这是标题啊' }}</div>
                         <div class="footer-info" :style="{ color: getCurrentColor('border', 'accent') }">
                             <span>Saturday</span>
                             <span>Text Note</span>
                         </div>
                     </div>
                 </div>
             </template>

             <!-- Handwriting -->
             <template v-if="currentTemplate.id === 'handwriting'">
                 <div class="bg-layer handwriting-bg" :style="getCurrentStyle('handwriting')">
                     <div class="paint-stroke" :style="{ background: getCurrentColor('handwriting', 'stroke') }"></div>
                 </div>
                 <div class="content-layer">
                     <div class="date-stamp">Date: {{ new Date().getDate() }}.{{ new Date().getMonth() + 1 }}</div>
                     <div class="text-handwriting-stroke" :style="{ color: getCurrentColor('handwriting', 'text') }">{{ form.title || '这是标题啊' }}</div>
                     <div class="doodle-icon">❤️</div>
                 </div>
             </template>

             <!-- Scribble -->
             <template v-if="currentTemplate.id === 'scribble'">
                 <div class="bg-layer scribble-bg" :style="getCurrentStyle('scribble')"></div>
                 <div class="content-layer centered">
                     <div class="scribble-container">
                         <template v-for="(part, idx) in processedScribbleTitle" :key="idx">
                             <span class="scribble-part" :class="{ highlight: part.highlight }" :style="part.highlight ? { background: getCurrentColor('scribble', 'highlight') } : {}">
                                 {{ part.text }}
                             </span>
                         </template>
                     </div>
                 </div>
             </template>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { Plus, Check, Star, StarFilled, Collection, CollectionTag, ChatDotRound, MagicStick, Refresh } from '@element-plus/icons-vue'
import request from '../utils/request'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import html2canvas from 'html2canvas'

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
const captureRef = ref(null)
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

// Cover Templates
const selectedTemplateIndex = ref(0)
const coverTemplates = [
    { id: 'basic', name: '基础' },
    { id: 'book', name: '书本' }, // Renamed from Illustration
    { id: 'memo', name: '备忘' },
    { id: 'border', name: '边框' },
    { id: 'handwriting', name: '手写' },
    { id: 'scribble', name: '涂写' },
]

// Color Themes Configuration
const selectedColorIndex = ref(0)
const colorThemes = {
    basic: [
        { bg: 'linear-gradient(180deg, #e6ffe6 0%, #ccffcc 100%)', text: '#333', accent: '#a3e6a3', border: '#52c41a' }, // Green Border
        { bg: 'linear-gradient(180deg, #e6f7ff 0%, #bae7ff 100%)', text: '#0050b3', accent: '#91d5ff', border: '#1890ff' },
        { bg: 'linear-gradient(180deg, #fff0f6 0%, #ffd6e7 100%)', text: '#c41d7f', accent: '#ffadd2', border: '#eb2f96' },
        { bg: 'linear-gradient(180deg, #fffbe6 0%, #ffe58f 100%)', text: '#d48806', accent: '#ffe58f', border: '#faad14' }
    ],
    book: [
        { bg: '#fdfbf7', text: '#2c3e50', shadow: 'rgba(0,0,0,0.1)' }, // Warm White
        { bg: '#f4e4bc', text: '#4a3b2a', shadow: 'rgba(92, 64, 51, 0.15)' }, // Sepia/Old Paper
        { bg: '#eef2f3', text: '#2d3436', shadow: 'rgba(0,0,0,0.15)' }, // Cool Grey
        { bg: '#2c3e50', text: '#ecf0f1', shadow: 'rgba(255,255,255,0.1)' } // Dark Mode
    ],
    memo: [
        { bg: '#fff', text: '#333', accent: '#f1c40f' },
        { bg: '#fff0f5', text: '#333', accent: '#ff69b4' },
        { bg: '#f0f8ff', text: '#333', accent: '#4682b4' },
        { bg: '#f5f5dc', text: '#333', accent: '#8b4513' }
    ],
    border: [
        { bg: '#2d9bf0', accent: '#2d9bf0', text: '#333' },
        { bg: '#ff6b6b', accent: '#ff6b6b', text: '#333' },
        { bg: '#1dd1a1', accent: '#1dd1a1', text: '#333' },
        { bg: '#5f27cd', accent: '#5f27cd', text: '#333' }
    ],
    handwriting: [
        { bg: '#fdfdfd', stroke: '#e0f7fa', text: '#333' },
        { bg: '#fff8e1', stroke: '#ffecb3', text: '#4e342e' },
        { bg: '#f3e5f5', stroke: '#e1bee7', text: '#4a148c' },
        { bg: '#e8f5e9', stroke: '#c8e6c9', text: '#1b5e20' }
    ],
    scribble: [
        { bg: '#f9f9f9', highlight: '#fff100' }, // Yellow
        { bg: '#f0fff4', highlight: '#68d391' }, // Green
        { bg: '#fff5f5', highlight: '#fc8181' }, // Red
        { bg: '#ebf8ff', highlight: '#63b3ed' }  // Blue
    ]
}

const currentTemplateColors = computed(() => {
    return colorThemes[currentTemplate.value.id] || []
})

// Reset color selection when template changes
watch(selectedTemplateIndex, () => {
    selectedColorIndex.value = 0
})

const getCurrentStyle = (templateId) => {
    if (templateId !== currentTemplate.value.id) return {}
    const colors = colorThemes[templateId]
    if (!colors) return {}
    const color = colors[selectedColorIndex.value] || colors[0]
    
    if (templateId === 'basic') return { background: color.bg, borderColor: color.border }
    if (templateId === 'book') return { background: color.bg, color: color.text }
    if (templateId === 'memo') return { backgroundColor: color.bg } // Use backgroundColor to preserve background-image lines
    if (templateId === 'border') return { background: color.bg }
    if (templateId === 'handwriting') return { background: color.bg }
    if (templateId === 'scribble') return { background: color.bg }
    return {}
}

const getCurrentColor = (templateId, key) => {
    if (templateId !== currentTemplate.value.id) return ''
    const colors = colorThemes[templateId]
    if (!colors) return ''
    const color = colors[selectedColorIndex.value] || colors[0]
    return color[key]
}

const getMiniPreviewStyle = (templateId) => {
    const colors = colorThemes[templateId]
    if (!colors) return {}
    // Use the first color for preview
    const color = colors[0]
    if (templateId === 'basic') return { background: 'linear-gradient(135deg, #e6ffe6 0%, #ccffcc 100%)', borderColor: color.border }
    if (templateId === 'book') return { background: color.bg }
    if (templateId === 'memo') return { background: color.bg }
    if (templateId === 'border') return { background: color.bg }
    if (templateId === 'handwriting') return { background: color.bg }
    if (templateId === 'scribble') return { background: color.bg }
    return {}
}

// Smart Scribble Highlight Logic
const processedScribbleTitle = computed(() => {
    const text = form.value.title || '这是标题啊'
    // Simple heuristic: split by spaces or punctuation
    // For Chinese, we might want to split by segmenter if supported, or just random
    // Fallback: Split by non-word characters or treat each character as potential? 
    // Let's assume standard space separation for English, and for Chinese we might just highlight random chunks if no spaces.
    
    // Heuristic:
    // 1. If text has spaces, split by spaces.
    // 2. If no spaces (likely Chinese/Japanese), try to split by punctuation.
    // 3. If no punctuation, split into chunks of 2-4 chars.
    
    let parts = []
    // 如果有空格（通常是英文），直接按空格分
    if (text.includes(' ')) {
        parts = text.split(' ').map(t => ({ text: t, highlight: false }))
    } else {
        // 如果没有空格（通常是中文），模拟分词 Mock segmentation for demo: random split 2-3 chars
        let remaining = text
        while (remaining.length > 0) {
            const len = Math.floor(Math.random() * 3) + 2 // 2 to 4 chars
            const chunk = remaining.slice(0, len)
            parts.push({ text: chunk, highlight: false })
            remaining = remaining.slice(len)
        }
    }
    
    // "Smart" Highlight: highlight longer words or every other word
    // Let's highlight the last segment (punchline) and maybe one in the middle
    if (parts.length > 0) {
        // Always highlight the last part if it's substantial
        parts[parts.length - 1].highlight = true
        
        // Randomly highlight others
        if (parts.length > 2) {
            const randomIndex = Math.floor(Math.random() * (parts.length - 1))
            parts[randomIndex].highlight = true
        }
    }
    
    return parts
})

// Debounce update for color change too
watch(selectedColorIndex, () => {
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(updatePreviewCover, 500)
})

const currentTemplate = computed(() => coverTemplates[selectedTemplateIndex.value])

const handleAiGenerate = async () => {
    if (!aiKeyword.value.trim()) {
        ElMessage.warning('请输入商品名或主题')
        return
    }

    aiLoading.value = true
    try {
        const token = localStorage.getItem('token') || localStorage.getItem('admin_token')
        const response = await fetch('/api/ai/generate-stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            },
            body: JSON.stringify({ keyword: aiKeyword.value })
        })

        if (response.status === 401) {
             ElMessage.error('请先登录')
             router.push('/login')
             return
        }

        if (!response.ok) {
            throw new Error('AI request failed')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let accumulatedContent = ''
        let buffer = ''
        
        if (quill) {
             quill.root.innerHTML = '<p>AI 正在思考...</p>'
        }

        let isDone = false
        while (!isDone) {
            const { done, value } = await reader.read()
            if (done) break
            
            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split('\n')
            buffer = lines.pop() || '' 
            
            for (const line of lines) {
                if (line.trim().startsWith('data:')) {
                    const content = line.trim().slice(5) 
                    if (content.includes('[DONE]')) {
                        isDone = true
                        break
                    }
                    accumulatedContent += content
                    
                    if (quill) {
                        quill.root.innerHTML = accumulatedContent
                    } else {
                        form.value.content = accumulatedContent
                    }
                }
            }
        }
        
        if (!isDone && buffer.trim().startsWith('data:')) {
             const content = buffer.trim().slice(5)
             if (!content.includes('[DONE]')) {
                 accumulatedContent += content
             }
        }
        
        if (quill) {
             quill.root.innerHTML = accumulatedContent
        } else {
             form.value.content = accumulatedContent
        }

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

const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { Authorization: 'Bearer ' + token } : {}
})

// Generate Cover Image using html2canvas
const generateCoverDataUrl = async () => {
    if (!captureRef.value) return ''
    
    // Wait for DOM update
    await nextTick()
    
    try {// 核心：调用 html2canvas 将 DOM 转换为 canvas 对象
        const canvas = await html2canvas(captureRef.value, {
            scale: 1, // Capture at 1:1 of the 600x800 element
            useCORS: true,
            backgroundColor: null
        })
        // 转换为 Base64 图片链接
        return canvas.toDataURL('image/jpeg', 0.8)
    } catch (e) {
        console.error('Capture failed', e)
        return ''
    }
}

const generateAndUploadCover = async () => {
    if (!captureRef.value) return ''
    
    await nextTick()
    
    try {
        const canvas = await html2canvas(captureRef.value, {
            scale: 1,
            useCORS: true
        })
        
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
    } catch (e) {
        console.error('Upload failed', e)
        throw e
    }
}

const updatePreviewCover = async () => {
    if (fileList.value.length === 0 && !hasContentImage.value) {
        // Debounce slightly to allow DOM to render
        setTimeout(async () => {
             previewCoverUrl.value = await generateCoverDataUrl()
        }, 100)
    }
}

let debounceTimer = null
watch([() => form.value.title, selectedTemplateIndex], () => {
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(updatePreviewCover, 500)
}, { immediate: true })

watch(() => fileList.value.length, () => {
    updatePreviewCover()
})

watch(() => form.value.content, (newVal) => {
    const imgRegex = /<img[^>]+src="([^">]+)"/g
    hasContentImage.value = imgRegex.test(newVal)
})

const route = useRoute()

onMounted(async () => {
    const loadScript = (src) => {
        return new Promise((resolve, reject) => {
            if (document.querySelector(`script[src^="${src}"]`)) {
                resolve()
                return
            }
            const script = document.createElement('script')
            script.src = src + '?t=' + new Date().getTime() 
            script.onload = resolve
            script.onerror = reject
            document.head.appendChild(script)
        })
    }

    const loadStyle = (href) => {
        if (document.querySelector(`link[href^="${href}"]`)) return
        const link = document.createElement('link')
        link.rel = 'stylesheet'
        link.href = href
        document.head.appendChild(link)
    }

    try {
        loadStyle('/quill/quill.snow.css')
        await loadScript('/quill/quill.min.js')
        loadStyle('/quill/quill-emoji.css')
        await loadScript('/quill/quill-emoji.js')

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

            if (route.query.id) {
                try {
                    const res = await request.get('/posts/' + route.query.id)
                    const post = res.data
                    form.value.title = post.title
                    form.value.content = post.content
                    if (quill) {
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
                                response: { url: url }
                            }))
                        } catch(e) {
                            console.error("Failed to parse images", e)
                        }
                    }
                    
                    form.value.coverUrl = post.coverUrl
                } catch (err) {
                    ElMessage.error('加载帖子失败')
                }
            }

        } else {
            ElMessage.error('编辑器初始化失败')
        }
    } catch (error) {
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
  // handled by fileList
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
    const uploadedUrls = fileList.value.map(file => file.response?.url || file.url).filter(u => u)
    
    if (uploadedUrls.length > 0) {
        previewData.value.images = uploadedUrls
    } else {
        const contentImg = getFirstContentImage(form.value.content)
        if (contentImg) {
             previewData.value.images = [contentImg]
        } else {
             const cover = await generateCoverDataUrl()
             previewData.value.images = [cover]
        }
    }
}

const prePublish = async () => {
  if (!form.value.title) {
      ElMessage.warning('请填写标题')
      return
  }

  const urls = fileList.value
    .map(file => file.response?.url)
    .filter(u => u && !u.startsWith('data:'));
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
  const urls = fileList.value
    .map(file => file.response?.url)
    .filter(u => u && !u.startsWith('data:'));

  loading.value = true
  try {
    if (urls.length === 0) {
        const contentImg = getFirstContentImage(form.value.content)
        if (contentImg) {
            form.value.coverUrl = contentImg
        } else {
            try {
                const generatedCoverUrl = await generateAndUploadCover()
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

.post-detail-preview .image-wrapper.placeholder {
    display: flex;
    justify-content: center;
    align-items: center;
    color: white;
    font-size: 14px;
    background: #333;
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

/* New Cover Styles Grid */
.cover-styles-grid {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;
    margin-top: 8px;
}
.style-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    cursor: pointer;
    position: relative;
    width: 80px;
}
.style-preview-mini {
    width: 80px;
    height: 100px;
    border-radius: 8px;
    margin-bottom: 8px;
    border: 2px solid transparent;
    transition: all 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    box-shadow: 0 2px 6px rgba(0,0,0,0.1);
    position: relative;
    overflow: hidden;
}
.style-card.active .style-preview-mini {
    border-color: #ff2442;
    transform: scale(1.05);
}
.style-card.active .check-icon {
    position: absolute;
    top: -5px;
    right: -5px;
    background: #ff2442;
    color: white;
    border-radius: 50%;
    padding: 2px;
    width: 16px;
    height: 16px;
    z-index: 10;
}
.style-name {
    font-size: 12px;
    color: var(--text-color);
}

/* Mini Previews CSS */
.style-preview-mini.basic {
    background: linear-gradient(135deg, #e6ffe6 0%, #ccffcc 100%);
    color: #333;
    font-weight: bold;
    border: 3px solid #ff4d4f;
}
.style-preview-mini.illustration {
    background: #f8f9fa;
}
.style-preview-mini.illustration .mini-img {
    font-size: 24px;
}
.style-preview-mini.memo {
    background: #fff;
    border: 1px solid #eee;
}
.style-preview-mini.memo .mini-icon {
    font-size: 24px;
}
.style-preview-mini.border {
    background: #2d9bf0;
}
.style-preview-mini .mini-border-inner {
    width: 60%;
    height: 60%;
    background: white;
    border-radius: 4px;
}
.style-preview-mini.handwriting {
    background: #fdfdfd;
}
.style-preview-mini.scribble {
    background: #f9f9f9;
}
.style-preview-mini.scribble .mini-text {
    background: #fff100;
    padding: 2px;
}
.mini-text-small {
    font-size: 10px;
    color: #333;
    position: absolute;
    bottom: 5px;
}

/* Color Picker */
.color-picker-section {
    margin-top: 20px;
    padding-top: 15px;
    border-top: 1px dashed var(--border-color);
}
.color-label {
    font-size: 12px;
    color: var(--text-color-secondary);
    margin-bottom: 8px;
}
.color-options {
    display: flex;
    gap: 12px;
}
.color-circle {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    cursor: pointer;
    border: 2px solid transparent;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white; /* For check icon */
    font-size: 14px;
    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    transition: transform 0.2s;
}
.color-circle:hover {
    transform: scale(1.1);
}
.color-circle.active {
    border-color: var(--text-color);
    transform: scale(1.1);
}

/* Book Style */
.book-bg {
    background: #fdfbf7;
    position: relative;
    box-shadow: inset 20px 0 50px rgba(0,0,0,0.05); /* Spine shadow */
}
.template-book .book-texture {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-image: url("data:image/svg+xml,%3Csvg width='100' height='100' viewBox='0 0 100 100' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100' height='100' filter='url(%23noise)' opacity='0.05'/%3E%3C/svg%3E");
    opacity: 0.5;
}
.template-book .book-header {
    font-family: 'Times New Roman', serif;
    font-size: 24px;
    color: inherit;
    opacity: 0.6;
    margin-bottom: 60px;
    letter-spacing: 2px;
    text-transform: uppercase;
}
.template-book .text-book {
    font-family: 'Times New Roman', serif;
    font-size: 56px;
    font-weight: bold;
    text-align: center;
    padding: 0 60px;
    line-height: 1.5;
    color: inherit;
}
.template-book .book-footer {
    font-family: 'Times New Roman', serif;
    font-size: 18px;
    margin-top: 80px;
    opacity: 0.5;
    font-style: italic;
}
.style-preview-mini.book {
    background: #fdfbf7;
    border: 1px solid #e0e0e0;
}
.style-preview-mini.book .mini-icon {
    font-size: 24px;
}

/* Scribble Updates */
.scribble-container {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 10px;
    padding: 0 40px;
}
.scribble-part {
    font-family: 'Segoe UI', sans-serif;
    font-weight: 900;
    font-size: 60px;
    color: #000;
    padding: 5px 10px;
    position: relative;
    z-index: 1;
}
.scribble-part.highlight {
    transform: rotate(-1deg);
    box-shadow: 2px 2px 0px rgba(0,0,0,0.1);
}

/* Canvas Capture & Generation Templates */
.canvas-capture-container {
    position: fixed;
    top: 0;
    left: -9999px;
    z-index: -1;
}

.capture-content {
    width: 600px;
    height: 800px;
    position: relative;
    overflow: hidden;
    display: flex;
    flex-direction: column;
}

/* Common Layers */
.bg-layer {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 1;
}
.content-layer {
    position: relative;
    z-index: 2;
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
}

/* Basic */
.basic-bg {
    background: linear-gradient(180deg, #e6ffe6 0%, #ccffcc 100%);
    border: 10px solid #52c41a;
    border-radius: 20px;
    box-sizing: border-box;
    margin: 10px;
    width: calc(100% - 20px);
    height: calc(100% - 20px);
}
.template-basic .quote-icon {
    font-size: 120px;
    color: #a3e6a3;
    position: absolute;
    top: 50px;
    left: 50px;
    font-family: serif;
}
.template-basic .underline-icon {
    font-size: 120px;
    color: #a3e6a3;
    position: absolute;
    bottom: 50px;
    right: 50px;
    font-family: serif;
}
.template-basic .text-main {
    font-size: 60px;
    font-weight: bold;
    color: #333;
    text-align: center;
    padding: 0 40px;
    line-height: 1.4;
}
.template-basic .color-badge {
    position: absolute;
    bottom: 100px;
    background: rgba(255,255,255,0.8);
    padding: 10px 20px;
    border-radius: 30px;
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 20px;
    color: #666;
}

/* Illustration */
.illustration-bg {
    background: #f8f9fa;
}
.template-illustration .text-top {
    font-size: 50px;
    font-weight: bold;
    color: #333;
    margin-top: 100px;
    margin-bottom: 50px;
    text-align: center;
}
.template-illustration .illustration-img img {
    width: 400px;
    height: auto;
}

/* Memo */
.memo-bg {
    background: #fff;
    background-image: linear-gradient(rgba(0,0,0,0.05) 1px, transparent 1px);
    background-size: 100% 40px;
}
.template-memo .memo-header {
    width: 95%;
    padding: 40px;
    display: flex;
    justify-content: space-between;
    color: #f1c40f;
    font-size: 30px;
    font-family: monospace;
}
.template-memo .text-handwriting {
    font-family: 'KaiTi', 'STKaiti', serif;
    font-size: 60px;
    color: #333;
    text-align: center;
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
}
.template-memo .sticker-icon {
    font-size: 80px;
    margin-bottom: 100px;
}

/* Border */
.border-bg {
    background: #2d9bf0;
    display: flex;
    align-items: center;
    justify-content: center;
}
.template-border .inner-card {
    background: white;
    width: 80%;
    height: 80%;
    border-radius: 30px;
    box-shadow: 0 10px 30px rgba(0,0,0,0.2);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    position: relative;
}
.template-border .emoji-icon {
    font-size: 60px;
    margin-bottom: 30px;
}
.template-border .text-main {
    font-size: 50px;
    font-weight: bold;
    text-align: center;
    padding: 0 20px;
}
.template-border .footer-info {
    position: absolute;
    bottom: 30px;
    width: 100%;
    padding: 0 40px;
    display: flex;
    justify-content: space-between;
    color: #2d9bf0;
    font-weight: bold;
    font-size: 16px;
    box-sizing: border-box;
}

/* Handwriting */
.handwriting-bg {
    background: #fdfdfd;
}
.template-handwriting .paint-stroke {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 500px;
    height: 150px;
    background: #e0f7fa;
    border-radius: 10px;
    filter: blur(10px);
    opacity: 0.5;
}
.template-handwriting .text-handwriting-stroke {
    font-family: 'KaiTi', 'STKaiti', cursive;
    font-size: 70px;
    color: #333;
    position: relative;
    z-index: 2;
    transform: rotate(-2deg);
}
.template-handwriting .date-stamp {
    position: absolute;
    font-size: 30px;
    top: 50px;
    left: 50px;
    font-family: monospace;
    color: #666;
    border-bottom: 1px solid #666;
}

/* Scribble */
.scribble-bg {
    background: #f9f9f9;
}
.template-scribble .highlight-text {
    background: #fff100;
    padding: 20px 40px;
    transform: rotate(-1deg);
    box-shadow: 2px 2px 0px rgba(0,0,0,0.1);
}
.template-scribble .highlight-text span {
    font-family: 'Segoe UI', sans-serif;
    font-weight: 900;
    font-size: 60px;
    color: #000;
}
.template-scribble .content-layer.centered {
    justify-content: center;
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
    min-height: 200px;
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
