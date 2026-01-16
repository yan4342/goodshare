import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const service = axios.create({
  baseURL: '/api', // Proxy is set in vite.config.js
  timeout: 5000
})

// Request interceptor
service.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  error => {
    console.log(error)
    return Promise.reject(error)
  }
)

// Response interceptor
service.interceptors.response.use(
  response => {
    return response
  },
  error => {
    console.log('err' + error)
    let message = error.message || 'Error'
    if (error.response) {
        const status = error.response.status
        const data = error.response.data
        message = typeof data === 'string' ? data : (data.message || message)

        if (status === 401) {
            ElMessage.error('登录已过期，请重新登录')
            localStorage.removeItem('token')
            router.push('/login')
            return Promise.reject(error)
        } else if (status === 403) {
            message = '没有权限执行此操作'
        }
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service
