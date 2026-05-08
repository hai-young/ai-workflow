<script setup lang="ts">
import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import type { ThinkingStep } from '@/types/chat'

const props = defineProps<{ steps?: ThinkingStep[]; currentStage?: string | null }>()
const chatStore = useChatStore()

const steps = computed(() => {
  if (props.steps !== undefined) return props.steps
  return chatStore.activeThinking?.steps ?? []
})
const currentStage = computed(() => {
  if (props.currentStage !== undefined) return props.currentStage
  return chatStore.activeThinking?.currentStage
})
const isDone = computed(() => steps.value.length > 0 && !currentStage.value)

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
    const meta = stageMeta[stage] || { icon: '●', label: stage }
    const done = !!step
    const current = currentStage.value === stage
    return { key: stage, meta, done, current, step }
  }).filter(s => s.done || s.current)
)

function stepDetail(s: { step?: { intent?: string; rewritten_query?: string; count?: number } }): string {
  const st = s.step
  if (!st) return ''
  if (st.intent) return st.intent
  if (st.rewritten_query) return st.rewritten_query
  if (st.count != null) return `${st.count} 篇文档`
  return ''
}
</script>

<template>
  <div v-if="steps.length > 0" class="thinking-indicator" :class="{ done: isDone }">
    <div class="thinking-header">
      <span v-if="!isDone" class="pulsing-dot"></span>
      {{ isDone ? '思考过程' : '思考中...' }}
    </div>

    <div class="thinking-rows">
      <div
        v-for="s in stageStates"
        :key="s.key"
        class="think-row"
        :class="{ done: s.done, current: s.current }"
      >
        <span class="row-dot" :class="{ pulse: s.current }"></span>
        <span class="row-icon">{{ s.meta.icon }}</span>
        <span class="row-label">{{ s.meta.label }}</span>
        <span v-if="s.done" class="row-check">✓</span>
        <span v-if="s.done && stepDetail(s)" class="row-value">{{ stepDetail(s) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.thinking-indicator {
  background: rgba(59, 130, 246, 0.03);
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 10px;
  font-size: 13px;
  transition: all 0.3s ease;

  &.done {
    background: rgba(34, 197, 94, 0.03);
    border-color: rgba(34, 197, 94, 0.08);
  }
}

.thinking-header {
  font-size: 11px;
  font-weight: 600;
  color: #848e9c;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;

  .done & { color: #22c55e; }
}

.pulsing-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #3b82f6;
  animation: dotPulse 1.5s ease-in-out infinite;
}

.thinking-rows {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.think-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
  font-size: 12px;
  color: #505968;
  transition: color 0.2s;

  &.done { color: #848e9c; }
  &.current { color: #3b82f6; font-weight: 500; }
}

.row-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #505968;
  flex-shrink: 0;

  .done & { background: #22c55e; }
  .current & { background: #3b82f6; }

  &.pulse {
    animation: dotPulse 1.5s ease-in-out infinite;
  }
}

.row-icon { font-size: 13px; flex-shrink: 0; }
.row-label { min-width: 60px; flex-shrink: 0; }
.row-check { font-size: 10px; color: #22c55e; font-weight: 700; }

.row-value {
  color: #e8ecf1;
  font-size: 12px;
  margin-left: auto;
}

@keyframes dotPulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.5); opacity: 0.4; }
}
</style>
