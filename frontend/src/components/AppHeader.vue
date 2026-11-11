<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  ChevronDown,
  ClipboardCheck,
  LogIn,
  PackageSearch,
  Plus,
  ShieldCheck,
  UserRound,
} from 'lucide-vue-next'
import { fetchUnreadCount } from '@/api/notifications'
import { useAuthStore } from '@/stores/auth'

defineEmits<{ publish: [] }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activeKind = computed(() => (route.query.kind === 'lost' || route.query.kind === 'found' ? route.query.kind : 'all'))
const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const unread = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

async function refreshUnread(): Promise<void> {
  if (!auth.user) {
    unread.value = 0
    return
  }
  try {
    unread.value = await fetchUnreadCount()
  } catch {
    unread.value = 0
  }
}

async function handleAccountCommand(command: string): Promise<void> {
  if (command === 'profile') {
    await router.push('/profile')
  }
  if (command === 'claims') {
    await router.push('/claims')
  }
  if (command === 'notifications') {
    await router.push('/notifications')
  }
  if (command === 'admin') {
    await router.push('/admin')
  }
  if (command === 'logout') {
    await auth.logout()
    ElMessage.success('已退出登录')
    if (route.path !== '/') {
      await router.push('/')
    }
  }
}

watch(
  () => auth.user,
  () => {
    void refreshUnread()
  },
  { immediate: true },
)

watch(
  () => route.fullPath,
  () => {
    void refreshUnread()
  },
)

onMounted(() => {
  void refreshUnread()
  timer = setInterval(() => {
    void refreshUnread()
  }, 60000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <RouterLink to="/" class="brand" aria-label="校园失物招领首页">
        <span class="brand__mark" aria-hidden="true">
          <PackageSearch :size="19" :stroke-width="2" />
        </span>
        <span class="brand__text">
          校园失物招领
          <span class="brand__sub">Campus Lost &amp; Found</span>
        </span>
      </RouterLink>

      <nav class="main-nav" aria-label="主导航">
        <RouterLink to="/" class="main-nav__link" :class="{ 'is-active': activeKind === 'all' }">全部</RouterLink>
        <RouterLink :to="{ path: '/', query: { kind: 'lost' } }" class="main-nav__link" :class="{ 'is-active': activeKind === 'lost' }">
          寻物启事
        </RouterLink>
        <RouterLink :to="{ path: '/', query: { kind: 'found' } }" class="main-nav__link" :class="{ 'is-active': activeKind === 'found' }">
          失物招领
        </RouterLink>
        <RouterLink v-if="auth.user" to="/claims" class="main-nav__link" :class="{ 'is-active': route.path === '/claims' }">
          认领处理
        </RouterLink>
      </nav>

      <div class="app-header__actions">
        <span class="env-tag">演示环境</span>

        <RouterLink v-if="auth.user" to="/notifications" class="bell" :aria-label="unread > 0 ? `通知，${unread} 条未读` : '通知'">
          <Bell :size="18" />
          <span v-if="unread > 0" class="bell__badge">{{ unread > 99 ? '99+' : unread }}</span>
        </RouterLink>

        <button type="button" class="btn btn--primary" @click="$emit('publish')">
          <Plus :size="16" />
          发布
        </button>

        <el-dropdown v-if="auth.user" trigger="click" @command="handleAccountCommand">
          <button type="button" class="account-trigger">
            <span class="account-trigger__avatar">{{ auth.displayName.slice(0, 1) }}</span>
            <span class="account-trigger__name">{{ auth.displayName }}</span>
            <ChevronDown :size="14" />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <UserRound :size="15" />
                个人中心
              </el-dropdown-item>
              <el-dropdown-item command="claims">
                <ClipboardCheck :size="15" />
                认领处理
              </el-dropdown-item>
              <el-dropdown-item command="notifications">
                <Bell :size="15" />
                通知{{ unread > 0 ? `（${unread}）` : '' }}
              </el-dropdown-item>
              <el-dropdown-item v-if="isAdmin" command="admin">
                <ShieldCheck :size="15" />
                管理后台
              </el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <button v-else type="button" class="btn btn--secondary" @click="auth.openLogin()">
          <LogIn :size="16" />
          登录
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.bell {
  position: relative;
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: #374151;
}

.bell:hover {
  background: #f3f4f6;
}

.bell__badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 9px;
  background: #dc2626;
  color: #fff;
  font-size: 11px;
  line-height: 17px;
  text-align: center;
}
</style>