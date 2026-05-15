import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { guest: true } },
    { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { guest: true } },
    {
      path: '/',
      component: () => import('@/components/layout/AppLayout.vue'),
      redirect: '/dashboard',
      meta: { requiresAuth: true },
      children: [
        { path: '/dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: '/calendar', component: () => import('@/views/CalendarView.vue') },
        { path: '/reports', component: () => import('@/views/ReportsView.vue') },
        { path: '/recycle-bin', component: () => import('@/views/RecycleBinView.vue') },
        { path: 'projects/:projectId', component: () => import('@/views/ProjectDetailView.vue') },
        { path: 'team/:id', component: () => import('@/views/TeamView.vue') },
        { path: 'dashboard', component: () => import('@/views/DashboardView.vue') }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.token) return '/login'
  if (to.meta.guest && auth.token) return '/'
})

export default router
