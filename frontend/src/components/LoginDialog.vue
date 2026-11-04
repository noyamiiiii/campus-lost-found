<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LoaderCircle, LockKeyhole, Phone, ShieldCheck } from 'lucide-vue-next'
import { sendLoginCode } from '@/api/auth'
import { toRequestMessage } from '@/api/posts'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const studentNo = ref('20260001')
const phone = ref('13800000001')
const code = ref('')
const sending = ref(false)
const codeSent = ref(false)
const countdown = ref(0)
const error = ref('')
let countdownTimer: ReturnType<typeof setInterval> | null = null

const dialogVisible = computed({
  get: () => auth.loginOpen,
  set: (value: boolean) => {
    if (value) {
      auth.openLogin()
    } else {
      auth.closeLogin()
    }
  },
})

const codeButtonLabel = computed(() => {
  if (sending.value) {
    return '发送中'
  }
  if (countdown.value > 0) {
    return `${countdown.value}s 后重发`
  }
  return codeSent.value ? '重新获取' : '获取验证码'
})

function startCountdown(seconds: number): void {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  countdown.value = seconds
  countdownTimer = setInterval(() => {
    countdown.value = Math.max(0, countdown.value - 1)
    if (countdown.value === 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

function validateAccount(): boolean {
  if (!/^[A-Za-z0-9]{4,24}$/.test(studentNo.value.trim())) {
    error.value = '请输入 4 到 24 位字母或数字组成的学号'
    return false
  }
  if (!/^1[3-9][0-9]{9}$/.test(phone.value.trim())) {
    error.value = '请输入有效的 11 位手机号'
    return false
  }
  return true
}

async function requestCode(): Promise<void> {
  error.value = ''
  if (!validateAccount() || countdown.value > 0) {
    return
  }
  sending.value = true
  try {
    const result = await sendLoginCode({
      studentNo: studentNo.value.trim(),
      phone: phone.value.trim(),
    })
    codeSent.value = true
    startCountdown(result.retryAfterSeconds)
    if (result.debugCode) {
      code.value = result.debugCode
    }
  } catch (requestError) {
    error.value = toRequestMessage(requestError)
  } finally {
    sending.value = false
  }
}

async function submit(): Promise<void> {
  error.value = ''
  if (!validateAccount()) {
    return
  }
  if (!/^[0-9]{6}$/.test(code.value.trim())) {
    error.value = '请输入 6 位数字验证码'
    return
  }
  try {
    const session = await auth.login({
      studentNo: studentNo.value.trim(),
      phone: phone.value.trim(),
      code: code.value.trim(),
    })
    auth.closeLogin()
    ElMessage.success('登录成功')
    if (session.needsProfileCompletion) {
      await router.push('/profile')
    }
  } catch (requestError) {
    error.value = toRequestMessage(requestError)
  }
}

watch(
  () => auth.loginOpen,
  (open) => {
    if (open) {
      error.value = ''
    }
  },
)

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<template>
  <el-dialog v-model="dialogVisible" title="登录校园失物招领" width="430px" align-center>
    <div class="login-dialog">
      <p class="login-dialog__lead">使用学号和手机号验证身份，首次登录会自动建立账号。</p>

      <div class="demo-account">
        <div>
          <span>本地演示账号</span>
          <strong>20260001</strong>
        </div>
        <div>
          <span>手机号</span>
          <strong>13800000001</strong>
        </div>
        <div>
          <span>固定验证码</span>
          <strong>123456</strong>
        </div>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label class="form-field">
          <span>学号</span>
          <el-input v-model="studentNo" maxlength="24" autocomplete="username" placeholder="请输入学号" />
        </label>

        <label class="form-field">
          <span>手机号</span>
          <el-input v-model="phone" maxlength="11" autocomplete="tel" placeholder="请输入手机号">
            <template #prefix><Phone :size="15" /></template>
          </el-input>
        </label>

        <label class="form-field">
          <span>验证码</span>
          <div class="code-row">
            <el-input v-model="code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位验证码">
              <template #prefix><LockKeyhole :size="15" /></template>
            </el-input>
            <button type="button" class="code-button" :disabled="sending || countdown > 0" @click="requestCode">
              <LoaderCircle v-if="sending" :size="14" class="spin" />
              {{ codeButtonLabel }}
            </button>
          </div>
        </label>

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>

        <button type="submit" class="btn btn--primary login-submit" :disabled="auth.busy">
          <LoaderCircle v-if="auth.busy" :size="16" class="spin" />
          <ShieldCheck v-else :size="16" />
          {{ auth.busy ? '正在验证' : '登录' }}
        </button>
      </form>
    </div>
  </el-dialog>
</template>
