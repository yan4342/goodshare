import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Login from '../views/Login.vue'
import PostDetail from '../views/PostDetail.vue'
import Publish from '../views/Publish.vue'
import Notification from '../views/Notification.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/post/:id',
    name: 'PostDetail',
    component: PostDetail
  },
  {
    path: '/publish',
    name: 'Publish',
    component: Publish
  },
  {
    path: '/notifications',
    name: 'Notification',
    component: Notification
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
