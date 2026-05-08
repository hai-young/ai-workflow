<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { darkTheme, lightTheme } from '@/theme/antd-theme'

onMounted(() => {
  const authStore = useAuthStore()
  authStore.initialize()

  // Apply initial theme attribute
  const uiStore = useUiStore()
  document.documentElement.setAttribute('data-theme', uiStore.theme)
})

const uiStore = useUiStore()
const currentTheme = computed(() => uiStore.theme === 'dark' ? darkTheme : lightTheme)
</script>

<template>
  <a-config-provider :theme="currentTheme">
    <RouterView />
  </a-config-provider>
</template>

<style lang="scss">
@import '@/styles/dark-neon.css';
</style>
