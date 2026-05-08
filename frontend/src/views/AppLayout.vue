<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute, RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import {
  UserOutlined, LogoutOutlined, DashboardOutlined,
  MessageOutlined, DatabaseOutlined, SettingOutlined,
  BulbOutlined, MenuOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { Component } from 'vue'
import { getUserSettings, updateUserSettings } from '@/api/rag'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const uiStore = useUiStore()

const username = computed(() => authStore.username)

// ── Settings state ──
const memoryTtlDays = ref(30)
const maxRounds = ref(10)
const streamSpeed = ref<'fast' | 'normal' | 'slow'>('normal')
const settingsLoaded = ref(false)
const settingsSaving = ref(false)

interface MenuItem {
  key: string
  icon: Component
  label: string
  path: string
}

const menuItems: MenuItem[] = [
  { key: 'dashboard', icon: DashboardOutlined, label: '工作台', path: '/dashboard' },
  { key: 'chat', icon: MessageOutlined, label: '智能问答', path: '/chat' },
  { key: 'knowledge', icon: DatabaseOutlined, label: '知识库', path: '/knowledge' },
]

const breadcrumbs = computed(() => {
  const crumbs: { title: string; path?: string }[] = [{ title: '工作台', path: '/dashboard' }]
  if (route.path.startsWith('/chat')) {
    crumbs.push({ title: '智能问答', path: '/chat' })
    if (route.params.sessionId) {
      crumbs.push({ title: '对话详情' })
    }
  } else if (route.path.startsWith('/knowledge')) {
    crumbs.push({ title: '知识库管理' })
  }
  return crumbs
})

const isActive = (path: string) => route.path.startsWith(path)

function navigate(path: string) {
  router.push(path)
}

async function handleLogout() {
  try {
    await authStore.logout()
    router.push('/login')
  } catch {
    router.push('/login')
  }
}

async function loadSettings() {
  try {
    const res = await getUserSettings()
    if (res.success && res.data) {
      memoryTtlDays.value = res.data.memoryTtlDays ?? 30
      maxRounds.value = res.data.maxRounds ?? 10
      streamSpeed.value = res.data.streamSpeed ?? 'normal'
    }
  } catch {
    // use defaults
  } finally {
    settingsLoaded.value = true
  }
}

async function saveSettings() {
  if (settingsSaving.value) return
  settingsSaving.value = true
  try {
    await updateUserSettings({
      memoryTtlDays: memoryTtlDays.value,
      maxRounds: maxRounds.value,
      streamSpeed: streamSpeed.value,
    })
    message.success('设置已保存')
  } catch {
    message.error('保存设置失败')
  } finally {
    settingsSaving.value = false
  }
}

async function handleMemoryTtlChange(val: number | null) {
  if (val != null) {
    memoryTtlDays.value = val
    await saveSettings()
  }
}

async function handleMaxRoundsChange(val: number | null) {
  if (val != null) {
    maxRounds.value = val
    await saveSettings()
  }
}

async function handleStreamSpeedChange(val: string) {
  streamSpeed.value = val as 'fast' | 'normal' | 'slow'
  await saveSettings()
}

// Load settings when drawer opens
watch(() => uiStore.settingsDrawerOpen, (isOpen) => {
  if (isOpen) {
    loadSettings()
  }
})

function openSettings() {
  uiStore.openSettings()
}
</script>

<template>
  <div class="app-layout">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="logo-section">
        <div class="logo-icon">
          <svg viewBox="0 0 64 64" fill="none">
            <circle cx="32" cy="32" r="30" stroke="url(#logoGradient)" stroke-width="4"/>
            <circle cx="32" cy="32" r="20" fill="url(#logoGradient)" opacity="0.3"/>
            <path d="M32 22V42M22 32H42" stroke="white" stroke-width="4" stroke-linecap="round"/>
            <defs>
              <linearGradient id="logoGradient" x1="0" y1="0" x2="64" y2="64">
                <stop offset="0%" stop-color="#8b5cf6"/>
                <stop offset="100%" stop-color="#06b6d4"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <h1 class="app-title">智能协作平台</h1>
      </div>

      <nav class="menu">
        <a
          v-for="item in menuItems"
          :key="item.key"
          class="menu-item"
          :class="{ active: isActive(item.path) }"
          @click="navigate(item.path)"
        >
          <component :is="item.icon" class="menu-icon" />
          <span class="menu-text">{{ item.label }}</span>
        </a>
      </nav>

      <div class="user-section">
        <div class="user-info">
          <div class="user-avatar">
            <UserOutlined />
          </div>
          <div class="user-details">
            <div class="user-name">{{ username }}</div>
            <div class="user-role">企业人员</div>
          </div>
        </div>
        <button class="logout-btn" @click="handleLogout">
          <LogoutOutlined />
          退出登录
        </button>
      </div>
    </aside>

    <!-- Main -->
    <div class="main-wrapper">
      <!-- TopBar -->
      <header class="topbar">
        <div class="topbar-left">
          <button class="hamburger-btn" @click="uiStore.toggleSidebar()">
            <MenuOutlined />
          </button>
          <div class="breadcrumb">
            <template v-for="(crumb, i) in breadcrumbs" :key="i">
              <span v-if="i > 0" class="breadcrumb-sep">›</span>
              <router-link
                v-if="crumb.path"
                :to="crumb.path"
                class="breadcrumb-link"
              >
                {{ crumb.title }}
              </router-link>
              <span v-else class="breadcrumb-current">{{ crumb.title }}</span>
            </template>
          </div>
        </div>
        <div class="topbar-actions">
          <button class="icon-btn" @click="uiStore.toggleTheme()" :title="uiStore.theme === 'dark' ? '切换到亮色主题' : '切换到暗色主题'">
            <BulbOutlined :class="{ 'bulb-on': uiStore.theme === 'dark' }" />
          </button>
          <button class="icon-btn" @click="openSettings" title="设置">
            <SettingOutlined />
          </button>
        </div>
      </header>

      <!-- Mobile sidebar drawer -->
      <a-drawer
        :open="uiStore.sidebarOpen"
        placement="left"
        :width="280"
        :closable="true"
        body-style="padding: 0; background: #0b0e11;"
        @close="uiStore.toggleSidebar()"
      >
        <div class="mobile-sidebar">
          <div class="logo-section">
            <h1 class="app-title">智能协作平台</h1>
          </div>
          <nav class="menu">
            <a
              v-for="item in menuItems"
              :key="item.key"
              class="menu-item"
              :class="{ active: isActive(item.path) }"
              @click="navigate(item.path)"
            >
              <component :is="item.icon" class="menu-icon" />
              <span class="menu-text">{{ item.label }}</span>
            </a>
          </nav>
        </div>
      </a-drawer>

      <!-- Page Content -->
      <main class="main-content">
        <RouterView />
      </main>
    </div>

    <!-- Settings Drawer -->
    <a-drawer
      :open="uiStore.settingsDrawerOpen"
      title="设置"
      placement="right"
      :width="340"
      @close="uiStore.closeSettings()"
    >
      <div class="settings-section">
        <h4 class="settings-heading">外观</h4>
        <div class="settings-row">
          <span>{{ uiStore.theme === 'dark' ? '🌙 暗色主题' : '☀️ 亮色主题' }}</span>
          <a-switch
            :checked="uiStore.theme === 'dark'"
            @change="uiStore.toggleTheme()"
          />
        </div>
      </div>

      <div class="settings-section">
        <h4 class="settings-heading">对话</h4>
        <div class="settings-row">
          <span>流式输出速度</span>
          <a-select :value="streamSpeed" style="width: 100px" @change="handleStreamSpeedChange">
            <a-select-option value="fast">快速</a-select-option>
            <a-select-option value="normal">正常</a-select-option>
            <a-select-option value="slow">慢速</a-select-option>
          </a-select>
        </div>
      </div>

      <div class="settings-section">
        <h4 class="settings-heading">记忆压缩</h4>
        <div class="settings-row">
          <span>历史保留天数</span>
          <a-input-number :min="1" :max="365" :value="memoryTtlDays" style="width: 80px" @change="handleMemoryTtlChange" />
        </div>
        <div class="settings-row" style="margin-top: 12px">
          <span>自动压缩阈值（轮）</span>
          <a-input-number :min="2" :max="50" :value="maxRounds" style="width: 80px" @change="handleMaxRoundsChange" />
        </div>
      </div>

      <div class="settings-section">
        <h4 class="settings-heading">知识库</h4>
        <div class="settings-row">
          <span>默认检索数量</span>
          <a-input-number :min="1" :max="20" default-value="5" style="width: 80px" />
        </div>
        <div class="settings-row" style="margin-top: 12px">
          <span>显示相关性分数</span>
          <a-switch default-checked />
        </div>
      </div>

      <div class="settings-section">
        <h4 class="settings-heading">关于</h4>
        <p class="about-text">智能 RAG 系统 v2.0</p>
        <p class="about-text secondary">Spring AI + Milvus + Elasticsearch</p>
      </div>
    </a-drawer>
  </div>
</template>

<style scoped lang="scss">
.app-layout {
  display: flex;
  min-height: 100vh;
  background: #0b0e11;
}

// ── Sidebar ──
.sidebar {
  width: 260px;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
  border-right: 1px solid #252b33;
  display: flex;
  flex-direction: column;
  position: fixed;
  height: 100vh;
  left: 0;
  top: 0;
  z-index: 50;
}

.logo-section {
  padding: 20px 20px 16px;
  border-bottom: 1px solid #252b33;
}

.logo-icon {
  width: 40px;
  height: 40px;
  margin-bottom: 12px;

  svg {
    width: 100%;
    height: 100%;
  }
}

.app-title {
  font-size: 17px;
  font-weight: 600;
  color: #e8ecf1;
  margin: 0;
  letter-spacing: -0.01em;
}

.menu {
  flex: 1;
  padding: 12px 12px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  margin-bottom: 2px;
  border-radius: 8px;
  color: #848e9c;
  cursor: pointer;
  transition: all 0.2s;
  text-decoration: none;
  font-size: 14px;

  &:hover {
    background: rgba(255, 255, 255, 0.04);
    color: #e8ecf1;
  }

  &.active {
    background: rgba(59, 130, 246, 0.12);
    color: #3b82f6;
    font-weight: 500;
    border-left: 3px solid #3b82f6;
    padding-left: 11px;
  }
}

.menu-icon {
  font-size: 18px;
}

.menu-text {
  font-weight: inherit;
}

.user-section {
  padding: 14px 16px;
  border-top: 1px solid #252b33;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.user-avatar {
  width: 38px;
  height: 38px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: white;
}

.user-details {
  flex: 1;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #e8ecf1;
}

.user-role {
  font-size: 12px;
  color: #505968;
}

.logout-btn {
  width: 100%;
  padding: 9px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 8px;
  color: #ef4444;
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;

  &:hover {
    background: rgba(239, 68, 68, 0.16);
    border-color: rgba(239, 68, 68, 0.4);
  }
}

// ── Main Wrapper ──
.main-wrapper {
  flex: 1;
  margin-left: 260px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

// ── TopBar ──
.topbar {
  height: 60px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #252b33;
  background: #0b0e11;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 10;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.breadcrumb-sep {
  color: #505968;
}

.breadcrumb-link {
  color: #848e9c;
  text-decoration: none;

  &:hover {
    color: #3b82f6;
  }
}

.breadcrumb-current {
  color: #e8ecf1;
  font-weight: 500;
}

.topbar-actions {
  display: flex;
  gap: 6px;
}

.icon-btn {
  width: 34px;
  height: 34px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #848e9c;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: all 0.2s;

  &:hover {
    background: #15191f;
    border-color: #252b33;
    color: #e8ecf1;
  }
}

.bulb-on {
  color: #f5a623;
}

.main-content {
  flex: 1;
}

// ── Settings ──
.settings-section {
  margin-bottom: 24px;
}

.settings-heading {
  font-size: 13px;
  font-weight: 600;
  color: #505968;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0 0 12px;
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 14px;
  color: #e8ecf1;
}

.about-text {
  font-size: 13px;
  color: #848e9c;
  margin: 2px 0;

  &.secondary {
    color: #505968;
  }
}

// ── Mobile (<768px) ──
.hamburger-btn {
  display: none;
  width: 34px;
  height: 34px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #848e9c;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  margin-right: 8px;

  &:hover {
    background: #15191f;
    color: #e8ecf1;
  }
}

.topbar-left {
  display: flex;
  align-items: center;
}

@media (max-width: 767px) {
  .sidebar {
    display: none; // Hidden on mobile, use drawer instead
  }

  .main-wrapper {
    margin-left: 0 !important;
  }

  .hamburger-btn {
    display: flex;
  }

  .topbar {
    padding: 0 16px;
  }

  .breadcrumb-link,
  .breadcrumb-current {
    font-size: 12px;
  }
}

.mobile-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #0b0e11;
}
</style>
