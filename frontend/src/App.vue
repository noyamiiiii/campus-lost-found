<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import LoginDialog from '@/components/LoginDialog.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const publishOpen = ref(false)

function openPublish(): void {
  if (!auth.user) {
    auth.openLogin()
    return
  }
  publishOpen.value = true
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

    <el-dialog v-model="publishOpen" title="发布失物 / 招领" width="440px" align-center>
      <div class="publish-dialog">
        <p class="publish-dialog__lead">登录已经接入，发布表单会在下一段迭代中完整落地。</p>
        <ul class="publish-dialog__list">
          <li>物品类别、时间、地点、描述</li>
          <li>图片上传与自动打标</li>
          <li>发布后进入审核队列</li>
        </ul>
      </div>
      <template #footer>
        <el-button type="primary" @click="publishOpen = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

