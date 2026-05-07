<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { sendVerifyCode } from '@/api/verify'
import { resetPassword } from '@/api/auth'
import { message } from 'ant-design-vue'
import { LockOutlined, PhoneOutlined, CheckCircleOutlined, ArrowLeftOutlined } from '@ant-design/icons-vue'

const router = useRouter()

// Password reset form state
const resetForm = ref({
  phone: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

// Loading state
const loading = ref(false)
const countDown = ref(0)

// Methods
const handleResetPassword = async () => {
  // Validation
  if (!resetForm.value.phone) {
    message.warning('请输入手机号')
    return
  }

  const phoneRegex = /^1[3-9]\d{9}$/
  if (!phoneRegex.test(resetForm.value.phone)) {
    message.warning('请输入正确的手机号')
    return
  }

  if (!resetForm.value.code) {
    message.warning('请输入验证码')
    return
  }

  if (!resetForm.value.newPassword) {
    message.warning('请输入新密码')
    return
  }

  if (resetForm.value.newPassword.length < 6 || resetForm.value.newPassword.length > 20) {
    message.warning('密码长度应为 6-20 位')
    return
  }

  if (!resetForm.value.confirmPassword) {
    message.warning('请确认新密码')
    return
  }

  if (resetForm.value.newPassword !== resetForm.value.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }

  loading.value = true
  try {
    const response = await resetPassword({
      phone: resetForm.value.phone,
      code: resetForm.value.code,
      newPassword: resetForm.value.newPassword,
      confirmPassword: resetForm.value.confirmPassword
    })

    if (response.success) {
      message.success('密码重置成功，请使用新密码登录')
      setTimeout(() => {
        router.push('/login')
      }, 1500)
    }
  } catch (error: any) {
    message.error(error.message || '密码重置失败')
  } finally {
    loading.value = false
  }
}

const handleBackToLogin = () => {
  router.push('/login')
}

const sendCode = async () => {
  if (!resetForm.value.phone) {
    message.warning('请输入手机号')
    return
  }

  const phoneRegex = /^1[3-9]\d{9}$/
  if (!phoneRegex.test(resetForm.value.phone)) {
    message.warning('请输入正确的手机号')
    return
  }

  loading.value = true
  try {
    await sendVerifyCode({
      phone: resetForm.value.phone,
      type: 'reset'
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
const passwordStrength = ref({ level: 0, text: '', color: '' })

const checkPassword = () => {
  const password = resetForm.value.newPassword
  if (!password) {
    passwordStrength.value = { level: 0, text: '', color: '' }
    return
  }

  let strength = 0
  if (password.length >= 6) strength++
  if (password.length >= 8) strength++
  if (/[A-Z]/.test(password)) strength++
  if (/[0-9]/.test(password)) strength++
  if (/[^A-Za-z0-9]/.test(password)) strength++

  if (strength <= 2) passwordStrength.value = { level: 1, text: '弱', color: '#ef4444' }
  else if (strength <= 4) passwordStrength.value = { level: 2, text: '中', color: '#eab308' }
  else passwordStrength.value = { level: 3, text: '强', color: '#10b981' }
}

const isPasswordMatch = computed(() => {
  return resetForm.value.newPassword === resetForm.value.confirmPassword &&
         resetForm.value.newPassword.length > 0
})
</script>

<template>
  <div class="reset-container">
    <!-- Particle Background -->
    <div class="particle-background"></div>

    <!-- Main Content -->
    <div class="reset-content">
      <div class="reset-card glass-card">
        <!-- Header -->
        <div class="reset-header">
          <button class="back-button" @click="handleBackToLogin">
            <ArrowLeftOutlined />
          </button>
          <h1 class="reset-title">重置密码</h1>
          <p class="reset-subtitle">通过手机验证码重置您的登录密码</p>
        </div>

        <!-- Reset Password Form -->
        <div class="reset-form">
          <!-- Phone -->
          <div class="form-group">
            <label class="form-label">手机号</label>
            <div class="input-wrapper">
              <PhoneOutlined class="input-icon" />
              <input
                v-model="resetForm.phone"
                type="tel"
                class="neon-input"
                placeholder="请输入注册手机号"
                maxlength="11"
              />
            </div>
          </div>

          <!-- Verification Code -->
          <div class="form-group">
            <label class="form-label">验证码</label>
            <div class="input-wrapper code-wrapper">
              <input
                v-model="resetForm.code"
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

          <!-- New Password -->
          <div class="form-group">
            <label class="form-label">新密码</label>
            <div class="input-wrapper">
              <LockOutlined class="input-icon" />
              <input
                v-model="resetForm.newPassword"
                type="password"
                class="neon-input"
                placeholder="请输入新密码（6-20位）"
                maxlength="20"
                @input="checkPassword"
              />
            </div>
            <div class="password-strength" v-if="resetForm.newPassword">
              <span class="strength-label">密码强度：</span>
              <span class="strength-text" :style="{ color: passwordStrength.color }">
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

          <!-- Confirm New Password -->
          <div class="form-group">
            <label class="form-label">确认新密码</label>
            <div class="input-wrapper">
              <LockOutlined class="input-icon" />
              <input
                v-model="resetForm.confirmPassword"
                type="password"
                class="neon-input"
                placeholder="请再次输入新密码"
                maxlength="20"
              />
            </div>
            <div class="password-match" v-if="resetForm.confirmPassword">
              <CheckCircleOutlined
                :style="{ color: isPasswordMatch ? '#10b981' : '#ef4444' }"
              />
              {{ isPasswordMatch ? '密码匹配' : '密码不匹配' }}
            </div>
          </div>

          <!-- Submit Button -->
          <button
            class="neon-button reset-button"
            :loading="loading"
            @click="handleResetPassword"
          >
            重置密码
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.reset-container {
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

.reset-content {
  width: 100%;
  max-width: 440px;
  z-index: 1;
}

.reset-card {
  padding: var(--spacing-xl);
  animation: fadeIn var(--transition-slow) ease-out;
}

.reset-header {
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

.reset-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-sm);
  text-shadow: 0 0 20px rgba(139, 92, 246, 0.3);
}

.reset-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 300;
}

.reset-form {
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

.reset-button {
  width: 100%;
  margin-top: var(--spacing-md);
  font-size: 16px;
}

/* Mobile responsive */
@media (max-width: 480px) {
  .reset-container {
    padding: var(--spacing-sm);
  }

  .reset-card {
    padding: var(--spacing-lg);
  }

  .reset-title {
    font-size: 24px;
  }

  .reset-button {
    font-size: 14px;
  }
}
</style>
