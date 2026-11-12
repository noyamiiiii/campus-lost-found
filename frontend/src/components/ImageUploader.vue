<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ImagePlus, LoaderCircle, X } from 'lucide-vue-next'
import { uploadImage, type UploadedFile } from '@/api/files'

const props = withDefaults(
  defineProps<{
    modelValue: UploadedFile[]
    max?: number
    hint?: string
    single?: boolean
    kind?: string
  }>(),
  { max: 6, hint: '支持 JPG / PNG / WebP，单张不超过 5MB', single: false, kind: '' },
)

const emit = defineEmits<{
  'update:modelValue': [value: UploadedFile[]]
  /** 单张上传完成，父组件据此做「智能填写」 */
  analyzed: [file: UploadedFile]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(0)

const limit = computed(() => (props.single ? 1 : props.max))
const reached = computed(() => props.modelValue.length >= limit.value)

function pick(): void {
  if (reached.value) {
    ElMessage.warning(`最多上传 ${limit.value} 张图片`)
    return
  }
  inputRef.value?.click()
}

async function onChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (files.length === 0) {
    return
  }
  const room = limit.value - props.modelValue.length
  const accepted = files.slice(0, room)
  if (files.length > room) {
    ElMessage.warning(`最多上传 ${limit.value} 张图片，超出部分已忽略`)
  }
  const next = [...props.modelValue]
  for (const file of accepted) {
    if (file.size > 5 * 1024 * 1024) {
      ElMessage.error(`${file.name} 超过 5MB，已跳过`)
      continue
    }
    uploading.value += 1
    try {
      const uploaded = await uploadImage(file, { kind: props.kind })
      next.push(uploaded)
      emit('analyzed', uploaded)
      if (props.single) {
        break
      }
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '上传失败，请重试')
    } finally {
      uploading.value -= 1
    }
  }
  emit('update:modelValue', next.slice(0, limit.value))
}

function remove(index: number): void {
  const next = props.modelValue.filter((_, i) => i !== index)
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="uploader">
    <div class="uploader__grid">
      <figure v-for="(image, index) in modelValue" :key="image.id" class="uploader__item">
        <img :src="image.url" :alt="`已上传图片 ${index + 1}`" loading="lazy" />
        <button type="button" class="uploader__remove" aria-label="移除图片" @click="remove(index)">
          <X :size="13" />
        </button>
      </figure>

      <button
        v-if="!reached"
        type="button"
        class="uploader__add"
        :disabled="uploading > 0"
        @click="pick"
      >
        <LoaderCircle v-if="uploading > 0" :size="18" class="spin" />
        <ImagePlus v-else :size="18" />
        <span>{{ uploading > 0 ? '上传中' : '添加图片' }}</span>
      </button>
    </div>
    <p class="uploader__hint">{{ hint }}（{{ modelValue.length }}/{{ limit }}）</p>
    <input ref="inputRef" type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple hidden @change="onChange" />
  </div>
</template>

<style scoped>
.uploader__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.uploader__item {
  position: relative;
  width: 88px;
  height: 88px;
  margin: 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--line, #e5e7eb);
}

.uploader__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.uploader__remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: rgba(17, 24, 39, 0.65);
  color: #fff;
  cursor: pointer;
}

.uploader__add {
  width: 88px;
  height: 88px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px dashed var(--line, #d1d5db);
  border-radius: 10px;
  background: #fafafa;
  color: var(--muted, #6b7280);
  font-size: 12px;
  cursor: pointer;
}

.uploader__add:disabled {
  cursor: progress;
  opacity: 0.7;
}

.uploader__hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--muted, #6b7280);
}
</style>