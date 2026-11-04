<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { BadgeCheck, LoaderCircle, LogIn, MessageCircle, Phone, Save, School } from 'lucide-vue-next'
import { toRequestMessage } from '@/api/posts'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const form = reactive({
  realName: '',
  dept: '',
  dorm: '',
  wechat: '',
})
const error = ref('')
const saved = ref(false)

function fillForm(): void {
  form.realName = auth.user?.realName ?? ''
  form.dept = auth.user?.dept ?? ''
  form.dorm = auth.user?.dorm ?? ''
  form.wechat = auth.user?.wechat ?? ''
}

async function save(): Promise<void> {
  error.value = ''
  saved.value = false
  if (!form.realName.trim()) {
    error.value = '请填写姓名'
    return
  }
  if (!form.dept.trim()) {
    error.value = '请填写院系'
    return
  }
  try {
    await auth.saveProfile({
      realName: form.realName.trim(),
      dept: form.dept.trim(),
      dorm: form.dorm.trim() || null,
      wechat: form.wechat.trim() || null,
    })
    saved.value = true
  } catch (requestError) {
    error.value = toRequestMessage(requestError)
  }
}

watch(() => auth.user, fillForm, { immediate: true })

onMounted(async () => {
  await auth.bootstrap()
  fillForm()
})
</script>

<template>
  <div class="page profile-page">
    <div class="profile-heading">
      <div>
        <p class="eyebrow">账号与个人资料</p>
        <h1>个人中心</h1>
        <p>维护校内身份信息，后续发布、认领和站内沟通都会使用这里的资料。</p>
      </div>
    </div>

    <el-skeleton v-if="!auth.initialized" :rows="7" animated />

    <section v-else-if="!auth.user" class="profile-guest">
      <LogIn :size="30" />
      <h2>登录后查看个人资料</h2>
      <p>登录状态已失效，或你还没有登录。</p>
      <el-button type="primary" @click="auth.openLogin">立即登录</el-button>
    </section>

    <div v-else class="profile-layout">
      <aside class="identity-panel">
        <div class="identity-panel__avatar">{{ auth.displayName.slice(0, 1) }}</div>
        <h2>{{ auth.displayName }}</h2>
        <p class="identity-panel__student">学号 {{ auth.user.studentNo }}</p>

        <div class="identity-status">
          <span><BadgeCheck :size="15" />手机号已验证</span>
          <span v-if="auth.user.profileComplete"><BadgeCheck :size="15" />资料已完善</span>
        </div>

        <dl class="identity-meta">
          <div><dt><Phone :size="14" />手机号</dt><dd>{{ auth.user.phoneMasked }}</dd></div>
          <div><dt><School :size="14" />院系</dt><dd>{{ auth.user.dept || '待补充' }}</dd></div>
          <div><dt><MessageCircle :size="14" />微信</dt><dd>{{ auth.user.wechat || '未填写' }}</dd></div>
        </dl>

        <p class="identity-note">真实姓名和院系只用于校内身份核对，不会作为帖子公开信息展示。</p>
      </aside>

      <section class="profile-form-panel">
        <div class="section-heading">
          <div>
            <h2>基本资料</h2>
            <p>带明确用途的信息才会被保存。</p>
          </div>
          <span v-if="!auth.user.profileComplete" class="status-pill">待完善</span>
        </div>

        <form class="profile-form" @submit.prevent="save">
          <label class="form-field">
            <span>姓名</span>
            <el-input v-model="form.realName" maxlength="64" placeholder="请输入真实姓名" />
          </label>

          <label class="form-field">
            <span>院系或部门</span>
            <el-input v-model="form.dept" maxlength="64" placeholder="例如：计算机学院" />
          </label>

          <label class="form-field">
            <span>宿舍</span>
            <el-input v-model="form.dorm" maxlength="64" placeholder="选填，例如：东区 3 号楼" />
          </label>

          <label class="form-field">
            <span>微信号</span>
            <el-input v-model="form.wechat" maxlength="64" placeholder="选填，仅按隐私设置展示" />
          </label>

          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <p v-if="saved" class="form-success" role="status">个人资料已保存</p>

          <div class="profile-form__actions">
            <el-button type="primary" native-type="submit" :disabled="auth.busy">
              <LoaderCircle v-if="auth.busy" :size="15" class="spin" />
              <Save v-else :size="15" />
              {{ auth.busy ? '保存中' : '保存资料' }}
            </el-button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>
