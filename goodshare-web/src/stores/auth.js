import { defineStore } from 'pinia'
import request from '../utils/request'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    isAuthenticated: !!localStorage.getItem('token'),
    token: localStorage.getItem('token'),
    refreshToken: localStorage.getItem('refreshToken')
  }),

  actions: {
    setUser(user) {
      this.user = user
      if (user && user.username) {
        localStorage.setItem('username', user.username)
      }
    },

    setToken(token, refreshToken = null) {
      this.token = token
      this.isAuthenticated = !!token
      if (token) {
        localStorage.setItem('token', token)
      } else {
        localStorage.removeItem('token')
      }

      if (refreshToken) {
        this.refreshToken = refreshToken
        localStorage.setItem('refreshToken', refreshToken)
      } else if (token === null) {
        this.refreshToken = null
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('username')
      }
    },

    async logout() {
      try {
        if (this.token) {
          await request.post('/auth/logout')
        }
      } catch (e) {
        console.error('Logout error', e)
      } finally {
        this.setToken(null)
        this.setUser(null)
      }
    },

    async fetchUser() {
      if (!this.token) return
      try {
        const res = await request.get('/profile')
        this.setUser(res.data)
      } catch (err) {
        console.error('Failed to fetch user profile', err)
      }
    }
  }
})
