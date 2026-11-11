<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AlertCircle, Inbox, LoaderCircle, PackageSearch } from 'lucide-vue-next'
import { actOnClaim, fetchMyClaims, fetchReceivedClaims, toClaimRequestMessage } from '@/api/claims'
import { useAuthStore } from '@/stores/auth'
import { claimStatusLabels, claimStatusTone, formatClaimTime, type ClaimView } from '@/types/claim'

const router = useRouter()
const auth = useAuthStore()

const tab = ref<'mine' | 'received'>('mine')
const mine = ref<ClaimView[]>([])
const received = ref<ClaimView[]>([])
const loading = ref(false)
const error = ref('')
const actingId = ref('')

const current = computed(() => (tab.value === 'mine' ? mine.value : received.value))

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [mineList, receivedList] = await Promise.all([fetchMyClaims(), fetchReceivedClaims()])
    mine.value = mineList
    received.value = receivedList
  } catch (requestError) {
    error.value = toClaimRequestMessage(requestError)
  } finally {
    loading.value = false
  }
}

async function run(claim: ClaimView, action: 'confirm' | 'reject' | 'reopen' | 'cancel' | 'complete'): Promise<void> {
  const prompts: Record<typeof action, string> = {
    confirm: '同意这次认领，并补充交接提示（可写见面时间地点）',
    reject: '拒绝这次认领，请说明原因',
    reopen: '重新受理这次认领，可补充说明',
    cancel: '撤回这次认领申请',
    complete: '确认已完成线下交接',
  }
  let message = ''
  if (action === 'cancel') {
    await ElMessageBox.confirm('撤回后需要重新申请，确定继续？', '撤回认领', { type: 'warning' })
  } else {
    const result = await ElMessageBox.prompt(prompts[action], '处理认领', {
      inputPlaceholder: action === 'reopen' ? '可留空' : '请填写说明',
      inputValidator: (value) => {
        if (action === 'reopen') {
          return true
        }
        return value && value.trim().length >= 2 ? true : '请至少写 2 个字'
      },
    })
    message = result.value.trim()
  }

  actingId.value = claim.publicId
  try {
    await actOnClaim(claim.publicId, action, message)
    ElMessage.success('处理完成')
    await load()
  } catch (requestError) {
    ElMessage.error(toClaimRequestMessage(requestError))
  } finally {
    actingId.value = ''
  }
}

function openPost(claim: ClaimView): void {
  router.push({ path: '/', query: { focus: claim.postPublicId } })
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
  <div class="page claims-page">
    <header class="claims-head">
      <div>
        <h1>认领处理</h1>
        <p>申请后由发布者核对特征决定是否同意；交接完成请点「确认交接」。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-tabs v-model="tab" class="claims-tabs">
      <el-tab-pane :label="`我发起的（${mine.length}）`" name="mine" />
      <el-tab-pane :label="`我收到的（${received.length}）`" name="received" />
    </el-tabs>

    <div v-if="loading" class="claims-state"><LoaderCircle :size="18" class="spin" /> 正在加载…</div>

    <div v-else-if="error" class="claims-state claims-state--error" role="alert">
      <AlertCircle :size="18" /> {{ error }}
      <el-button size="small" @click="load">重试</el-button>
    </div>

    <div v-else-if="current.length === 0" class="claims-state">
      <Inbox :size="18" />
      {{ tab === 'mine' ? '还没有发起过认领，去首页找找你的物品吧' : '暂时没有收到认领申请' }}
    </div>

    <ul v-else class="claim-list">
      <li v-for="claim in current" :key="claim.publicId" class="claim-card">
        <div class="claim-card__head">
          <button type="button" class="claim-card__title" @click="openPost(claim)">
            <PackageSearch :size="15" />
            {{ claim.postTitle }}
          </button>
          <span class="item-status" :class="`item-status--${claimStatusTone(claim.status)}`">
            {{ claimStatusLabels[claim.status] }}
          </span>
        </div>

        <p class="claim-card__message">{{ claim.message || '（无说明）' }}</p>

        <dl class="claim-card__meta">
          <div><dt>申请人</dt><dd>{{ claim.applicantName || (claim.applicantPublicId ? '校园用户' : '—') }}</dd></div>
          <div><dt>发布者</dt><dd>{{ claim.ownerName || (claim.ownerPublicId ? '校园用户' : '—') }}</dd></div>
          <div><dt>提交</dt><dd>{{ formatClaimTime(claim.createdAt) }}</dd></div>
          <div v-if="claim.contact"><dt>联系</dt><dd>{{ claim.contact }}</dd></div>
          <div><dt>编号</dt><dd>{{ claim.publicId }}</dd></div>
        </dl>

        <div class="claim-card__actions">
          <template v-if="tab === 'received'">
            <el-button
              v-if="claim.status === 'PENDING'"
              type="primary"
              size="small"
              :loading="actingId === claim.publicId"
              @click="run(claim, 'confirm')"
            >
              同意认领
            </el-button>
            <el-button v-if="claim.status === 'PENDING'" size="small" @click="run(claim, 'reject')">拒绝</el-button>
            <el-button v-if="claim.status === 'REJECTED'" size="small" @click="run(claim, 'reopen')">重新受理</el-button>
            <el-button
              v-if="claim.status === 'CONFIRMED'"
              type="primary"
              size="small"
              :loading="actingId === claim.publicId"
              @click="run(claim, 'complete')"
            >
              确认交接
            </el-button>
          </template>
          <template v-else>
            <el-button
              v-if="claim.status === 'PENDING' || claim.status === 'CONFIRMED'"
              size="small"
              :loading="actingId === claim.publicId"
              @click="run(claim, 'cancel')"
            >
              撤回申请
            </el-button>
            <span v-else class="claim-card__hint">该申请已结束</span>
          </template>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.claims-page {
  max-width: 900px;
  margin: 0 auto;
}

.claims-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.claims-head h1 {
  margin: 0 0 6px;
  font-size: 24px;
}

.claims-head p {
  margin: 0;
  color: var(--muted, #6b7280);
}

.claims-tabs {
  margin-bottom: 8px;
}

.claims-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 28px 16px;
  color: var(--muted, #6b7280);
  background: #fff;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 12px;
}

.claims-state--error {
  color: #b91c1c;
}

.claim-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.claim-card {
  padding: 16px 18px;
  background: #fff;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 12px;
}

.claim-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.claim-card__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  padding: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--ink, #111827);
  cursor: pointer;
  text-align: left;
}

.claim-card__message {
  margin: 10px 0;
  color: #374151;
  line-height: 1.6;
  white-space: pre-wrap;
}

.claim-card__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 6px 16px;
  margin: 0 0 12px;
  font-size: 13px;
  color: #4b5563;
}

.claim-card__meta div {
  display: flex;
  gap: 6px;
}

.claim-card__meta dt {
  color: var(--muted, #9ca3af);
}

.claim-card__meta dd {
  margin: 0;
}

.claim-card__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.claim-card__hint {
  font-size: 13px;
  color: var(--muted, #9ca3af);
}
</style>