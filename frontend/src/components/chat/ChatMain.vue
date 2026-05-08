<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useChat } from '@/composables/useChat'
import MessageItem from './MessageItem.vue'
import EmptyState from './EmptyState.vue'
import InputBar from './InputBar.vue'

const chatStore = useChatStore()
const { sendMessage, stopGeneration } = useChat()

const messagesContainer = ref<HTMLElement | null>(null)

const messages = computed(() => chatStore.currentMessages)
const isStreaming = computed(() => chatStore.isStreaming)
const activeId = computed(() => chatStore.activeConversationId)

// Auto-scroll when new messages arrive
watch(
  () => messages.value.length,
  () => {
    nextTick(() => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      }
    })
  }
)

// Also scroll on token append (content changes)
watch(
  () => messages.value.map(m => m.content.length).join(','),
  () => {
    nextTick(() => {
      if (messagesContainer.value) {
        const el = messagesContainer.value
        // Only auto-scroll if already near the bottom
        if (el.scrollHeight - el.scrollTop - el.clientHeight < 100) {
          el.scrollTop = el.scrollHeight
        }
      }
    })
  }
)

function handleSend(text: string) {
  const sessionId = activeId.value ?? crypto.randomUUID()
  sendMessage(text, sessionId)
}

function handleSuggestionAsk(text: string) {
  handleSend(text)
}

const convTitle = computed(() => {
  const conv = chatStore.conversations.find(c => c.id === activeId.value)
  return conv?.title ?? '智能问答'
})
</script>

<template>
  <div class="chat-main">
    <header class="chat-header">
      <h2 class="chat-title">{{ convTitle }}</h2>
    </header>

    <div ref="messagesContainer" class="messages-container">
      <EmptyState
        v-if="messages.length === 0 && !isStreaming"
        @ask="handleSuggestionAsk"
      />

      <MessageItem
        v-for="msg in messages"
        :key="msg.id"
        :message="msg"
      />
    </div>

    <InputBar
      :disabled="false"
      :is-streaming="isStreaming"
      @send="handleSend"
      @stop="stopGeneration"
    />
  </div>
</template>

<style scoped lang="scss">
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #0b0e11;
}

.chat-header {
  height: 60px;
  padding: 0 24px;
  border-bottom: 1px solid #252b33;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: #e8ecf1;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px;
}
</style>
