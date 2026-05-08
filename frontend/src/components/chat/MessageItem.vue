<script setup lang="ts">
import { computed } from 'vue'
import type { Message } from '@/types/chat'
import { useChatStore } from '@/stores/chat'
import { renderMarkdown } from '@/utils/markdown'
import ThinkingIndicator from './ThinkingIndicator.vue'

const props = defineProps<{ message: Message }>()
const chatStore = useChatStore()

const isUser = computed(() => props.message.role === 'user')
const isAssistant = computed(() => props.message.role === 'assistant')
const hasCitations = computed(() => (props.message.citations?.length ?? 0) > 0)

function renderContent(text: string): string {
  return renderMarkdown(text)
}

function onCitationClick(index: number) {
  if (props.message.citations) {
    chatStore.openCitation(props.message.citations, index)
  }
}

function handleContentClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.classList.contains('citation-ref')) {
    const idx = parseInt(target.dataset.index || '0', 10)
    if (idx) onCitationClick(idx)
  }
}
</script>

<template>
  <div class="message-item" :class="message.role">
    <div class="message-avatar">
      <span v-if="isUser" class="avatar-icon">&#x1f464;</span>
      <span v-else class="avatar-icon ai">AI</span>
    </div>
    <div class="message-body">
      <!-- Thinking indicator: live during stream, saved for history -->
      <ThinkingIndicator
        v-if="isAssistant && message.thinking?.steps?.length"
        :steps="message.thinking.steps"
        :current-stage="message.thinking.currentStage"
      />
      <ThinkingIndicator v-else-if="isAssistant && message.isStreaming" />

      <div
        v-if="message.content"
        class="message-content"
        :class="{ streaming: message.isStreaming }"
        v-html="renderContent(message.content)"
        @click="handleContentClick"
      ></div>

      <!-- Streaming cursor -->
      <span v-if="message.isStreaming && message.content" class="streaming-cursor">&#x258d;</span>

      <!-- Citations badges -->
      <div v-if="hasCitations && !message.isStreaming" class="citation-badges">
        <button
          v-for="c in message.citations"
          :key="c.index"
          class="citation-badge"
          @click="onCitationClick(c.index)"
        >
          [{{ c.index }}] {{ c.fileName }}
        </button>
      </div>

      <!-- Safety warning -->
      <div v-if="message.safetyStatus === 'warn'" class="safety-warning">
        &#x26a0;&#xfe0f; 该回答的部分内容未经充分验证
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@import '@/styles/highlight.css';

.message-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  animation: msgIn 0.3s ease;

  &.user {
    flex-direction: row-reverse;

    .message-avatar {
      background: linear-gradient(135deg, #3b82f6, #8b5cf6);
    }
  }
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #1c2128;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  color: #848e9c;
  border: 1px solid #252b33;

  .avatar-icon {
    font-size: 14px;

    &.ai {
      font-size: 10px;
      font-weight: 700;
      color: #3b82f6;
    }
  }
}

.message-body {
  max-width: 75%;
  min-width: 0;
}

.message-content {
  font-size: 15px;
  line-height: 1.7;
  color: #e8ecf1;
  word-break: break-word;

  &.streaming {
    display: inline;
  }

  :deep(.citation-ref) {
    color: #3b82f6;
    cursor: pointer;
    font-weight: 600;
    text-decoration: none;
    padding: 0 1px;

    &:hover {
      text-decoration: underline;
      color: #60a5fa;
    }
  }

  :deep(pre) {
    background: #0d1117;
    border: 1px solid #252b33;
    border-radius: 8px;
    padding: 16px;
    overflow-x: auto;
    margin: 12px 0;
  }

  :deep(code) {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 13px;
  }

  :deep(p code) {
    background: #1c2128;
    padding: 2px 6px;
    border-radius: 4px;
    border: 1px solid #252b33;
  }

  :deep(blockquote) {
    border-left: 3px solid #3b82f6;
    padding-left: 16px;
    margin: 12px 0;
    color: #848e9c;
  }

  :deep(table) {
    border-collapse: collapse;
    margin: 12px 0;
    width: 100%;
  }

  :deep(th), :deep(td) {
    border: 1px solid #252b33;
    padding: 8px 12px;
    text-align: left;
  }

  :deep(th) {
    background: #15191f;
    font-weight: 600;
  }

  :deep(ul), :deep(ol) {
    padding-left: 24px;
    margin: 8px 0;
  }

  :deep(li) {
    margin: 4px 0;
  }

  :deep(a) {
    color: #3b82f6;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }

  :deep(h1), :deep(h2), :deep(h3) {
    margin: 16px 0 8px;
    font-weight: 600;
  }

  :deep(h1) { font-size: 20px; }
  :deep(h2) { font-size: 17px; }
  :deep(h3) { font-size: 15px; }

  :deep(strong) {
    font-weight: 600;
  }
}

.streaming-cursor {
  display: inline;
  color: #3b82f6;
  animation: blink 1s step-end infinite;
  font-size: 15px;
}

.citation-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.citation-badge {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(59, 130, 246, 0.08);
  border: 1px solid rgba(59, 130, 246, 0.2);
  color: #3b82f6;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: rgba(59, 130, 246, 0.18);
    border-color: #3b82f6;
  }
}

.safety-warning {
  margin-top: 8px;
  padding: 8px 12px;
  background: rgba(245, 158, 11, 0.08);
  border-left: 3px solid #f59e0b;
  border-radius: 0 6px 6px 0;
  font-size: 12px;
  color: #f59e0b;
}

@keyframes msgIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
