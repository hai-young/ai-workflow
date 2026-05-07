<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { sendVerifyCode, verifyCode } from '@/api/verify'
import { register } from '@/api/auth'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, PhoneOutlined, MailOutlined, ArrowLeftOutlined } from '@ant-design/icons-vue'
import { CheckCircleOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

// Register form state
const registerForm = ref({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
  code: ''
})

// Loading state
const loading = ref(false)
const countDown = ref(0)

// Methods
const handleRegister = async () => {
  // Validation
  if (!registerForm.value.username) {
    message.warning('请输入用户名')
    return
  }

  if (registerForm.value.username.length < 4 || registerForm.value.username.length > 20) {
    message.warning('用户名长度应为 4-20 位')
    return
  }

  if (!registerForm.value.password) {
    message.warning('请输入密码')
    return
  }

  if (registerForm.value.password.length < 6 || registerForm.value.password.length > 20) {
    message.warning('密码长度应为 6-20 位')
    return
  }

  if (!registerForm.value.confirmPassword) {
    message.warning('请确认密码')
    return
  }

  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    message.warning('两次输入的密码不一致')
    return
  }

  if (!registerForm.value.phone) {
    message.warning('请输入手机号')
    return
  }

  // Phone validation
  const phoneRegex = /^1[3-9]\d{9}$/
  if (!phoneRegex.test(registerForm.value.phone)) {
    message.warning('请输入正确的手机号')
    return
  }

  if (!registerForm.value.code) {
    message.warning('请输入验证码')
    return
  }

  loading.value = true
  try {
    const response = await register({
      username: registerForm.value.username,
      password: registerForm.value.password,
      confirmPassword: registerForm.value.confirmPassword,
      email: registerForm.value.email,
      phone: registerForm.value.phone,
      code: registerForm.value.code
    })

    if (response.success) {
      message.success('注册成功，正在跳转登录页面...')
      setTimeout(() => {
        router.push('/login')
      }, 1500)
    }
  } catch (error: any) {
    message.error(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}

const handleBackToLogin = () => {
  router.push('/login')
}

const sendCode = async () => {
  if (!registerForm.value.phone) {
    message.warning('请输入手机号')
    return
  }

  const phoneRegex = /^1[3-9]\d{9}$/
  if (!phoneRegex.test(registerForm.value.phone)) {
    message.warning('请输入正确的手机号')
    return
  }

  loading.value = true
  try {
    await sendVerifyCode({
      phone: registerForm.value.phone,
      type: 'register'
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

// Password strength indicator
const passwordStrength = computed(() => {
  const password = registerForm.value.password
  if (!password) return { level: 0, text: '', color: '' }

  let strength = 0
  if (password.length >= 6) strength++
  if (password.length >= 8) strength++
  if (/[A-Z]/.test(password)) strength++
  if (/[0-9]/.test(password)) strength++
  if (/[^A-Za-z0-9]/.test(password)) strength++

  if (strength <= 2) return { level: 1, text: '弱', color: '#ef4444' }
  if (strength <= 4) return { level: 2, text: '中', color: '#eab308' }
  return { level: 3, text: '强', color: '#10b981' }
})

const isPasswordMatch = computed(() => {
  return registerForm.value.password === registerForm.value.confirmPassword &&
         registerForm.value.password.length > 0
})
</script>

<template>
  <div class="register-container">
    <!-- Particle Background -->
    <div class="particle-background"></div>

    <!-- Main Content -->
    <div class="register-content">
      <div class="register-card glass-card">
        <!-- Header -->
        <div class="register-header">
          <button class="back-button" @click="handleBackToLogin">
            <ArrowLeftOutlined />
          </button>
          <h1 class="register-title">创建账号</h1>
          <p class="register-subtitle">加入智能协作平台，开启高效工作之旅</p>
        </div>

        <!-- Register Form -->
        <div class="register-form">
          <!-- Username -->
          <div class="form-group">
            <label class="form-label">用户名</label>
            <div class="input-wrapper">
              <UserOutlined class="input-icon" />
              <input
                v-model="registerForm.username"
                type="text"
                class="neon-input"
                placeholder="请输入用户名（4-20位字母或数字）"
                maxlength="20"
              />
            </div>
          </div>

          <!-- Password -->
          <div class="form-group">
            <label class="form-label">密码</label>
            <div class="input-wrapper">
              <LockOutlined class="input-icon" />
              <input
                v-model="registerForm.password"
                type="password"
                class="neon-input"
                placeholder="请输入密码（6-20位）"
                maxlength="20"
                @input="checkPassword"
              />
            </div>
            <div class="password-strength" v-if="registerForm.password">
              <span class="strength-label">密码强度：</span>
              <span
                class="strength-text"
                :style="{ color: passwordStrength.color }"
              >
                {{ passwordStrength.text }}
              </span>
              <div class="strength-bar">
                <div
                  class="strength-fill"
                  :style="{ width: passwordStrength.level * 33.33 + '%', background: passwordStrength.color }"
                ></div>
              </div>
            </div>
          </div>

          <!-- Confirm Password -->
          <div class="form-group">
            <label class="form-label">确认密码</label>
            <div class="input-wrapper">
              <LockOutlined class="input-icon" />
              <input
                v-model="registerForm.confirmPassword"
                type="password"
                class="neon-input"
                placeholder="请再次输入密码"
                maxlength="20"
              />
            </div>
            <div class="password-match" v-if="registerForm.confirmPassword">
              <CheckCircleOutlined
                :style="{ color: isPasswordMatch ? '#10b981' : '#ef4444' }"
              />
              {{ isPasswordMatch ? '密码匹配' : '密码不匹配' }}
            </div>
          </div>

          <!-- Email (Optional) -->
          <div class="form-group">
            <label class="form-label">邮箱（可选）</label>
            <div class="input-wrapper">
              <MailOutlined class="input-icon" />
              <input
                v-model="registerForm.email"
                type="email"
                class="neon-input"
                placeholder="请输入邮箱地址"
                maxlength="100"
              />
            </div>
          </div>

          <!-- Phone -->
          <div class="form-group">
            <label class="form-label">手机号</label>
            <div class="input-wrapper">
              <PhoneOutlined class="input-icon" />
              <input
                v-model="registerForm.phone"
                type="tel"
                class="neon-input"
                placeholder="请输入手机号"
                maxlength="11"
              />
            </div>
          </div>

          <!-- Verification Code -->
          <div class="form-group">
            <label class="form-label">验证码</label>
            <div class="input-wrapper code-wrapper">
              <input
                v-model="registerForm.code"
                type="text"
                class="neon-input"
                placeholder="请输入验证码"
                maxlength="6"
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

          <!-- Submit Button -->
          <button
            class="neon-button register-button"
            :loading="loading"
            @click="handleRegister"
          >
            注册并登录
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.register-container {
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

.register-content {
  width: 100%;
  max-width: 440px;
  z-index: 1;
}

.register-card {
  padding: var(--spacing-xl);
  animation: fadeIn var(--transition-slow) ease-out;
}

.register-header {
  text-align: center;
  margin-bottom: var(--spacing-xl);
}

.back-button {
  position: absolute;
  top: var(--spacing-lg);
  left: var(--spacing-lg);
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  transition: all var(--transition-fast);

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    color: var(--text-primary);
  }
}

.register-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-sm);
  text-shadow: 0 0 20px rgba(139, 92, 246, 0.3);
}

.register-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 300;
}

.register-form {
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

.password-strength {
  margin-top: var(--spacing-sm);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.strength-label {
  color: var(--text-tertiary);
}

.strength-text {
  font-weight: 500;
}

.strength-bar {
  flex: 1;
  height: 4px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  transition: width 0.3s ease, background 0.3s ease;
}

.password-match {
  margin-top: var(--spacing-sm);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.register-button {
  width: 100%;
  margin-top: var(--spacing-md);
  font-size: 16px;
}

/* Mobile responsive */
@media (max-width: 480px) {
  .register-container {
    padding: var(--spacing-sm);
  }

  .register-card {
    padding: var(--spacing-lg);
  }

  .register-title {
    font-size: 24px;
  }

  .register-button {
    font-size: 14px;
  }
}
</style>
