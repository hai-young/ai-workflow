<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

onMounted(() => {
  const authStore = useAuthStore()
  authStore.initialize()
})
</script>

<template>
  <div id="app">
    <RouterView />
  </div>
</template>

<style lang="scss">
@import '@/styles/dark-neon.css';
</style>
