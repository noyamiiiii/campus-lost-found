import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchAiHealth, fetchBackendHealth } from '@/api/health'
import type { ServiceState } from '@/types/health'

const idleState = (): ServiceState => ({ status: 'idle', detail: '尚未检测', latencyMs: null })
const checkingState = (): ServiceState => ({ status: 'checking', detail: '检测中', latencyMs: null })

export const useHealthStore = defineStore('health', () => {
  const backend = ref<ServiceState>(idleState())
  const ai = ref<ServiceState>(idleState())
  const busy = ref(false)
  const lastCheckedAt = ref('')
  const neverChecked = computed(() => lastCheckedAt.value === '')

  async function refresh(): Promise<void> {
    busy.value = true
    backend.value = checkingState()
    ai.value = checkingState()

    const [backendResult, aiResult] = await Promise.all([fetchBackendHealth(), fetchAiHealth()])

    backend.value = {
      status: backendResult.ok ? 'up' : 'down',
      detail: backendResult.detail,
      latencyMs: backendResult.latencyMs,
    }
    ai.value = {
      status: aiResult.ok ? 'up' : 'down',
      detail: aiResult.detail,
      latencyMs: aiResult.latencyMs,
    }
    lastCheckedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    busy.value = false
  }

  return { backend, ai, busy, lastCheckedAt, neverChecked, refresh }
})

