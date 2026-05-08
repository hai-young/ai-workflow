<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getSuggestions } from '@/api/rag'

const suggestions = ref<string[]>([])

onMounted(async () => {
  try {
    const res = await getSuggestions()
    if (res.success && res.suggestions) {
      suggestions.value = res.suggestions
    }
  } catch { /* use defaults */ }
  if (suggestions.value.length === 0) {
    suggestions.value = [
      '如何配置JWT令牌的有效期？',
      'Spring AI 和 LangChain 的区别？',
      'Milvus 如何优化检索性能？',
    ]
  }
})

const emit = defineEmits<{ ask: [text: string] }>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon">
      <svg viewBox="0 0 80 80" fill="none">
        <circle cx="40" cy="40" r="38" stroke="url(#emptyGrad)" stroke-width="2" stroke-dasharray="8 4"/>
        <circle cx="40" cy="40" r="24" fill="url(#emptyGrad)" opacity="0.12"/>
        <path d="M32 40H48M40 32V48" stroke="url(#emptyGrad)" stroke-width="3" stroke-linecap="round"/>
        <defs>
          <linearGradient id="emptyGrad" x1="0" y1="0" x2="80" y2="80">
            <stop offset="0%" stop-color="#3b82f6"/>
            <stop offset="100%" stop-color="#8b5cf6"/>
          </linearGradient>
        </defs>
      </svg>
    </div>
    <h2 class="empty-title">AI 智能助手</h2>
    <p class="empty-desc">有什么可以帮助你的？</p>

    <div class="suggestion-list">
      <button
        v-for="(s, i) in suggestions"
        :key="i"
        class="suggestion-chip"
        @click="emit('ask', s)"
      >
        {{ s }}
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
  animation: float 3s ease-in-out infinite;
}

.empty-title {
  font-size: 20px;
  font-weight: 600;
  color: #e8ecf1;
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 14px;
  color: #848e9c;
  margin: 0 0 32px;
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 360px;
  width: 100%;
}

.suggestion-chip {
  padding: 12px 18px;
  border: 1px solid #252b33;
  border-radius: 10px;
  background: #15191f;
  color: #848e9c;
  font-size: 14px;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;

  &:hover {
    border-color: #3b82f6;
    color: #e8ecf1;
    background: rgba(59, 130, 246, 0.06);
  }
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}
</style>
