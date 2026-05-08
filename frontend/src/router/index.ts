import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
  },
  {
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/ResetPasswordView.vue'),
  },
  // ── 认证后的布局（含 Sidebar + TopBar） ──
  {
    path: '/',
    component: () => import('@/views/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/ChatPage.vue'),
        meta: { title: '智能问答' },
      },
      {
        path: 'chat/:sessionId',
        name: 'ChatSession',
        component: () => import('@/views/ChatPage.vue'),
        meta: { title: '智能问答' },
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/KnowledgePage.vue'),
        meta: { title: '知识库管理' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const isAuthenticated = authStore.isAuthenticated

  const publicRoutes = ['/login', '/register', '/reset-password']

  // If route requires auth and user is not authenticated, redirect to login
  if (to.matched.some(r => r.meta.requiresAuth) && !isAuthenticated) {
    next('/login')
  } else if (to.path === '/login' && isAuthenticated) {
    next('/dashboard')
  } else if (publicRoutes.includes(to.path) && isAuthenticated) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
