import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import Publish from '../views/Publish.vue'
import Notification from '../views/Notification.vue'
import PostDetail from '../views/PostDetail.vue'
import authStore from '../stores/auth'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { guest: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: { guest: true }
  },
  {
    path: '/publish',
    name: 'Publish',
    component: Publish,
    meta: { requiresAuth: true }
  },
  {
    path: '/notification',
    name: 'Notification',
    component: Notification,
    meta: { requiresAuth: true }
  },
  {
    path: '/post/:id',
    name: 'PostDetail',
    component: PostDetail
  },
  {
    path: '/search',
    name: 'Search',
    component: () => import('../views/Search.vue')
  },
  {
    path: '/price-compare',
    name: 'PriceCompare',
    component: () => import('../views/PriceCompare.vue')
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('../views/Chat.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/me',
    name: 'Me',
    component: () => import('../views/Me.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/user/:id',
    name: 'UserDetail',
    component: () => import('../views/Me.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('../views/admin/AdminLogin.vue'),
    meta: { guest: true }
  },
  {
    path: '/admin/tags',
    name: 'AdminTags',
    component: () => import('../views/admin/AdminTagManager.vue'),
    meta: { requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

router.beforeEach((to, from, next) => {
    const isAuthenticated = authStore.state.isAuthenticated
    const isAdminAuthenticated = !!localStorage.getItem('admin_token')
    
    if (to.meta.requiresAdmin) {
        if (!isAdminAuthenticated) {
            next('/admin/login')
        } else {
            next()
        }
    } else if (to.meta.requiresAuth && !isAuthenticated) {
        next('/login')
    } else if (to.meta.guest && isAuthenticated) {
        // If already logged in and trying to access guest page
        // If it's admin login, allow it (will handle logout in component)
        if (to.path === '/admin/login') {
            if (isAdminAuthenticated) {
                next('/admin/tags')
            } else {
                next()
            }
        } else {
            next('/')
        }
    } else {
        next()
    }
})

export default router
