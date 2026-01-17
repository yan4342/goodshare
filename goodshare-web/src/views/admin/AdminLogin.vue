<template>
  <div class="admin-login-container">
    <div class="admin-login-card">
      <h2>管理员登录</h2>
      <el-form :model="form" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入管理员账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-button type="primary" class="login-btn" @click="handleLogin" :loading="loading">登录</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import authStore from '../../stores/auth'

const router = useRouter()
const loading = ref(false)

const form = ref({
  username: '',
  password: ''
})

onMounted(() => {
    // If user is already logged in, log them out to ensure clean admin login
    if (authStore.state.isAuthenticated) {
        authStore.logout()
        ElMessage.info('已自动登出当前账号，请登录管理员账号')
    }
})

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }

  loading.value = true
  try {
    const res = await request.post('/auth/login', {
        username: form.value.username,
        password: form.value.password
    })
    
    // Validate response
    const data = res.data
    if (!data || !data.accessToken) {
        throw new Error('Invalid response')
    }

    authStore.setToken(data.accessToken)
    // Manually construct user object since backend might only return token
    authStore.setUser({ username: form.value.username, role: 'ADMIN' }) 
    
    ElMessage.success('管理员登录成功')
    
    // Force navigation
    setTimeout(() => {
        router.push('/admin/tags').catch(err => {
            console.error('Navigation failed:', err)
            ElMessage.error('页面跳转失败')
        })
    }, 100)
    
  } catch (error) {
    console.error(error)
    ElMessage.error('登录失败，请检查账号密码或权限')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.admin-login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f0f2f5;
}

.admin-login-card {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

h2 {
  text-align: center;
  margin-bottom: 30px;
  color: #333;
}

.login-btn {
  width: 100%;
  margin-top: 20px;
}
</style>