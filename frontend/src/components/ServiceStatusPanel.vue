<script setup lang="ts">
import { onMounted } from 'vue'
import { BrainCircuit, MonitorSmartphone, RefreshCw, Server } from 'lucide-vue-next'
import { useHealthStore } from '@/stores/health'
import type { ServiceState } from '@/types/health'

const health = useHealthStore()

const statusLabel: Record<ServiceState['status'], string> = {
  idle: '未检测',
  checking: '检测中',
  up: '正常',
  down: '未连接',
}

onMounted(() => {
  if (health.neverChecked) {
    void health.refresh()
  }
})
</script>

<template>
  <section class="rail-card" aria-labelledby="service-status-title">
    <div class="rail-card__head">
      <h2 id="service-status-title">服务状态</h2>
      <button
        type="button"
        class="icon-btn"
        :disabled="health.busy"
        :aria-label="health.busy ? '正在检测' : '重新检测'"
        title="重新检测"
        @click="health.refresh()"
      >
        <RefreshCw :size="15" :class="{ spin: health.busy }" />
      </button>
    </div>

    <div class="service-row">
      <MonitorSmartphone :size="16" class="service-row__icon" />
      <div class="service-row__body">
        <div class="service-row__name">
          <span>前端应用</span>
          <span class="service-row__state"><i class="dot dot--up" />正常</span>
        </div>
        <p class="service-row__detail">Vue 3 · Vite 开发服务器</p>
      </div>
    </div>

    <div class="service-row">
      <Server :size="16" class="service-row__icon" />
      <div class="service-row__body">
        <div class="service-row__name">
          <span>后端 API</span>
          <span class="service-row__state" :class="`is-${health.backend.status}`">
            <i class="dot" :class="`dot--${health.backend.status}`" />
            {{ statusLabel[health.backend.status] }}
          </span>
        </div>
        <p class="service-row__detail">
          {{ health.backend.detail }}
          <template v-if="health.backend.latencyMs !== null"> · {{ health.backend.latencyMs }} ms</template>
        </p>
      </div>
    </div>

    <div class="service-row">
      <BrainCircuit :size="16" class="service-row__icon" />
      <div class="service-row__body">
        <div class="service-row__name">
          <span>AI 服务</span>
          <span class="service-row__state" :class="`is-${health.ai.status}`">
            <i class="dot" :class="`dot--${health.ai.status}`" />
            {{ statusLabel[health.ai.status] }}
          </span>
        </div>
        <p class="service-row__detail">
          {{ health.ai.detail }}
          <template v-if="health.ai.latencyMs !== null"> · {{ health.ai.latencyMs }} ms</template>
        </p>
      </div>
    </div>

    <p class="rail-foot">
      {{ health.lastCheckedAt ? `最近检测 ${health.lastCheckedAt}` : '等待首次检测' }}
    </p>
  </section>
</template>

