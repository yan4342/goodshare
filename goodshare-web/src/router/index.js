import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import Publish from '../views/Publish.vue'
import Notification from '../views/Notification.vue'
import PostDetail from '../views/PostDetail.vue'
import { useAuthStore } from '../stores/auth'

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
    component: () => import('../views/admin/AdminManager.vue'),
    meta: { requiresAdmin: true }
  },
  {
    path: '/appraisals',
    name: 'AppraisalList',
    component: () => import('../views/AppraisalList.vue')
  },
  {
    path: '/appraisals/create',
    name: 'AppraisalCreate',
    component: () => import('../views/AppraisalCreate.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/appraisals/:id',
    name: 'AppraisalDetail',
    component: () => import('../views/AppraisalDetail.vue')
  },
  {
    path: '/admin/appraisals',
    name: 'AdminAppraisals',
    component: () => import('../views/admin/AdminAppraisalManager.vue'),
    meta: { requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

router.beforeEach((to, from, next) => {
    const authStore = useAuthStore()
    const isAuthenticated = authStore.isAuthenticated
    const isAdminAuthenticated = !!localStorage.getItem('admin_token')
    
    // 1. Admin Routes Protection
    if (to.meta.requiresAdmin) {
        if (!isAdminAuthenticated) {
            next('/admin/login')
        } else {
            next()
        }
        return
    }

    // 2. Admin Login Auto-Redirect (Better UX)
    // If already logged in as admin, redirect to dashboard
    if (to.path === '/admin/login' && isAdminAuthenticated) {
        next('/admin/tags')
        return
    }

    // 3. Regular Auth Protection
    if (to.meta.requiresAuth && !isAuthenticated) {
        next('/login')
        return
    } 
    
    // 4. Guest Pages (Login/Register) for Regular Users
    if (to.meta.guest && isAuthenticated) {
        // Allow access to admin login even if logged in as user
        if (to.path === '/admin/login') {
            next()
        } else {
            next('/')
        }
        return
    }

    next()
})

export default router
