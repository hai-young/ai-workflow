<script setup lang="ts">
import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import type { ThinkingStep } from '@/types/chat'

const props = defineProps<{ steps?: ThinkingStep[]; currentStage?: string | null }>()
const chatStore = useChatStore()

const steps = computed(() =>
  props.steps ?? chatStore.activeThinking?.steps ?? []
)
const currentStage = computed(() =>
  props.currentStage ?? chatStore.activeThinking?.currentStage
)
const isLive = computed(() => !!currentStage.value)

const stageMeta: Record<string, { icon: string; label: string }> = {
  intent: { icon: '🎯', label: '意图识别' },
  rewrite: { icon: '✏️', label: '查询重写' },
  retrieval: { icon: '🔍', label: '混合检索' },
  rerank: { icon: '⭐', label: '重排序' },
}

const orderedStages = ['intent', 'rewrite', 'retrieval', 'rerank']

const stageStates = computed(() =>
  orderedStages.map(stage => {
    const step = steps.value.find(s => s.stage === stage)
    return {
      key: stage,
      meta: stageMeta[stage] || { icon: '●', label: stage },
      done: !!step,
      current: currentStage.value === stage,
      step,
    }
  })
)

function detailHtml(s: ReturnType<typeof stageStates.value>[0]): string {
  const st = s.step
  if (!st) return ''
  if (st.intent) return `意图: ${st.intent}`
  if (st.rewritten_query) return `改写: ${st.rewritten_query}`
  if (st.count != null) return `${st.count} 篇文档`
  return ''
}
</script>

<template>
  <div v-if="steps.length > 0" class="thinking-indicator" :class="{ live: isLive, done: !isLive }">
    <div class="thinking-header">
      <span v-if="isLive" class="pulsing-dot"></span>
      {{ isLive ? '思考中...' : '思考过程' }}
    </div>

    <!-- Live mode: compact horizontal steps -->
    <div v-if="isLive" class="thinking-steps">
      <div
        v-for="s in stageStates"
        :key="s.key"
        class="thinking-step"
        :class="{ done: s.done, current: s.current }"
      >
        <span class="step-dot" :class="{ pulse: s.current }"></span>
        <span class="step-icon">{{ s.meta.icon }}</span>
        <span class="step-label">{{ s.meta.label }}</span>
        <span v-if="s.done" class="step-check">✓</span>
        <span v-if="s.detail && s.current" class="step-detail">{{ detailHtml(s) }}</span>
      </div>
    </div>

    <!-- Done mode: detailed card per stage -->
    <div v-else class="thinking-details">
      <div
        v-for="s in stageStates.filter(x => x.done)"
        :key="s.key"
        class="detail-row"
      >
        <span class="detail-icon">{{ s.meta.icon }}</span>
        <span class="detail-label">{{ s.meta.label }}</span>
        <span class="detail-value">{{ detailHtml(s) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.thinking-indicator {
  background: rgba(59, 130, 246, 0.04);
  border: 1px solid rgba(59, 130, 246, 0.12);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 10px;
  font-size: 13px;
  transition: all 0.3s ease;

  &.done {
    background: rgba(34, 197, 94, 0.03);
    border-color: rgba(34, 197, 94, 0.1);
  }
}

.thinking-header {
  font-size: 11px;
  font-weight: 600;
  color: #848e9c;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;

  .done & {
    color: #22c55e;
  }
}

.pulsing-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #3b82f6;
  animation: dotPulse 1.5s ease-in-out infinite;
}

// ── Live steps (horizontal) ──
.thinking-steps {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.thinking-step {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #505968;
  transition: all 0.2s ease;
  position: relative;

  &.done {
    color: #22c55e;
  }
  &.current {
    color: #3b82f6;
    font-weight: 500;
  }
}

.step-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #505968;
  transition: background 0.2s;

  .done & { background: #22c55e; }
  .current & { background: #3b82f6; }

  &.pulse {
    animation: dotPulse 1.5s ease-in-out infinite;
  }
}

.step-icon { font-size: 13px; }
.step-label { white-space: nowrap; }
.step-check { font-size: 10px; color: #22c55e; font-weight: 700; }

.step-detail {
  position: absolute;
  left: 0;
  top: 100%;
  margin-top: 4px;
  font-size: 11px;
  color: #848e9c;
  background: #15191f;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
  z-index: 1;
}

// ── Done details (vertical card) ──
.thinking-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
}

.detail-icon {
  font-size: 13px;
  flex-shrink: 0;
}

.detail-label {
  color: #848e9c;
  min-width: 60px;
  font-weight: 500;
}

.detail-value {
  color: #e8ecf1;
  flex: 1;
}

@keyframes dotPulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.5); opacity: 0.4; }
}
</style>
