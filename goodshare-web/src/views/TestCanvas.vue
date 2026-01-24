<template>
  <div class="test-canvas-container">
    <h2>Canvas Style Test Cases</h2>
    <div class="controls">
        <el-input v-model="testText" placeholder="Input test text" style="width: 300px; margin-right: 20px;" />
        <el-button type="primary" @click="renderAll">Render All Styles</el-button>
    </div>
    
    <div class="canvas-grid">
        <div v-for="(cover, cIndex) in coverStyles" :key="cIndex" class="style-row">
            <h3>{{ cover.name }} ({{ cover.decoration }})</h3>
            <div class="style-variants">
                <div v-for="(text, tIndex) in textStyles" :key="tIndex" class="canvas-wrapper">
                    <canvas :ref="el => setCanvasRef(el, cIndex, tIndex)" width="300" height="400"></canvas>
                    <div class="label">{{ text.name }}</div>
                </div>
            </div>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'

const testText = ref('GoodShare\n测试文本')
const canvasRefs = ref({})

const setCanvasRef = (el, cIndex, tIndex) => {
    if (el) {
        canvasRefs.value[`${cIndex}-${tIndex}`] = el
    }
}

// Copy styles from Publish.vue (In a real app, these should be shared in a utility file)
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

const drawCanvasContent = (ctx, w, h, text, bgStyle, txtStyle) => {
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
            const r = Math.random() * (bgStyle.decoration === 'bubbles' ? 40 : 25) // Scaled down for test
            ctx.arc(Math.random()*w, Math.random()*h, r, 0, 2*Math.PI)
            ctx.fill()
        }
    } else if (bgStyle.decoration === 'grid') {
        ctx.strokeStyle = 'rgba(255,255,255,0.1)'
        ctx.lineWidth = 2
        const step = 25 // Scaled down
        for(let x=0; x<=w; x+=step) {
            ctx.beginPath()
            ctx.moveTo(x, 0)
            ctx.lineTo(x, h)
            ctx.stroke()
        }
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
            ctx.lineTo(x + 100, y + 100)
            ctx.stroke()
        }
    } else if (bgStyle.decoration === 'border') {
        ctx.strokeStyle = txtStyle.color === '#FFFFFF' ? 'rgba(255,255,255,0.8)' : 'rgba(0,0,0,0.8)'
        ctx.lineWidth = 10
        ctx.strokeRect(10, 10, w-20, h-20)
        ctx.lineWidth = 1
        ctx.strokeRect(25, 25, w-50, h-50)
    } else if (bgStyle.decoration === 'noise') {
        ctx.fillStyle = 'rgba(0,0,0,0.05)'
        for(let i=0; i<2500; i++) {
            ctx.fillRect(Math.random()*w, Math.random()*h, 2, 2)
        }
    } else if (bgStyle.decoration === 'confetti') {
        const colors = ['#FFC700', '#FF0000', '#2E3192', '#009E00', '#FF00FF']
        for(let i=0; i<50; i++) {
            ctx.fillStyle = colors[Math.floor(Math.random() * colors.length)]
            ctx.save()
            ctx.translate(Math.random() * w, Math.random() * h)
            ctx.rotate(Math.random() * Math.PI * 2)
            ctx.fillRect(0, 0, 4 + Math.random() * 4, 2 + Math.random() * 2)
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
        ctx.shadowBlur = 10
        ctx.lineWidth = 1
        for(let i=0; i<5; i++) {
            ctx.beginPath()
            const y = Math.random() * h
            ctx.moveTo(0, y)
            ctx.bezierCurveTo(w/3, y - 50, 2*w/3, y + 50, w, y)
            ctx.stroke()
        }
        ctx.strokeStyle = '#FF00FF'
        ctx.shadowColor = '#FF00FF'
        for(let i=0; i<5; i++) {
            ctx.beginPath()
            const x = Math.random() * w
            ctx.moveTo(x, 0)
            ctx.bezierCurveTo(x - 50, h/3, x + 50, 2*h/3, x, h)
            ctx.stroke()
        }
        ctx.shadowBlur = 0
    }
    ctx.restore()

    // 3. Draw Text
    ctx.fillStyle = txtStyle.color
    ctx.font = `${txtStyle.weight} 28px ${txtStyle.font}` // Scaled down
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    
    if (txtStyle.glow) {
        ctx.shadowColor = txtStyle.shadowColor || 'white'
        ctx.shadowBlur = 10
    } else if (txtStyle.shadow) {
        ctx.shadowColor = txtStyle.shadowColor || 'rgba(0,0,0,0.3)'
        ctx.shadowBlur = 5
        ctx.shadowOffsetX = 1
        ctx.shadowOffsetY = 1
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
    const maxWidth = w - 40
    const lineHeight = 35

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
            ctx.lineWidth = 1.5
            ctx.strokeStyle = txtStyle.stroke
            ctx.strokeText(lines[i], w / 2, startY + (i * lineHeight))
        }
        ctx.fillText(lines[i], w / 2, startY + (i * lineHeight))
    }
}

const renderAll = async () => {
    await nextTick()
    coverStyles.forEach((cover, cIndex) => {
        textStyles.forEach((text, tIndex) => {
            const canvas = canvasRefs.value[`${cIndex}-${tIndex}`]
            if (canvas) {
                const ctx = canvas.getContext('2d')
                ctx.clearRect(0, 0, canvas.width, canvas.height)
                drawCanvasContent(ctx, canvas.width, canvas.height, testText.value, cover, text)
            }
        })
    })
}

onMounted(() => {
    renderAll()
})
</script>

<style scoped>
.test-canvas-container {
    padding: 20px;
    background: #f0f2f5;
    min-height: 100vh;
}
.canvas-grid {
    display: flex;
    flex-direction: column;
    gap: 30px;
}
.style-row {
    background: white;
    padding: 20px;
    border-radius: 8px;
}
.style-variants {
    display: flex;
    flex-wrap: wrap;
    gap: 20px;
    margin-top: 10px;
}
.canvas-wrapper {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
}
.label {
    font-size: 12px;
    color: #666;
}
canvas {
    border: 1px solid #eee;
    box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1);
}
</style>
