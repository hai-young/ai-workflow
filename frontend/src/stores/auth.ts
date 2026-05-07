import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo, LoginRequest, RegisterRequest } from '@/types/auth'
import { login as loginApi, register as registerApi, logout as logoutApi, getUserInfo } from '@/api/auth'
import { message } from 'ant-design-vue'

const IDLE_TIMEOUT = 30 * 60 * 1000 // 30 minutes
const TOKEN_CHECK_INTERVAL = 60 * 1000 // check every minute

function decodeJwtPayload(token: string): { exp?: number } | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = JSON.parse(atob(parts[1]))
    return payload
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token') || null)
  const userInfo = ref<UserInfo | null>(null)
  const isAuthenticated = ref<boolean>(!!token.value)

  let idleTimer: ReturnType<typeof setTimeout> | null = null
  let tokenCheckTimer: ReturnType<typeof setInterval> | null = null

  const username = computed(() => userInfo.value?.username || '')
  const role = computed(() => userInfo.value?.role || 'ROLE_USER')
  const isAdmin = computed(() => role.value === 'ROLE_ADMIN')

  function resetIdleTimer() {
    if (idleTimer) clearTimeout(idleTimer)
    idleTimer = setTimeout(() => {
      message.warning('您已长时间未操作，即将自动退出登录')
      performLogout()
    }, IDLE_TIMEOUT)
  }

  function startSessionTracking() {
    resetIdleTimer()
    const events = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart', 'click']
    events.forEach(e => window.addEventListener(e, resetIdleTimer, { passive: true }))

    if (tokenCheckTimer) clearInterval(tokenCheckTimer)
    tokenCheckTimer = setInterval(() => {
      if (token.value) {
        const payload = decodeJwtPayload(token.value)
        if (payload?.exp && payload.exp * 1000 < Date.now()) {
          message.warning('登录已过期，请重新登录')
          performLogout()
        }
      }
    }, TOKEN_CHECK_INTERVAL)
  }

  function stopSessionTracking() {
    if (idleTimer) {
      clearTimeout(idleTimer)
      idleTimer = null
    }
    if (tokenCheckTimer) {
      clearInterval(tokenCheckTimer)
      tokenCheckTimer = null
    }
    const events = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart', 'click']
    events.forEach(e => window.removeEventListener(e, resetIdleTimer))
  }

  function performLogout() {
    stopSessionTracking()
    token.value = null
    userInfo.value = null
    isAuthenticated.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('remembered-username')
    if (window.location.pathname !== '/login') {
      // Lazy import to avoid circular dependency with router
      import('@/router').then(m => m.default.push('/login'))
    }
  }

  async function login(data: LoginRequest) {
    try {
      const response = await loginApi(data)

      if (response.success && response.data) {
        token.value = response.data.token
        userInfo.value = response.data.userInfo || null
        isAuthenticated.value = true

        localStorage.setItem('token', response.data.token)
        if (data.rememberMe) {
          localStorage.setItem('remembered-username', data.username)
        }

        // Fetch full user info after login
        await refreshUserInfo()
        startSessionTracking()

        message.success('登录成功')
        return true
      } else {
        message.error('登录失败')
        return false
      }
    } catch (error: any) {
      console.error('Login error:', error)
      message.error(error.message || '登录失败')
      return false
    }
  }

  async function register(data: RegisterRequest) {
    try {
      const response = await registerApi(data)

      if (response.success) {
        message.success('注册成功，请登录')
        return true
      } else {
        message.error(response.error || '注册失败')
        return false
      }
    } catch (error: any) {
      console.error('Register error:', error)
      message.error(error.message || '注册失败')
      return false
    }
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (error) {
      console.error('Logout error:', error)
    } finally {
      performLogout()
    }
  }

  async function refreshUserInfo() {
    try {
      const response = await getUserInfo()
      if (response.success && response.data) {
        userInfo.value = response.data
        return response.data
      }
    } catch (error) {
      console.error('Refresh user info error:', error)
    }
    return null
  }

  function clearRememberedUsername() {
    localStorage.removeItem('remembered-username')
  }

  function initialize() {
    const rememberedUsername = localStorage.getItem('remembered-username')
    if (rememberedUsername) {
      token.value = localStorage.getItem('token') || null
      isAuthenticated.value = !!token.value
    }

    if (token.value) {
      const payload = decodeJwtPayload(token.value)
      if (payload?.exp && payload.exp * 1000 < Date.now()) {
        performLogout()
        return
      }
      startSessionTracking()
      refreshUserInfo()
    }
  }

  return {
    token,
    userInfo,
    isAuthenticated,
    username,
    role,
    isAdmin,
    login,
    register,
    logout,
    refreshUserInfo,
    clearRememberedUsername,
    initialize,
    startSessionTracking,
    stopSessionTracking
  }
})
