<template>
  <div class="login-container">
    <el-card class="login-card">
      <div class="header">
        <h2 class="title">GoodShare</h2>
        <p class="subtitle">发现美好，分享生活</p>
      </div>
      
      <el-form 
        ref="loginFormRef"
        :model="loginForm" 
        :rules="loginRules"
        label-width="0px"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input 
            v-model="loginForm.username" 
            placeholder="用户名" 
            :prefix-icon="User" 
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input 
            v-model="loginForm.password" 
            type="password" 
            placeholder="密码" 
            :prefix-icon="Lock" 
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <div class="form-options">
            <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
            <el-link type="primary" :underline="false">忘记密码？</el-link>
        </div>

        <el-button 
            type="primary" 
            class="login-btn" 
            @click="handleLogin" 
            :loading="loading"
            size="large"
            round
        >
            登录
        </el-button>
      </el-form>
      
      <div class="footer-links">
        <span>还没有账号？</span>
        <el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import request from '../utils/request'
import authStore from '../stores/auth'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({ 
    username: '', 
    password: '',
    rememberMe: false
})

const loginRules = {
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, message: '密码长度不能小于6位', trigger: 'blur' }
    ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate(async (valid) => {
      if (valid) {
          loading.value = true
          try {
            const res = await request.post('/auth/login', {
                username: loginForm.username,
                password: loginForm.password
            })
            
            authStore.setToken(res.data.accessToken, res.data.refreshToken)
            await authStore.fetchUser()
            ElMessage.success('登录成功')
            router.push('/')
          } catch (err) {
            // Error handling is done in interceptor
          } finally {
            loading.value = false
          }
      }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: var(--bg-color);
  background-image: url('../assets/background.svg');
  background-size: contain;
  background-position: center;
  transition: background-color 0.3s;
}

.login-card {
  width: 400px;
  padding: 40px 30px;
  border-radius: 24px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.1);
  background: var(--bg-color-overlay);
  backdrop-filter: blur(10px);
  transition: background-color 0.3s;
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
    color: var(--text-color-secondary);
    font-size: 14px;
}

.login-form {
    margin-bottom: 20px;
}

.form-options {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.login-btn {
  width: 100%;
  background-color: #ff2442;
  border-color: #ff2442;
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 2px;
}

.login-btn:hover {
    background-color: #e61e3a;
    border-color: #e61e3a;
}

.footer-links {
    margin-top: 20px;
    text-align: center;
    font-size: 14px;
    color: var(--text-color-secondary);
    display: flex;
    justify-content: center;
    gap: 5px;
}
</style>
