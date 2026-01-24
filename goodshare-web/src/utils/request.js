import axios from 'axios'
import { ElMessage } from 'element-plus'

let router = null

export const setRouter = (r) => {
    router = r
}

const service = axios.create({
  baseURL: '/api', // Proxy is set in vite.config.js
  timeout: 60000 // Increased timeout for crawler requests
})

// Request interceptor
service.interceptors.request.use(
  config => {
    let token = localStorage.getItem('token')
    // Use admin token for admin endpoints or if explicitly requested
    if (config.url.startsWith('/admin') || config.url.startsWith('/api/admin') || config._isAdmin) {
        const adminToken = localStorage.getItem('admin_token')
        if (adminToken) {
            token = adminToken
        }
    }

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
            // Check if it was an admin request
            const isAdminRequest = error.config && (
                error.config.url?.includes('/admin/') || 
                error.config.url?.startsWith('/admin') || 
                error.config.url?.startsWith('/api/admin') || 
                error.config._isAdmin
            )

            if (isAdminRequest) {
                ElMessage.error('管理员登录已过期，请重新登录')
                localStorage.removeItem('admin_token')
                if (router) {
                    router.push('/admin/login')
                } else {
                    window.location.href = '/admin/login'
                }
            } else {
                ElMessage.error('登录已过期，请重新登录')
                localStorage.removeItem('token')
                if (router) {
                    router.push('/login')
                } else {
                    window.location.href = '/login'
                }
            }
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
