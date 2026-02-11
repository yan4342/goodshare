<template>
  <Sidebar v-if="showSidebar" />
  <router-view v-slot="{ Component }">
    <transition name="fade-transform" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<script setup>
import { onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import authStore from './stores/auth'
import Sidebar from './components/Sidebar.vue'

const route = useRoute()

const showSidebar = computed(() => {
  // Show sidebar if user is authenticated and not on guest pages (Login/Register)
  // Also check if the route explicitly hides sidebar (optional)
  const isGuestPage = ['Login', 'Register', 'AdminLogin'].includes(route.name)
  const isAdminPage = route.path.startsWith('/admin')
  return authStore.state.isAuthenticated && !isGuestPage && !isAdminPage
})

onMounted(() => {
    if (authStore.state.isAuthenticated && !authStore.state.user) {
        authStore.fetchUser()
    }
})
</script>

<style>
#app {
  width: 100%;
  min-height: 100vh;
}

/* Global Page Transition */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: opacity 0.3s ease-in-out, transform 0.3s ease-in-out;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* Global Overrides for Element Plus Transitions to match 300ms ease-in-out */
.el-fade-in-enter-active,
.el-fade-in-leave-active,
.el-fade-in-linear-enter-active,
.el-fade-in-linear-leave-active,
.el-zoom-in-center-enter-active,
.el-zoom-in-center-leave-active,
.el-zoom-in-top-enter-active,
.el-zoom-in-top-leave-active,
.el-zoom-in-bottom-enter-active,
.el-zoom-in-bottom-leave-active {
    transition-duration: 0.3s !important;
    transition-timing-function: ease-in-out !important;
}

/* Tabs transition */
.el-tabs__content {
    transition: all 0.3s ease-in-out;
}
</style>
