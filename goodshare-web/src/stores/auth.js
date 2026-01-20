import { reactive, readonly } from 'vue'
import request from '../utils/request'

const state = reactive({
  user: null,
  isAuthenticated: !!localStorage.getItem('token'),
  token: localStorage.getItem('token')
})

const setUser = (user) => {
  state.user = user
}

const setToken = (token, rememberMe = false) => {
  state.token = token
  state.isAuthenticated = !!token
  if (token) {
      localStorage.setItem('token', token)
  } else {
      localStorage.removeItem('token')
  }
}

const logout = () => {
    setToken(null)
    setUser(null)
}

const fetchUser = async () => {
    if (!state.token) return
    try {
        const res = await request.get('/profile')
        setUser(res.data)
    } catch (err) {
        console.error('Failed to fetch user profile', err)
        // Optionally logout if token is invalid, but interceptor handles 401
    }
}

export default {
  state: readonly(state),
  setUser,
  setToken,
  logout,
  fetchUser
}
