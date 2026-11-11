<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { AlertCircle, BellRing, CheckCheck, LoaderCircle } from 'lucide-vue-next'
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notifications'
import { useAuthStore } from '@/stores/auth'
import { formatClaimTime } from '@/types/claim'
import type { NotificationView } from '@/types/notification'

const router = useRouter()
const auth = useAuthStore()

const items = ref<NotificationView[]>([])
const loading = ref(false)
const error = ref('')

const categoryLabels: Record<NotificationView['category'], string> = {
  MATCH: '智能匹配',
  CLAIM: '认领',
  AUDIT: '审核',
  SYSTEM: '系统',
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    items.value = await fetchNotifications()
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function open(item: NotificationView): Promise<void> {
  if (!item.read) {
    try {
      await markNotificationRead(item.id)
      item.read = true
    } catch {
      /* 标记失败不影响查看 */
    }
  }
  if (item.category === 'CLAIM') {
    await router.push('/claims')
  }
}

async function readAll(): Promise<void> {
  try {
    await markAllNotificationsRead()
    items.value = items.value.map((item) => ({ ...item, read: true }))
    ElMessage.success('已全部标为已读')
  } catch (requestError) {
    ElMessage.error(requestError instanceof Error ? requestError.message : '操作失败')
  }
}

onMounted(() => {
  if (!auth.user) {
    void auth.bootstrap().then(() => {
      if (!auth.user) {
        auth.openLogin()
        return
      }
      void load()
    })
    return
  }
  void load()
})
</script>

<template>
  <div class="page notify-page">
    <header class="notify-head">
      <div>
        <h1>通知</h1>
        <p>认领进展、审核结果与系统提示都会出现在这里。</p>
      </div>
      <div class="notify-head__actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button @click="readAll">
          <CheckCheck :size="15" />
          全部已读
        </el-button>
      </div>
    </header>

    <div v-if="loading" class="notify-state"><LoaderCircle :size="18" class="spin" /> 正在加载…</div>

    <div v-else-if="error" class="notify-state notify-state--error" role="alert">
      <AlertCircle :size="18" /> {{ error }}
      <el-button size="small" @click="load">重试</el-button>
    </div>

    <div v-else-if="items.length === 0" class="notify-state">
      <BellRing :size="18" /> 还没有通知
    </div>

    <ul v-else class="notify-list">
      <li v-for="item in items" :key="item.id" class="notify-item" :class="{ 'is-unread': !item.read }">
        <button type="button" class="notify-item__body" @click="open(item)">
          <span class="notify-item__tag">{{ categoryLabels[item.category] }}</span>
          <span class="notify-item__title">{{ item.title }}</span>
          <span class="notify-item__content">{{ item.content }}</span>
          <span class="notify-item__time">{{ formatClaimTime(item.createdAt) }}</span>
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.notify-page {
  max-width: 780px;
  margin: 0 auto;
}

.notify-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.notify-head h1 {
  margin: 0 0 6px;
  font-size: 24px;
}

.notify-head p {
  margin: 0;
  color: var(--muted, #6b7280);
}

.notify-head__actions {
  display: flex;
  gap: 8px;
}

.notify-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 28px 16px;
  color: var(--muted, #6b7280);
  background: #fff;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 12px;
}

.notify-state--error {
  color: #b91c1c;
}

.notify-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
}

.notify-item {
  background: #fff;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 12px;
}

.notify-item.is-unread {
  border-color: #c7d2fe;
  background: #f8faff;
}

.notify-item__body {
  width: 100%;
  display: grid;
  gap: 4px;
  padding: 14px 16px;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.notify-item__tag {
  font-size: 12px;
  color: #4338ca;
}

.notify-item__title {
  font-weight: 600;
  color: var(--ink, #111827);
}

.notify-item__content {
  color: #4b5563;
  font-size: 14px;
  line-height: 1.6;
}

.notify-item__time {
  font-size: 12px;
  color: var(--muted, #9ca3af);
}
</style>