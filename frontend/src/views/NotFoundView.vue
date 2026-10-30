<script setup lang="ts">
import { ArrowLeft } from 'lucide-vue-next'
</script>

<template>
  <div class="page not-found">
    <p class="not-found__code">404</p>
    <h1>这个页面不存在</h1>
    <p class="not-found__text">链接可能已经失效，或记录已被发布者撤回。</p>
    <RouterLink to="/" class="btn btn--primary">
      <ArrowLeft :size="16" />
      返回首页
    </RouterLink>
  </div>
</template>

