<script setup lang="ts">
import { ref } from 'vue'
import AppHeader from '@/components/AppHeader.vue'

const publishOpen = ref(false)
</script>

<template>
  <div class="app-shell">
    <AppHeader @publish="publishOpen = true" />
    <RouterView />

    <el-dialog v-model="publishOpen" title="发布失物 / 招领" width="440px" align-center>
      <div class="publish-dialog">
        <p class="publish-dialog__lead">发布表单会在迭代一（W9）接入，当前骨架用于验证三端工程链路。</p>
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

