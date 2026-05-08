<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import ChatSidebar from '@/components/chat/ChatSidebar.vue'
import ChatMain from '@/components/chat/ChatMain.vue'
import CitationPanel from '@/components/chat/CitationPanel.vue'

const route = useRoute()
const chatStore = useChatStore()

onMounted(() => {
  chatStore.fetchConversations()

  const sessionId = route.params.sessionId as string | undefined
  if (sessionId) {
    chatStore.setActiveConversation(sessionId)
  }
})

watch(
  () => route.params.sessionId,
  (newId) => {
    if (newId && typeof newId === 'string') {
      chatStore.setActiveConversation(newId)
    }
  }
)
</script>

<template>
  <div class="chat-page">
    <ChatSidebar />
    <ChatMain />
    <CitationPanel />
  </div>
</template>

<style scoped lang="scss">
.chat-page {
  display: flex;
  height: calc(100vh - 60px); // Subtract AppLayout TopBar
  overflow: hidden;
}
</style>
