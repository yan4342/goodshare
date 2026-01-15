<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="title">GoodShare</h2>
      <el-form :model="loginForm" label-width="0px">
        <el-form-item>
          <el-input v-model="loginForm.username" placeholder="Username" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" type="password" placeholder="Password" :prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" class="login-btn" @click="handleLogin" :loading="loading">登录</el-button>
      </el-form>
      <div class="footer-links">
        <el-link type="info">注册账号</el-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loginForm = ref({ username: '', password: '' })
const loading = ref(false)

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
      ElMessage.warning('请输入用户名和密码')
      return
  }
  loading.value = true
  try {
    const res = await axios.post('/api/auth/login', loginForm.value)
    // Assuming res.data.accessToken based on AuthResponse
    localStorage.setItem('token', res.data.accessToken)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (err) {
    ElMessage.error(err.response?.data || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #fff;
  background-image: url('https://ci.xiaohongshu.com/eb760777-62d1-4235-8664-984254b92497'); /* Optional: background image */
  background-size: cover;
}
.login-card {
  width: 400px;
  padding: 40px;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
}
.title {
  text-align: center;
  color: #ff2442;
  margin-bottom: 30px;
  font-family: sans-serif;
}
.login-btn {
  width: 100%;
  background-color: #ff2442;
  border-color: #ff2442;
  height: 40px;
  font-size: 16px;
  margin-top: 10px;
}
.login-btn:hover {
    background-color: #e61e3a;
    border-color: #e61e3a;
}
.footer-links {
    margin-top: 20px;
    text-align: center;
}
</style>
