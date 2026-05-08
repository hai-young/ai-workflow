<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  DashboardOutlined, FileTextOutlined, TeamOutlined,
  MessageOutlined, DatabaseOutlined,
} from '@ant-design/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const username = computed(() => authStore.username)

const statCards = [
  { icon: DashboardOutlined, value: '24', label: '今日任务', color: '#8b5cf6' },
  { icon: FileTextOutlined, value: '12', label: '进行中文档', color: '#3b82f6' },
  { icon: TeamOutlined, value: '8', label: '团队成员', color: '#06b6d4' },
]

const featureCards = [
  {
    icon: MessageOutlined,
    title: 'AI 智能问答',
    desc: '基于 RAG 的智能知识检索与回答，支持混合检索和重排序',
    path: '/chat',
  },
  {
    icon: DatabaseOutlined,
    title: '知识库管理',
    desc: '上传文档、管理索引、监控检索质量',
    path: '/knowledge',
  },
  {
    icon: DashboardOutlined,
    title: '工作流编排',
    desc: '通过可视化界面创建和运行 AI 工作流',
    path: '/dashboard',
  },
  {
    icon: TeamOutlined,
    title: '任务管理',
    desc: '高效的看板和甘特图任务管理',
    path: '/dashboard',
  },
]
</script>

<template>
  <div class="dashboard-content">
    <div class="welcome-section">
      <h1 class="welcome-title">{{ `欢迎回来，${username}！` }}</h1>
      <p class="welcome-subtitle">开始您的高效协作之旅</p>
    </div>

    <div class="stats-grid">
      <div v-for="stat in statCards" :key="stat.label" class="stat-card glass-card">
        <div class="stat-icon" :style="{ color: stat.color }">
          <component :is="stat.icon" />
        </div>
        <div class="stat-value">{{ stat.value }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </div>
    </div>

    <div class="features-grid">
      <div
        v-for="feature in featureCards"
        :key="feature.title"
        class="feature-card glass-card"
        @click="router.push(feature.path)"
      >
        <div class="feature-icon">
          <component :is="feature.icon" />
        </div>
        <h3>{{ feature.title }}</h3>
        <p>{{ feature.desc }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard-content {
  padding: 32px;
  max-width: 1200px;
}

.welcome-section {
  text-align: center;
  margin-bottom: 48px;
}

.welcome-title {
  font-size: 32px;
  font-weight: 700;
  color: #e8ecf1;
  margin: 0 0 12px;
  background: linear-gradient(135deg, #8b5cf6, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  font-size: 16px;
  color: #848e9c;
  margin: 0;
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
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(10px);
  border: 1px solid #252b33;
  border-radius: 12px;
}

.stat-icon {
  font-size: 32px;
  margin-bottom: 16px;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  color: #e8ecf1;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #848e9c;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.feature-card {
  padding: 32px;
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(10px);
  border: 1px solid #252b33;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
    border-color: #3b82f6;
  }
}

.feature-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: #8b5cf6;
  margin-bottom: 20px;
  background: rgba(139, 92, 246, 0.08);
  border-radius: 12px;
}

.feature-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: #e8ecf1;
  margin: 0 0 10px;
}

.feature-card p {
  font-size: 14px;
  color: #848e9c;
  line-height: 1.6;
  margin: 0;
}

@media (max-width: 768px) {
  .dashboard-content {
    padding: 20px;
  }
  .stats-grid {
    grid-template-columns: 1fr;
  }
  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
