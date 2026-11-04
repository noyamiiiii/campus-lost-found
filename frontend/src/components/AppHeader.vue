<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChevronDown, LogIn, PackageSearch, Plus, UserRound } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

defineEmits<{ publish: [] }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activeKind = computed(() => (route.query.kind === 'lost' || route.query.kind === 'found' ? route.query.kind : 'all'))

async function handleAccountCommand(command: string): Promise<void> {
  if (command === 'profile') {
    await router.push('/profile')
  }
  if (command === 'logout') {
    await auth.logout()
    ElMessage.success('已退出登录')
    if (route.path === '/profile') {
      await router.push('/')
    }
  }
}
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
      </nav>

      <div class="app-header__actions">
        <span class="env-tag">演示环境</span>
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
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <button v-else type="button" class="btn btn--secondary" @click="auth.openLogin">
          <LogIn :size="16" />
          登录
        </button>
      </div>
    </div>
  </header>
</template>

