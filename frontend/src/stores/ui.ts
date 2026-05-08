import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const theme = ref<'dark' | 'light'>(
    (localStorage.getItem('theme') as 'dark' | 'light') || 'dark'
  )
  const sidebarOpen = ref(false)
  const settingsDrawerOpen = ref(false)

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem('theme', theme.value)
    document.documentElement.setAttribute('data-theme', theme.value)
  }

  function toggleSidebar() {
    sidebarOpen.value = !sidebarOpen.value
  }

  function openSettings() {
    settingsDrawerOpen.value = true
  }

  function closeSettings() {
    settingsDrawerOpen.value = false
  }

  return {
    theme,
    sidebarOpen,
    settingsDrawerOpen,
    toggleTheme,
    toggleSidebar,
    openSettings,
    closeSettings,
  }
})
