<script setup lang="ts">
import { ref } from 'vue'
import { SendOutlined, PauseCircleOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  disabled?: boolean
  isStreaming?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  send: [text: string]
  stop: []
}>()

const inputText = ref('')
const textareaRef = ref<InstanceType<typeof HTMLTextAreaElement> | null>(null)

function handleSend() {
  const text = inputText.value.trim()
  if (!text || props.disabled) return
  emit('send', text)
  inputText.value = ''
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="input-bar">
    <div class="input-wrapper">
      <textarea
        ref="textareaRef"
        v-model="inputText"
        class="input-field"
        :placeholder="placeholder || '输入你的问题... (Enter 发送, Shift+Enter 换行)'"
        :disabled="disabled"
        rows="1"
        @keydown="handleKeydown"
      ></textarea>
      <div class="input-actions">
        <button
          v-if="isStreaming"
          class="stop-btn"
          @click="emit('stop')"
          title="停止生成"
        >
          <PauseCircleOutlined />
        </button>
        <button
          v-else
          class="send-btn"
          :disabled="!inputText.trim() || disabled"
          @click="handleSend"
          title="发送"
        >
          <SendOutlined />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.input-bar {
  padding: 12px 24px 16px;
  border-top: 1px solid #252b33;
  background: #0b0e11;
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: #15191f;
  border: 1px solid #252b33;
  border-radius: 12px;
  padding: 10px 14px;
  transition: border-color 0.2s;

  &:focus-within {
    border-color: #3b82f6;
    box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.12);
  }
}

.input-field {
  flex: 1;
  border: none;
  background: transparent;
  color: #e8ecf1;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  outline: none;
  min-height: 22px;
  max-height: 120px;
  font-family: inherit;

  &::placeholder {
    color: #505968;
  }

  &:disabled {
    opacity: 0.5;
  }
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: all 0.2s;

  &:hover:not(:disabled) {
    box-shadow: 0 0 16px rgba(59, 130, 246, 0.4);
    transform: scale(1.05);
  }

  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }
}

.stop-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid rgba(239, 68, 68, 0.3);
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: all 0.2s;

  &:hover {
    background: rgba(239, 68, 68, 0.18);
  }
}
</style>
