import { reactive, readonly } from 'vue'

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

export default {
  state: readonly(state),
  setUser,
  setToken,
  logout
}
