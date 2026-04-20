import axios from 'axios'
import { ElMessage } from 'element-plus'

let router = null

// 用于防止重复显示登录提示
let lastLoginPromptTime = 0
const LOGIN_PROMPT_THROTTLE_MS = 1000 // 1秒内只显示一次登录提示

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
            // 检查是否是登录请求
            const isLoginRequest = error.config && (
                error.config.url?.includes('/auth/login') || 
                error.config.url?.includes('/admin/login')
            )
            
            if (isLoginRequest) {
                // 如果设置了跳过错误消息，则直接返回
                if (error.config?._skipErrorMessage) {
                    return Promise.reject(error)
                }
                // 登录失败，显示具体的错误信息
                const isAdminLogin = error.config._isAdmin || error.config.url?.includes('/admin/login') || error.config.url?.includes('/api/admin')
                if (isAdminLogin) {
                    ElMessage.error('管理员用户名或密码错误')
                } else {
                    ElMessage.error('用户名或密码错误')
                }
                return Promise.reject(error)
            }
            
            // 否则按原来的逻辑处理（令牌过期）
            // Check if it was an admin request
            const isAdminRequest = error.config && (
                error.config.url?.includes('/admin/') || 
                error.config.url?.startsWith('/admin') || 
                error.config.url?.startsWith('/api/admin') || 
                error.config._isAdmin
            )

            // 检查请求是否包含了Authorization头
            const hadAuthorization = error.config?.headers?.Authorization || 
                                   error.config?.headers?.authorization ||
                                   (error.config && error.config.headers && 
                                    Object.keys(error.config.headers).some(key => 
                                        key.toLowerCase() === 'authorization'))

            if (hadAuthorization) {
                // 有Authorization头，说明token已过期
                const now = Date.now()
                const shouldShowPrompt = now - lastLoginPromptTime > LOGIN_PROMPT_THROTTLE_MS
                
                if (shouldShowPrompt) {
                    lastLoginPromptTime = now
                    if (isAdminRequest) {
                        ElMessage.error('管理员登录已过期，请重新登录')
                    } else {
                        ElMessage.error('登录已过期，请重新登录')
                    }
                }
                
                // 总是执行清理和跳转逻辑，但可能没有显示提示
                if (isAdminRequest) {
                    localStorage.removeItem('admin_token')
                    if (router) {
                        router.push('/admin/login')
                    } else {
                        window.location.href = '/admin/login'
                    }
                } else {
                    localStorage.removeItem('token')
                    if (router) {
                        router.push('/login')
                    } else {
                        window.location.href = '/login'
                    }
                }
            } else {
                // 没有Authorization头，说明用户未登录
                const now = Date.now()
                const shouldShowPrompt = now - lastLoginPromptTime > LOGIN_PROMPT_THROTTLE_MS
                
                if (shouldShowPrompt) {
                    lastLoginPromptTime = now
                    if (isAdminRequest) {
                        ElMessage.error('请先登录管理员账号')
                    } else {
                        ElMessage.error('请先登录')
                    }
                }
                
                // 总是执行跳转逻辑，但可能没有显示提示
                if (isAdminRequest) {
                    if (router) {
                        router.push('/admin/login')
                    } else {
                        window.location.href = '/admin/login'
                    }
                } else {
                    if (router) {
                        router.push('/login')
                    } else {
                        window.location.href = '/login'
                    }
                }
            }
            return Promise.reject(error)
        } else if (status === 403) {
            message = '没有权限执行此操作'
        }
    }
    if (error.config?._skipErrorMessage) {
        return Promise.reject(error)
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service
