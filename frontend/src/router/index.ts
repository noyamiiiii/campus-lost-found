import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import ProfileView from '@/views/ProfileView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { title: '失物招领' },
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfileView,
      meta: { title: '个人中心' },
    },
    {
      path: '/claims',
      name: 'claims',
      component: () => import('@/views/ClaimsView.vue'),
      meta: { title: '认领处理', requiresAuth: true },
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/NotificationsView.vue'),
      meta: { title: '通知', requiresAuth: true },
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/AdminView.vue'),
      meta: { title: '账号管理', requiresAdmin: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView,
      meta: { title: '页面不存在' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAdmin && !to.meta.requiresAuth) {
    return true
  }
  const auth = useAuthStore()
  await auth.bootstrap()
  if (!auth.user) {
    auth.openLogin()
    return { path: '/' }
  }
  if (to.meta.requiresAdmin && auth.user.role !== 'ADMIN') {
    return { path: '/' }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 校园失物招领` : '校园失物招领'
})

export default router

