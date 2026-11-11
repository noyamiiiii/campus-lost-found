<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { LoaderCircle, Send } from 'lucide-vue-next'
import ImageUploader from './ImageUploader.vue'
import { createClaim, toClaimRequestMessage } from '@/api/claims'
import type { UploadedFile } from '@/api/files'
import type { ClaimView } from '@/types/claim'

const props = defineProps<{
  modelValue: boolean
  post: { publicId: string; title: string; publisherName: string } | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submitted: [claim: ClaimView]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const submitting = ref(false)
const error = ref('')
const evidence = ref<UploadedFile[]>([])

const form = reactive({
  message: '',
  contact: '',
})

function reset(): void {
  form.message = ''
  form.contact = ''
  evidence.value = []
  error.value = ''
}

async function submit(): Promise<void> {
  error.value = ''
  if (!props.post) {
    return
  }
  if (form.message.trim().length < 5) {
    error.value = '请至少写 5 个字，说明你能佐证物品归属的细节'
    return
  }
  submitting.value = true
  try {
    const claim = await createClaim(props.post.publicId, {
      message: form.message.trim(),
      contact: form.contact.trim() || null,
      evidenceFileId: evidence.value[0]?.id ?? null,
    })
    ElMessage.success('认领申请已提交，请留意通知')
    visible.value = false
    reset()
    emit('submitted', claim)
  } catch (requestError) {
    error.value = toClaimRequestMessage(requestError)
  } finally {
    submitting.value = false
  }
}

watch(visible, (open) => {
  if (!open) {
    reset()
  }
})
</script>

<template>
  <el-dialog v-model="visible" title="申请认领" width="520px" align-center :close-on-click-modal="false">
    <div v-if="post" class="claim-dialog">
      <p class="claim-dialog__post">
        <span class="claim-dialog__label">物品</span>
        {{ post.title }}
        <small>（{{ post.publisherName }} 发布）</small>
      </p>

      <label class="form-field">
        <span>归属说明</span>
        <el-input
          v-model="form.message"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="描述物品独有的特征、丢失经过等，方便发布者核对"
        />
      </label>

      <label class="form-field">
        <span>联系方式（选填，仅发布者可见）</span>
        <el-input v-model="form.contact" maxlength="120" placeholder="例如：微信 clf2026 / 手机号" />
      </label>

      <div class="form-field">
        <span>佐证图片（选填）</span>
        <ImageUploader v-model="evidence" single hint="可上传购买凭证、同款照片等" />
      </div>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <button type="button" class="btn btn--primary" :disabled="submitting" @click="submit">
        <LoaderCircle v-if="submitting" :size="16" class="spin" />
        <Send v-else :size="16" />
        {{ submitting ? '提交中' : '提交申请' }}
      </button>
    </template>
  </el-dialog>
</template>

<style scoped>
.claim-dialog__post {
  margin: 0 0 14px;
  font-weight: 600;
  color: var(--ink, #111827);
}

.claim-dialog__label {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 12px;
  font-weight: 500;
}
</style>