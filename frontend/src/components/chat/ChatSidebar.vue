<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { PlusOutlined, SearchOutlined, DeleteOutlined, MessageOutlined } from '@ant-design/icons-vue'

const chatStore = useChatStore()
const searchQuery = ref('')
const hoveredId = ref<string | null>(null)

onMounted(() => {
  chatStore.fetchConversations()
})

function selectConversation(id: string) {
  chatStore.setActiveConversation(id)
}

function newConversation() {
  chatStore.createConversation()
  searchQuery.value = ''
}

function handleDelete(id: string, e: Event) {
  e.stopPropagation()
  chatStore.deleteConversation(id)
}

const filteredConversations = computed(() => {
  if (!searchQuery.value.trim()) return chatStore.conversations
  const q = searchQuery.value.toLowerCase()
  return chatStore.conversations.filter(
    c => c.title.toLowerCase().includes(q) || c.preview?.toLowerCase().includes(q)
  )
})

import { computed } from 'vue'
</script>

<template>
  <aside class="chat-sidebar">
    <div class="sidebar-header">
      <button class="new-chat-btn" @click="newConversation">
        <PlusOutlined />
        <span>新建对话</span>
      </button>
    </div>

    <div class="search-bar">
      <SearchOutlined class="search-icon" />
      <input
        v-model="searchQuery"
        class="search-input"
        placeholder="搜索对话..."
      />
    </div>

    <div class="conversation-list">
      <div
        v-for="conv in filteredConversations"
        :key="conv.id"
        class="conv-item"
        :class="{ active: conv.id === chatStore.activeConversationId }"
        @click="selectConversation(conv.id)"
        @mouseenter="hoveredId = conv.id"
        @mouseleave="hoveredId = null"
      >
        <MessageOutlined class="conv-icon" />
        <div class="conv-content">
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-meta">
            <span v-if="conv.rounds">{{ conv.rounds }} 轮</span>
            <span v-if="conv.preview" class="conv-preview">{{ conv.preview }}</span>
          </div>
        </div>
        <button
          v-if="hoveredId === conv.id"
          class="conv-delete"
          @click="(e: Event) => handleDelete(conv.id, e)"
          title="删除对话"
        >
          <DeleteOutlined />
        </button>
      </div>

      <div v-if="filteredConversations.length === 0 && !chatStore.conversationsLoading" class="empty-list">
        <p v-if="searchQuery">无匹配的对话</p>
        <p v-else>暂无对话，点击上方按钮开始</p>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.chat-sidebar {
  width: 280px;
  background: #0b0e11;
  border-right: 1px solid #252b33;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #252b33;
}

.new-chat-btn {
  width: 100%;
  padding: 10px;
  border: 1px solid #252b33;
  border-radius: 8px;
  background: transparent;
  color: #e8ecf1;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;

  &:hover {
    border-color: #3b82f6;
    background: rgba(59, 130, 246, 0.06);
    color: #3b82f6;
  }
}

.search-bar {
  position: relative;
  padding: 12px 16px;
}

.search-icon {
  position: absolute;
  left: 28px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: #505968;
}

.search-input {
  width: 100%;
  padding: 8px 12px 8px 32px;
  border: 1px solid #252b33;
  border-radius: 8px;
  background: #15191f;
  color: #e8ecf1;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;

  &::placeholder {
    color: #505968;
  }

  &:focus {
    border-color: #3b82f6;
  }
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  position: relative;

  &:hover {
    background: #15191f;
  }

  &.active {
    background: rgba(59, 130, 246, 0.08);
    border: 1px solid rgba(59, 130, 246, 0.15);
  }
}

.conv-icon {
  color: #505968;
  font-size: 16px;
  flex-shrink: 0;
}

.conv-content {
  flex: 1;
  min-width: 0;
}

.conv-title {
  font-size: 13px;
  font-weight: 500;
  color: #e8ecf1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-meta {
  font-size: 11px;
  color: #505968;
  margin-top: 2px;
  display: flex;
  gap: 8px;

  .conv-preview {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.conv-delete {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #ef4444;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;

  &:hover {
    background: rgba(239, 68, 68, 0.12);
  }
}

.empty-list {
  text-align: center;
  padding: 32px 16px;
  font-size: 13px;
  color: #505968;
}
</style>
