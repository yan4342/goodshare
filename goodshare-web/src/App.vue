<template>
  <router-view></router-view>
</template>

<script setup>
import { onMounted } from 'vue'
import authStore from './stores/auth'

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
</style>
