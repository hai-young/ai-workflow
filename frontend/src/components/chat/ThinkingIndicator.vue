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

const stageMeta: Record<string, { icon: string; label: string }> = {
  intent: { icon: '🎯', label: '意图识别' },
  rewrite: { icon: '✏️', label: '查询重写' },
  retrieval: { icon: '🔍', label: '混合检索' },
  rerank: { icon: '⭐', label: '精排' },
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
      detail: step
        ? step.intent
          ? `意图: ${step.intent}`
          : step.rewritten_query
          ? `改写: ${step.rewritten_query}`
          : step.count != null
          ? `${step.count} 篇`
          : ''
        : '',
    }
  })
)
</script>

<template>
  <div v-if="steps.length > 0" class="thinking-indicator" :class="{ completed: !currentStage }">
    <div class="thinking-header">🧠 思考过程</div>
    <div class="thinking-steps">
      <TransitionGroup name="step">
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
          <span v-if="s.detail && s.done" class="step-detail">{{ s.detail }}</span>
        </div>
      </TransitionGroup>
    </div>
  </div>
</template>

<style scoped lang="scss">
.thinking-indicator {
  background: rgba(59, 130, 246, 0.04);
  border: 1px solid rgba(59, 130, 246, 0.12);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
}

.thinking-header {
  font-size: 12px;
  font-weight: 500;
  color: #848e9c;
  margin-bottom: 10px;
}

.thinking-steps {
  display: flex;
  gap: 12px;
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

  .done & {
    background: #22c55e;
  }

  .current & {
    background: #3b82f6;
  }

  &.pulse {
    animation: dotPulse 2s ease-in-out infinite;
  }
}

.step-icon {
  font-size: 14px;
}

.step-label {
  white-space: nowrap;
}

.step-check {
  font-size: 10px;
  color: #22c55e;
  font-weight: 700;
}

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

// Transition
.step-enter-active,
.step-leave-active {
  transition: all 0.2s ease;
}

.step-enter-from,
.step-leave-to {
  opacity: 0;
  transform: translateY(-2px);
}

@keyframes dotPulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.4); opacity: 0.6; }
}
</style>
