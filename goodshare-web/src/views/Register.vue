<template>
  <div class="register-container">
    <el-card class="register-card">
      <div class="header">
        <h2 class="title">GoodShare</h2>
        <p class="subtitle">欢迎加入，开启分享之旅</p>
      </div>
      
      <el-form 
        ref="registerFormRef"
        :model="registerForm" 
        :rules="registerRules"
        label-width="0px"
        class="register-form"
      >
        <el-form-item prop="username">
          <el-input 
            v-model="registerForm.username" 
            placeholder="用户名 (3-50字符)" 
            :prefix-icon="User" 
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="email">
          <el-input 
            v-model="registerForm.email" 
            placeholder="邮箱" 
            :prefix-icon="Message" 
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input 
            v-model="registerForm.password" 
            type="password" 
            placeholder="设置密码 (至少6位)" 
            :prefix-icon="Lock" 
            size="large"
            show-password
            @input="checkPasswordStrength"
          />
        </el-form-item>
        
        <!-- Password Strength Meter -->
        <div class="strength-meter" v-if="registerForm.password">
            <div class="meter-bar">
                <div class="bar-fill" :class="strengthClass" :style="{ width: strengthWidth }"></div>
            </div>
            <span class="strength-text">{{ strengthText }}</span>
        </div>
        
        <el-form-item prop="confirmPassword">
          <el-input 
            v-model="registerForm.confirmPassword" 
            type="password" 
            placeholder="确认密码" 
            :prefix-icon="Lock" 
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item prop="captcha" class="captcha-item">
             <el-input 
                v-model="registerForm.captcha" 
                placeholder="验证码" 
                size="large"
             />
             <el-button class="captcha-btn" :disabled="captchaTimer > 0" @click="sendCaptcha">
                 {{ captchaTimer > 0 ? `${captchaTimer}s后重试` : '获取验证码' }}
             </el-button>
        </el-form-item>

        <el-button 
            type="primary" 
            class="register-btn" 
            @click="handleRegister" 
            :loading="loading"
            size="large"
            round
        >
            立即注册
        </el-button>
      </el-form>
      
      <div class="footer-links">
        <span>已有账号？</span>
        <el-link type="primary" @click="$router.push('/login')">直接登录</el-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { User, Lock, Message } from '@element-plus/icons-vue'
import request from '../utils/request'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const registerFormRef = ref(null)
const loading = ref(false)
const captchaTimer = ref(0)

const registerForm = reactive({ 
    username: '', 
    email: '',
    password: '',
    confirmPassword: '',
    captcha: '' // Mock captcha
})

const validatePass = (rule, value, callback) => {
    if (value === '') {
        callback(new Error('请输入密码'))
    } else {
        if (registerForm.confirmPassword !== '') {
            if (!registerFormRef.value) return
            registerFormRef.value.validateField('confirmPassword')
        }
        callback()
    }
}

const validatePass2 = (rule, value, callback) => {
    if (value === '') {
        callback(new Error('请再次输入密码'))
    } else if (value !== registerForm.password) {
        callback(new Error('两次输入密码不一致!'))
    } else {
        callback()
    }
}

const registerRules = {
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, max: 50, message: '长度在 3 到 50 个字符', trigger: 'blur' }
    ],
    email: [
        { required: true, message: '请输入邮箱地址', trigger: 'blur' },
        { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
    ],
    password: [
        { required: true, validator: validatePass, trigger: 'blur' },
        { min: 6, message: '密码长度至少6位', trigger: 'blur' }
    ],
    confirmPassword: [
        { required: true, validator: validatePass2, trigger: 'blur' }
    ],
    captcha: [
         { required: true, message: '请输入验证码', trigger: 'blur' }
    ]
}

// Password Strength Logic
const strengthScore = ref(0)
const checkPasswordStrength = (val) => {
    let score = 0
    if (!val) {
        strengthScore.value = 0
        return
    }
    if (val.length >= 6) score += 1
    if (val.length >= 10) score += 1
    if (/[A-Z]/.test(val)) score += 1
    if (/[0-9]/.test(val)) score += 1
    if (/[^A-Za-z0-9]/.test(val)) score += 1
    strengthScore.value = score
}

const strengthClass = computed(() => {
    if (strengthScore.value <= 2) return 'weak'
    if (strengthScore.value <= 4) return 'medium'
    return 'strong'
})

const strengthWidth = computed(() => {
    if (strengthScore.value === 0) return '0%'
    return (strengthScore.value / 5) * 100 + '%'
})

const strengthText = computed(() => {
    if (strengthScore.value <= 2) return '弱'
    if (strengthScore.value <= 4) return '中'
    return '强'
})

// Mock Captcha Sending
const sendCaptcha = () => {
    if (!registerForm.email) {
        ElMessage.warning('请先输入邮箱')
        return
    }
    // Simulate sending
    ElMessage.success(`验证码已发送至 ${registerForm.email} (模拟: 123456)`)
    captchaTimer.value = 60
    const interval = setInterval(() => {
        captchaTimer.value--
        if (captchaTimer.value <= 0) {
            clearInterval(interval)
        }
    }, 1000)
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  
  await registerFormRef.value.validate(async (valid) => {
      if (valid) {
          if (registerForm.captcha !== '123456') { // Mock verification
               ElMessage.error('验证码错误 (测试请用 123456)')
               return
          }

          loading.value = true
          try {
            await request.post('/auth/register', {
                username: registerForm.username,
                email: registerForm.email,
                password: registerForm.password
            })
            ElMessage.success('注册成功，请登录')
            router.push('/login')
          } catch (err) {
            // Error handled in interceptor
          } finally {
            loading.value = false
          }
      }
  })
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f5f5f5;
  background-image: url('https://ci.xiaohongshu.com/eb760777-62d1-4235-8664-984254b92497');
  background-size: cover;
  background-position: center;
}

.register-card {
  width: 450px;
  padding: 40px 30px;
  border-radius: 24px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.1);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
}

.header {
    text-align: center;
    margin-bottom: 30px;
}

.title {
  color: #ff2442;
  font-size: 32px;
  margin-bottom: 8px;
  font-family: 'Billabong', cursive, sans-serif;
  letter-spacing: -1px;
}

.subtitle {
    color: #999;
    font-size: 14px;
}

.register-form {
    margin-bottom: 20px;
}

.captcha-item :deep(.el-form-item__content) {
    display: flex;
    gap: 10px;
}

.captcha-btn {
    width: 120px;
}

.register-btn {
  width: 100%;
  background-color: #ff2442;
  border-color: #ff2442;
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 2px;
  margin-top: 10px;
}

.register-btn:hover {
    background-color: #e61e3a;
    border-color: #e61e3a;
}

.footer-links {
    margin-top: 20px;
    text-align: center;
    font-size: 14px;
    color: #666;
    display: flex;
    justify-content: center;
    gap: 5px;
}

/* Password Strength */
.strength-meter {
    margin-bottom: 18px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0 2px;
}
.meter-bar {
    flex: 1;
    height: 4px;
    background-color: #eee;
    border-radius: 2px;
    overflow: hidden;
}
.bar-fill {
    height: 100%;
    transition: all 0.3s;
}
.bar-fill.weak { background-color: #f56c6c; }
.bar-fill.medium { background-color: #e6a23c; }
.bar-fill.strong { background-color: #67c23a; }

.strength-text {
    font-size: 12px;
    color: #999;
    width: 20px;
}
</style>
