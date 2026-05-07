<script setup lang="ts">
import { ref, computed } from 'vue'
import { LockOutlined, EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  modelValue: string
  placeholder?: string
  disabled?: boolean
  maxlength?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const showPassword = ref(false)

const handleTogglePassword = () => {
  showPassword.value = !showPassword.value
}

const passwordStrength = computed(() => {
  const password = props.modelValue
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

const handleInput = (e: Event) => {
  const target = e.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <div class="password-input-wrapper">
    <input
      :value="modelValue"
      :type="showPassword ? 'text' : 'password'"
      :placeholder="placeholder"
      :disabled="disabled"
      :maxlength="maxlength"
      @input="handleInput"
      class="password-input"
    />
    <button
      :class="['password-toggle', { active: showPassword }]"
      @click="handleTogglePassword"
      :disabled="disabled"
    >
      <EyeOutlined v-if="!showPassword" />
      <EyeInvisibleOutlined v-else />
    </button>
    <div v-if="modelValue" class="strength-indicator">
      <div class="strength-bar">
        <div
          class="strength-fill"
          :style="{
            width: passwordStrength.level * 33.33 + '%',
            background: passwordStrength.color
          }"
        ></div>
      </div>
      <span class="strength-text" :style="{ color: passwordStrength.color }">
        {{ passwordStrength.text }}
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.password-input-wrapper {
  position: relative;
  width: 100%;
}

.password-input {
  width: 100%;
  padding: 12px 40px 12px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: white;
  font-size: 14px;
  transition: all 0.3s ease;
}

.password-input:hover {
  border-color: rgba(139, 92, 246, 0.5);
}

.password-input:focus {
  outline: none;
  border-color: rgba(139, 92, 246, 0.5);
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.1);
}

.password-toggle {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  background: transparent;
  border: none;
  color: var(--text-tertiary);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.3s ease;

  &:hover:not(:disabled) {
    color: var(--text-secondary);
    background: rgba(255, 255, 255, 0.05);
  }

  &:active:not(:disabled) {
    transform: translateY(-50%) scale(0.95);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.strength-indicator {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
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

.strength-text {
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
</style>
