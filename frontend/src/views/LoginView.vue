<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { sendVerifyCode, verifyCode } from '@/api/verify'
import { login, phoneLogin } from '@/api/auth'
import { message } from 'ant-design-vue'
import { LockOutlined, UserOutlined, PhoneOutlined, MailOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

// Page state
const showLoginForm = ref(true)

// Login form state
const loginForm = ref({
  username: '',
  password: ''
})

// Phone login form state
const phoneLoginForm = ref({
  phone: '',
  code: ''
})

// Remember password
const rememberMe = ref(false)

// Loading state
const loading = ref(false)
const countDown = ref(0)

// Global error handler to catch any errors
window.addEventListener('error', (event) => {
  console.error('Global error:', event.error)
})

window.addEventListener('unhandledrejection', (event) => {
  console.error('Unhandled promise rejection:', event.reason)
})

// Methods
function handleLogin() {
  console.log('=== handleLogin called ===')

  console.log('Form values:', loginForm.value)
  console.log('Auth store:', authStore)

  if (!loginForm.value.username || !loginForm.value.password) {
    console.log('Validation failed: username or password is empty')
    message.warning('请输入用户名和密码')
    return
  }

  console.log('Starting login process...')
  loading.value = true

  authStore.login({
    username: loginForm.value.username,
    password: loginForm.value.password,
    rememberMe: rememberMe.value
  }).then((success) => {
    console.log('Login result:', success)
    if (success) {
      console.log('Login successful, redirecting...')
      router.push('/dashboard')
    } else {
      console.log('Login failed')
    }
  }).catch((error) => {
    console.error('Login error:', error)
    message.error('登录失败，请检查控制台')
  }).finally(() => {
    console.log('Finally block executed')
    loading.value = false
  })
}

const handlePhoneLogin = async () => {
  console.log('handlePhoneLogin called')
  console.log('Phone form values:', phoneLoginForm.value)

  if (!phoneLoginForm.value.phone || !phoneLoginForm.value.code) {
    message.warning('请输入手机号和验证码')
    return
  }

  loading.value = true
  try {
    // First verify the code
    const verifyResult = await verifyCode(phoneLoginForm.value.phone, phoneLoginForm.value.code, 'login')
    console.log('Verification result:', verifyResult)

    if (!verifyResult) {
      message.error('验证码无效或已过期')
      loading.value = false
      return
    }

    // Then login using phone number
    const response = await phoneLogin(phoneLoginForm.value.phone, phoneLoginForm.value.code)

    console.log('Phone login response:', response)
    console.log('Response keys:', response ? Object.keys(response) : 'no response')

    if (response && response.data && response.data.token) {
      console.log('Token found:', response.data.token)
      message.success('登录成功')
      // Store token
      localStorage.setItem('token', response.data.token)
      localStorage.setItem('remembered-phone', phoneLoginForm.value.phone)

      // Update auth store
      const authStore = useAuthStore()
      authStore.token = response.data.token
      authStore.isAuthenticated = true
      console.log('Auth store updated:', authStore.token, authStore.isAuthenticated)

      // Redirect to dashboard
      console.log('Navigating to dashboard...')
      await router.push('/dashboard')
      console.log('Navigation completed')
    } else {
      console.error('Invalid response:', response)
      message.error('登录失败，无效的响应')
    }
  } catch (error: any) {
    console.error('Phone login error:', error)
    message.error(error.message || '登录失败，请检查控制台')
  } finally {
    loading.value = false
  }
}

const handleRegister = () => {
  console.log('=== handleRegister called ===')
  console.log('router object:', router)
  console.log('router.push method:', typeof router.push)
  console.log('Current path:', router.currentRoute.value.path)

  try {
    router.push('/register').then(() => {
      console.log('✓ Navigation to /register successful')
    }).catch(err => {
      console.error('✗ Navigation failed:', err)
    })
  } catch (error) {
    console.error('✗ handleRegister error:', error)
  }
}

const handleForgotPassword = () => {
  router.push('/reset-password')
}

const handleEnterpriseLogin = () => {
  message.info('企业微信登录功能待实现')
}

const sendCode = async () => {
  if (!phoneLoginForm.value.phone) {
    message.warning('请输入手机号')
    return
  }

  // Simple phone number validation
  const phoneRegex = /^1[3-9]\d{9}$/
  if (!phoneRegex.test(phoneLoginForm.value.phone)) {
    message.warning('请输入正确的手机号')
    return
  }

  loading.value = true
  try {
    await sendVerifyCode({
      phone: phoneLoginForm.value.phone,
      type: 'login'
    })

    message.success('验证码已发送')
    countDown.value = 60

    const timer = setInterval(() => {
      countDown.value--
      if (countDown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error: any) {
    message.error(error.message || '发送验证码失败')
  } finally {
    loading.value = false
  }
}

// Initialize remembered username
onMounted(() => {
  console.log('=== LoginView mounted ===')
  console.log('authStore:', authStore)
  console.log('loginForm:', loginForm.value)
  console.log('loading:', loading.value)

  authStore.initialize()
  const rememberedUsername = localStorage.getItem('remembered-username')
  if (rememberedUsername) {
    loginForm.value.username = rememberedUsername
    rememberMe.value = true
  }

  // Test if the button can receive clicks
  console.log('Checking button clickability...')

  // Add a global click listener to test if any click works
  window.addEventListener('click', (e) => {
    console.log('Global click detected at:', e.target)
  }, true)
})
</script>

<template>
  <div class="login-container">
    <!-- Particle Background -->
    <div class="particle-background"></div>

    <!-- Main Content -->
    <div class="login-content">
      <div class="login-card glass-card">
        <!-- Logo and Title -->
        <div class="login-header">
          <div class="logo-container">
            <div class="logo-icon">
              <svg viewBox="0 0 64 64" fill="none">
                <circle cx="32" cy="32" r="30" stroke="url(#logoGradient)" stroke-width="4"/>
                <circle cx="32" cy="32" r="20" fill="url(#logoGradient)" opacity="0.3"/>
                <path d="M32 22V42M22 32H42" stroke="white" stroke-width="4" stroke-linecap="round"/>
                <defs>
                  <linearGradient id="logoGradient" x1="0" y1="0" x2="64" y2="64">
                    <stop offset="0%" stop-color="#8b5cf6"/>
                    <stop offset="100%" stop-color="#06b6d4"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
          </div>
          <h1 class="app-title">智能协作平台</h1>
          <p class="app-subtitle">Intelligent Collaboration Platform</p>
        </div>

        <!-- Login Tabs -->
        <div class="login-tabs">
          <div
            class="tab-item"
            :class="{ active: showLoginForm }"
            @click="showLoginForm = true"
          >
            账号登录
          </div>
          <div
            class="tab-item"
            :class="{ active: !showLoginForm }"
            @click="showLoginForm = false"
          >
            手机登录
          </div>
        </div>

        <!-- Login Form -->
        <div v-if="showLoginForm" class="login-form">
          <div class="form-group">
            <label class="form-label">用户名</label>
            <div class="input-wrapper">
              <UserOutlined class="input-icon" />
              <input
                v-model="loginForm.username"
                type="text"
                class="neon-input"
                placeholder="请输入用户名"
                @keyup.enter="handleLogin"
              />
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">密码</label>
            <div class="input-wrapper">
              <LockOutlined class="input-icon" />
              <input
                v-model="loginForm.password"
                type="password"
                class="neon-input"
                placeholder="请输入密码"
                @keyup.enter="handleLogin"
              />
            </div>
          </div>

          <div class="form-options">
            <label class="checkbox-label">
              <input
                v-model="rememberMe"
                type="checkbox"
                class="neon-checkbox"
              />
              记住密码
            </label>
            <a class="forgot-password" @click.prevent="handleForgotPassword">
              忘记密码？
            </a>
          </div>

          <button
            class="neon-button login-button"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </button>

          <div class="divider">
            <span class="divider-line"></span>
            <span class="divider-text">或</span>
            <span class="divider-line"></span>
          </div>

          <button class="neon-button neon-button-secondary enterprise-button" @click="handleEnterpriseLogin">
            <MailOutlined />
            企业微信登录
          </button>

          <div class="register-link">
            还没有账号？
            <button type="button" class="register-link-text" @click="handleRegister">立即注册</button>
          </div>
        </div>

        <!-- Phone Login Form -->
        <div v-else class="phone-login-form">
          <div class="form-group">
            <label class="form-label">手机号</label>
            <div class="input-wrapper">
              <PhoneOutlined class="input-icon" />
              <input
                v-model="phoneLoginForm.phone"
                type="tel"
                class="neon-input"
                placeholder="请输入手机号"
                maxlength="11"
                @keyup.enter="handlePhoneLogin"
              />
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">验证码</label>
            <div class="input-wrapper code-wrapper">
              <input
                v-model="phoneLoginForm.code"
                type="text"
                class="neon-input"
                placeholder="请输入验证码"
                maxlength="6"
                @keyup.enter="handlePhoneLogin"
              />
              <button
                class="neon-button neon-button-secondary code-button"
                :disabled="countDown > 0 || loading"
                @click="sendCode"
              >
                {{ countDown > 0 ? `${countDown}s 后重新获取` : '获取验证码' }}
              </button>
            </div>
          </div>

          <button
            class="neon-button login-button"
            :loading="loading"
            @click="handlePhoneLogin"
          >
            登 录
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-container {
  min-height: 100vh;
  background: var(--bg-gradient);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-md);
}

.particle-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.login-content {
  width: 100%;
  max-width: 420px;
  z-index: 1;
}

.login-card {
  padding: var(--spacing-xl);
  animation: fadeIn var(--transition-slow) ease-out;
}

.login-header {
  text-align: center;
  margin-bottom: var(--spacing-xl);
}

.logo-container {
  width: 80px;
  height: 80px;
  margin: 0 auto var(--spacing-md);
}

.logo-icon {
  width: 100%;
  height: 100%;
  animation: pulse 3s ease-in-out infinite;
}

.app-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-sm);
  text-shadow: 0 0 20px rgba(139, 92, 246, 0.3);
}

.app-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 300;
}

.login-tabs {
  display: flex;
  background: rgba(255, 255, 255, 0.05);
  border-radius: var(--radius-md);
  padding: 4px;
  margin-bottom: var(--spacing-lg);
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  color: var(--text-secondary);
  font-size: 14px;
}

.tab-item:hover {
  color: var(--text-primary);
}

.tab-item.active {
  background: var(--btn-primary-bg);
  color: white;
  font-weight: 500;
}

.login-form,
.phone-login-form {
  animation: slideIn var(--transition-normal) ease-out;
}

.form-group {
  margin-bottom: var(--spacing-lg);
}

.form-label {
  display: block;
  margin-bottom: var(--spacing-sm);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 12px;
  color: var(--text-secondary);
  font-size: 16px;
}

.neon-input {
  width: 100%;
  padding: 12px 12px 12px 40px;
}

.code-wrapper {
  gap: var(--spacing-sm);
}

.code-button {
  white-space: nowrap;
  padding: 0 var(--spacing-md);
  flex-shrink: 0;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
}

.checkbox-label {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 14px;
}

.neon-checkbox {
  margin-right: var(--spacing-sm);
}

.forgot-password {
  color: var(--neon-blue);
  cursor: pointer;
  font-size: 14px;
  text-decoration: none;
  transition: color var(--transition-fast);

  &:hover {
    color: var(--neon-cyan);
  }
}

.login-button {
  width: 100%;
  margin-bottom: var(--spacing-lg);
  font-size: 16px;
}

.divider {
  display: flex;
  align-items: center;
  margin-bottom: var(--spacing-lg);
  color: var(--text-tertiary);
  font-size: 12px;
}

.divider-line {
  flex: 1;
  height: 1px;
  background: var(--glass-border);
}

.divider-text {
  padding: 0 var(--spacing-md);
}

.enterprise-button {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
}

.register-link {
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
  margin-top: var(--spacing-md);
}

.register-link-text {
  background: none;
  border: none;
  color: var(--neon-purple);
  text-decoration: none;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  font-size: inherit;
  padding: 0;
  margin: 0;
  line-height: 1;
  box-shadow: none;
  outline: none;
  transition: color 0.3s;
  border-radius: 0;

  &:hover {
    color: var(--neon-blue);
  }

  &:active {
    transform: none;
  }
}

/* Mobile responsive */
@media (max-width: 480px) {
  .login-container {
    padding: var(--spacing-sm);
  }

  .login-card {
    padding: var(--spacing-lg);
  }

  .app-title {
    font-size: 24px;
  }

  .login-button,
  .enterprise-button {
    font-size: 14px;
  }
}
</style>
