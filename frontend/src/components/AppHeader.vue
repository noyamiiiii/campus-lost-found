<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, PackageSearch } from 'lucide-vue-next'

defineEmits<{ publish: [] }>()

const route = useRoute()
const activeKind = computed(() => (route.query.kind === 'lost' || route.query.kind === 'found' ? route.query.kind : 'all'))
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
      </div>
    </div>
  </header>
</template>

