<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { UserOutlined, LogoutOutlined, DashboardOutlined, FileTextOutlined, TeamOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

const userInfo = computed(() => authStore.userInfo)
const username = computed(() => authStore.username)

const handleLogout = async () => {
  try {
    await authStore.logout()
    router.push('/login')
  } catch (error) {
    console.error('Logout error:', error)
  }
}

const menuItems = [
  {
    key: 'dashboard',
    icon: DashboardOutlined,
    label: '工作台'
  },
  {
    key: 'documents',
    icon: FileTextOutlined,
    label: '文档管理'
  },
  {
    key: 'team',
    icon: TeamOutlined,
    label: '团队协作'
  }
]
</script>

<template>
  <div class="dashboard-container">
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
          @click="router.push(`/${item.key}`)"
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
        <button class="logout-button" @click="handleLogout">
          <LogoutOutlined />
          退出登录
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <header class="top-bar">
        <h2 class="page-title">工作台</h2>
      </header>

      <div class="content">
        <div class="welcome-section">
          <h1 class="welcome-title">
            {{ `欢迎回来，${username}！` }}
          </h1>
          <p class="welcome-subtitle">开始您的高效协作之旅</p>
        </div>

        <div class="stats-grid">
          <div class="stat-card glass-card">
            <div class="stat-icon" style="color: #8b5cf6;">
              <DashboardOutlined />
            </div>
            <div class="stat-value">24</div>
            <div class="stat-label">今日任务</div>
          </div>

          <div class="stat-card glass-card">
            <div class="stat-icon" style="color: #3b82f6;">
              <FileTextOutlined />
            </div>
            <div class="stat-value">12</div>
            <div class="stat-label">进行中文档</div>
          </div>

          <div class="stat-card glass-card">
            <div class="stat-icon" style="color: #06b6d4;">
              <TeamOutlined />
            </div>
            <div class="stat-value">8</div>
            <div class="stat-label">团队成员</div>
          </div>
        </div>

        <div class="features-grid">
          <div class="feature-card glass-card">
            <div class="feature-icon">
              <svg viewBox="0 0 48 48" fill="none">
                <rect x="4" y="8" width="16" height="32" rx="2" stroke="url(#featureGradient)" stroke-width="2"/>
                <rect x="28" y="4" width="16" height="36" rx="2" stroke="url(#featureGradient)" stroke-width="2"/>
                <rect x="16" y="16" width="12" height="20" rx="1" fill="url(#featureGradient)" opacity="0.5"/>
                <rect x="40" y="12" width="4" height="28" rx="1" fill="url(#featureGradient)" opacity="0.3"/>
                <defs>
                  <linearGradient id="featureGradient" x1="0" y1="0" x2="48" y2="48">
                    <stop offset="0%" stop-color="#8b5cf6"/>
                    <stop offset="100%" stop-color="#06b6d4"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <h3>工作流编排</h3>
            <p>通过可视化界面创建和运行 AI 工作流</p>
          </div>

          <div class="feature-card glass-card">
            <div class="feature-icon">
              <svg viewBox="0 0 48 48" fill="none">
                <circle cx="24" cy="24" r="16" stroke="url(#featureGradient)" stroke-width="2"/>
                <circle cx="24" cy="24" r="8" fill="url(#featureGradient)" opacity="0.3"/>
                <path d="M24 16V24L30 30" stroke="white" stroke-width="2" stroke-linecap="round"/>
                <circle cx="24" cy="12" r="2" fill="#8b5cf6"/>
                <circle cx="36" cy="24" r="2" fill="#8b5cf6"/>
                <circle cx="24" cy="36" r="2" fill="#8b5cf6"/>
                <circle cx="12" cy="24" r="2" fill="#8b5cf6"/>
              </svg>
            </div>
            <h3>AI 智能问答</h3>
            <p>基于 RAG 的智能知识检索与回答</p>
          </div>

          <div class="feature-card glass-card">
            <div class="feature-icon">
              <svg viewBox="0 0 48 48" fill="none">
                <rect x="8" y="8" width="32" height="32" rx="4" stroke="url(#featureGradient)" stroke-width="2"/>
                <line x1="16" y1="24" x2="32" y2="24" stroke="white" stroke-width="2" stroke-linecap="round"/>
                <line x1="16" y1="16" x2="28" y2="16" stroke="white" stroke-width="2" stroke-linecap="round"/>
                <circle cx="32" cy="32" r="4" fill="#8b5cf6"/>
              </svg>
            </div>
            <h3>文档协作</h3>
            <p>实时多人协作编辑文档</p>
          </div>

          <div class="feature-card glass-card">
            <div class="feature-icon">
              <svg viewBox="0 0 48 48" fill="none">
                <path d="M8 24L18 34L40 12" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="8" cy="24" r="4" fill="white"/>
                <circle cx="40" cy="12" r="4" fill="white"/>
              </svg>
            </div>
            <h3>任务管理</h3>
            <p>高效的看板和甘特图任务管理</p>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.dashboard-container {
  display: flex;
  min-height: 100vh;
  background: var(--bg-gradient);
}

.sidebar {
  width: 260px;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
  border-right: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  position: fixed;
  height: 100vh;
  left: 0;
  top: 0;
  z-index: 10;
}

.logo-section {
  padding: 24px;
  border-bottom: 1px solid var(--glass-border);
}

.logo-icon {
  width: 48px;
  height: 48px;
  margin-bottom: 16px;
  animation: pulse 3s ease-in-out infinite;
}

.app-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.menu {
  flex: 1;
  padding: 16px 12px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 4px;
  border-radius: 8px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;

  &:hover {
    background: rgba(255, 255, 255, 0.05);
    color: var(--text-primary);
  }

  &.router-link-active {
    background: var(--btn-primary-bg);
    color: white;
  }
}

.menu-icon {
  font-size: 18px;
}

.menu-text {
  font-size: 14px;
  font-weight: 500;
}

.user-section {
  padding: 16px;
  border-top: 1px solid var(--glass-border);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  background: var(--btn-primary-bg);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: white;
}

.user-details {
  flex: 1;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.user-role {
  font-size: 12px;
  color: var(--text-tertiary);
}

.logout-button {
  width: 100%;
  padding: 10px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 8px;
  color: #ef4444;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(239, 68, 68, 0.2);
  }
}

.main-content {
  flex: 1;
  margin-left: 260px;
  padding: 32px;
}

.top-bar {
  margin-bottom: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
}

.content {
  max-width: 1200px;
}

.welcome-section {
  text-align: center;
  margin-bottom: 48px;
}

.welcome-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 12px;
  background: linear-gradient(135deg, var(--neon-purple), var(--neon-cyan));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  font-size: 16px;
  color: var(--text-secondary);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-bottom: 48px;
}

.stat-card {
  padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.stat-icon {
  font-size: 32px;
  margin-bottom: 16px;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.feature-card {
  padding: 32px;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
  }
}

.feature-icon {
  width: 64px;
  height: 64px;
  margin-bottom: 20px;
}

.feature-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.feature-card p {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
}

/* Responsive */
@media (max-width: 768px) {
  .sidebar {
    width: 100%;
    height: auto;
    position: fixed;
    bottom: 0;
    left: 0;
    top: auto;
    border-right: none;
    border-top: 1px solid var(--glass-border);
  }

  .main-content {
    margin-left: 0;
    padding: 24px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
