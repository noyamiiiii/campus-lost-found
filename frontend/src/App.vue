<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '@/components/AppHeader.vue'
import LoginDialog from '@/components/LoginDialog.vue'
import PublishDialog from '@/components/PublishDialog.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const publishOpen = ref(false)

function openPublish(): void {
  if (!auth.user) {
    auth.openLogin()
    return
  }
  publishOpen.value = true
}

async function handlePublished(title: string): Promise<void> {
  ElMessage.success(`已发布：${title}`)
  if (route.path !== '/') {
    await router.push('/')
  }
}

onMounted(() => {
  void auth.bootstrap()
})
</script>

<template>
  <div class="app-shell">
    <AppHeader @publish="openPublish" />
    <RouterView />
    <LoginDialog />
    <PublishDialog v-model="publishOpen" @published="handlePublished" />
  </div>
</template>

