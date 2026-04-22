import { reactive, readonly } from 'vue'
import request from '../utils/request'

const state = reactive({
  user: null,
  isAuthenticated: !!localStorage.getItem('token'),
  token: localStorage.getItem('token'),
  refreshToken: localStorage.getItem('refreshToken')
})

const setUser = (user) => {
  state.user = user
  if (user && user.username) {
      localStorage.setItem('username', user.username)
  }
}

const setToken = (token, refreshToken = null) => {
  state.token = token
  state.isAuthenticated = !!token
  if (token) {
      localStorage.setItem('token', token)
  } else {
      localStorage.removeItem('token')
  }
  
  if (refreshToken) {
      state.refreshToken = refreshToken
      localStorage.setItem('refreshToken', refreshToken)
  } else if (token === null) {
      state.refreshToken = null
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('username')
  }
}

const logout = async () => {
    try {
        if (state.token) {
            await request.post('/auth/logout')
        }
    } catch (e) {
        console.error('Logout error', e)
    } finally {
        setToken(null)
        setUser(null)
    }
}

const fetchUser = async () => {
    if (!state.token) return
    try {
        const res = await request.get('/profile')
        setUser(res.data)
    } catch (err) {
        console.error('Failed to fetch user profile', err)
        // Interceptor handles 401 and refresh
    }
}

export default {
  state: readonly(state),
  setUser,
  setToken,
  logout,
  fetchUser
}
