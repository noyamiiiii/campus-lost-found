<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AlertCircle, LoaderCircle, RefreshCw, Search, ShieldCheck, UserRoundX, UserRoundCheck } from 'lucide-vue-next'
import { fetchAdminSummary, fetchAdminUsers, updateUserStatus } from '@/api/admin'
import { toRequestMessage } from '@/api/posts'
import { useAuthStore } from '@/stores/auth'
import { accountStatusLabels, roleLabels, type AdminSummary, type AdminUser } from '@/types/admin'

const auth = useAuthStore()
const router = useRouter()

const summary = ref<AdminSummary | null>(null)
const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const statusFilter = ref('')
const loading = ref(false)
const changingId = ref('')
const error = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

const isAdmin = computed(() => auth.user?.role === 'ADMIN')

async function loadSummary(): Promise<void> {
  try {
    summary.value = await fetchAdminSummary()
  } catch (requestError) {
    error.value = toRequestMessage(requestError)
  }
}

async function loadUsers(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchAdminUsers({
      keyword: keyword.value,
      status: statusFilter.value,
      page: page.value,
      size: size.value,
    })
    users.value = result.list
    total.value = result.total
  } catch (requestError) {
    error.value = toRequestMessage(requestError)
  } finally {
    loading.value = false
  }
}

async function reload(): Promise<void> {
  await Promise.all([loadSummary(), loadUsers()])
}

async function toggleStatus(user: AdminUser): Promise<void> {
  const nextStatus = user.status === 'BANNED' ? 'ACTIVE' : 'BANNED'
  const actionName = nextStatus === 'BANNED' ? '封禁' : '解封'
  try {
    await ElMessageBox.confirm(
      nextStatus === 'BANNED'
        ? `封禁后 ${user.realName || user.studentNo} 将无法登录，已发布的帖子仍保留。`
        : `解封后 ${user.realName || user.studentNo} 可以重新登录。`,
      `${actionName}账号`,
      { confirmButtonText: actionName, cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  changingId.value = user.publicId
  try {
    const updated = await updateUserStatus(user.publicId, nextStatus)
    users.value = users.value.map((item) => (item.publicId === updated.publicId ? updated : item))
    ElMessage.success(`已${actionName} ${updated.realName || updated.studentNo}`)
    await loadSummary()
  } catch (requestError) {
    ElMessage.error(toRequestMessage(requestError))
  } finally {
    changingId.value = ''
  }
}

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

watch(keyword, () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    page.value = 1
    void loadUsers()
  }, 320)
})

watch(statusFilter, () => {
  page.value = 1
  void loadUsers()
})

watch(page, () => {
  void loadUsers()
})

onMounted(async () => {
  await auth.bootstrap()
  if (!isAdmin.value) {
    return
  }
  await reload()
})
</script>

<template>
  <div class="page admin-page">
    <div class="admin-heading">
      <div>
        <p class="eyebrow">管理后台</p>
        <h1>账号管理</h1>
        <p>查看校园账号、发布情况，并处理违规账号的封禁与解封。</p>
      </div>
      <button type="button" class="icon-btn" title="刷新数据" :disabled="loading" @click="reload">
        <RefreshCw :size="16" :class="{ spin: loading }" />
      </button>
    </div>

    <section v-if="!isAdmin" class="admin-denied">
      <ShieldCheck :size="30" />
      <h2>当前账号没有管理权限</h2>
      <p>管理后台只对管理员账号开放。</p>
      <el-button type="primary" @click="router.push('/')">返回首页</el-button>
    </section>

    <template v-else>
      <dl class="admin-stats">
        <div>
          <dt>账号总数</dt>
          <dd>{{ summary?.totalUsers ?? '—' }}</dd>
        </div>
        <div>
          <dt>正常账号</dt>
          <dd>{{ summary?.activeUsers ?? '—' }}</dd>
        </div>
        <div>
          <dt>已封禁</dt>
          <dd :class="{ 'is-warn': (summary?.bannedUsers ?? 0) > 0 }">{{ summary?.bannedUsers ?? '—' }}</dd>
        </div>
        <div>
          <dt>帖子总数</dt>
          <dd>{{ summary?.totalPosts ?? '—' }}</dd>
        </div>
      </dl>

      <section class="admin-panel">
        <div class="admin-toolbar">
          <label class="admin-search">
            <Search :size="15" />
            <input v-model="keyword" type="search" placeholder="搜索学号、姓名或手机号" />
          </label>
          <el-select v-model="statusFilter" placeholder="全部状态" style="width: 140px">
            <el-option label="全部状态" value="" />
            <el-option label="正常" value="ACTIVE" />
            <el-option label="已封禁" value="BANNED" />
          </el-select>
          <span class="admin-toolbar__count">共 {{ total }} 个账号</span>
        </div>

        <p v-if="error" class="admin-error" role="alert">
          <AlertCircle :size="15" />
          {{ error }}
        </p>

        <div class="admin-table-wrap">
          <table class="admin-table">
            <thead>
              <tr>
                <th scope="col">账号</th>
                <th scope="col">院系</th>
                <th scope="col">角色</th>
                <th scope="col">发布</th>
                <th scope="col">状态</th>
                <th scope="col">最近登录</th>
                <th scope="col"><span class="sr-only">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in users" :key="user.publicId">
                <td>
                  <div class="admin-user">
                    <span class="admin-user__avatar">{{ (user.realName || user.studentNo || '?').slice(0, 1) }}</span>
                    <div>
                      <strong>{{ user.realName || '未完善' }}</strong>
                      <small>学号 {{ user.studentNo || '—' }} · {{ user.phoneMasked || '未绑定手机' }}</small>
                    </div>
                  </div>
                </td>
                <td>{{ user.dept || '—' }}</td>
                <td>{{ roleLabels[user.role] || user.role }}</td>
                <td>{{ user.postCount }}</td>
                <td>
                  <span class="admin-status" :class="`admin-status--${user.status.toLowerCase()}`">
                    {{ accountStatusLabels[user.status] || user.status }}
                  </span>
                </td>
                <td>{{ formatDate(user.lastLoginAt) }}</td>
                <td class="admin-table__action">
                  <button
                    type="button"
                    class="admin-action"
                    :class="{ 'is-restore': user.status === 'BANNED' }"
                    :disabled="user.role === 'ADMIN' || changingId === user.publicId"
                    @click="toggleStatus(user)"
                  >
                    <LoaderCircle v-if="changingId === user.publicId" :size="14" class="spin" />
                    <UserRoundCheck v-else-if="user.status === 'BANNED'" :size="14" />
                    <UserRoundX v-else :size="14" />
                    {{ user.status === 'BANNED' ? '解封' : '封禁' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>

          <p v-if="!loading && users.length === 0" class="admin-empty">没有匹配的账号</p>
        </div>

        <div class="admin-pager">
          <el-pagination
            v-model:current-page="page"
            :page-size="size"
            :total="total"
            layout="prev, pager, next"
            background
          />
        </div>
      </section>
    </template>
  </div>
</template>
