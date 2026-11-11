<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { LoaderCircle, Send } from 'lucide-vue-next'
import ImageUploader from './ImageUploader.vue'
import { createPost, fetchCategories, fetchLocations, toRequestMessage, type Category } from '@/api/posts'
import type { UploadedFile } from '@/api/files'
import { useFeedStore } from '@/stores/feed'
import type { CampusLocation, ContactMode, ItemKind } from '@/types/item'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; published: [title: string] }>()

const feed = useFeedStore()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const categories = ref<Category[]>([])
const locations = ref<CampusLocation[]>([])
const optionsLoaded = ref(false)
const submitting = ref(false)
const error = ref('')
const images = ref<UploadedFile[]>([])

const form = reactive({
  type: 'FOUND' as ItemKind,
  title: '',
  description: '',
  categoryId: null as number | null,
  locationId: null as number | null,
  detail: '',
  brand: '',
  color: '',
  contactMode: 'IN_APP_ONLY' as ContactMode,
  eventTime: new Date() as Date | null,
})

async function loadOptions(): Promise<void> {
  if (optionsLoaded.value) {
    return
  }
  try {
    const [categoryList, locationList] = await Promise.all([fetchCategories(), fetchLocations()])
    categories.value = categoryList
    locations.value = locationList
    optionsLoaded.value = true
  } catch (requestError) {
    error.value = toRequestMessage(requestError)
  }
}

function resetForm(): void {
  form.type = 'FOUND'
  form.title = ''
  form.description = ''
  form.categoryId = null
  form.locationId = null
  form.detail = ''
  form.brand = ''
  form.color = ''
  form.contactMode = 'IN_APP_ONLY'
  form.eventTime = new Date()
  images.value = []
  error.value = ''
}

function validate(): string {
  if (form.title.trim().length < 2) {
    return '标题至少需要 2 个字'
  }
  if (form.description.trim().length < 5) {
    return '描述至少需要 5 个字，写清特征更容易匹配'
  }
  if (!form.categoryId) {
    return '请选择物品分类'
  }
  if (!form.eventTime) {
    return '请选择发生时间'
  }
  if (form.eventTime.getTime() > Date.now() + 10 * 60 * 1000) {
    return '发生时间不能晚于当前时间'
  }
  return ''
}

async function submit(): Promise<void> {
  error.value = ''
  const message = validate()
  if (message) {
    error.value = message
    return
  }
  submitting.value = true
  try {
    await createPost({
      type: form.type,
      title: form.title.trim(),
      description: form.description.trim(),
      categoryId: form.categoryId as number,
      locationId: form.locationId,
      detail: form.detail.trim() || null,
      eventTime: (form.eventTime as Date).toISOString(),
      brand: form.brand.trim() || null,
      color: form.color.trim() || null,
      contactMode: form.contactMode,
      imageIds: images.value.map((image) => image.id),
    })
    const title = form.title.trim()
    visible.value = false
    resetForm()
    feed.bump()
    emit('published', title)
  } catch (requestError) {
    error.value = toRequestMessage(requestError)
  } finally {
    submitting.value = false
  }
}

watch(visible, (open) => {
  if (open) {
    void loadOptions()
    return
  }
  error.value = ''
})

watch(
  form,
  () => {
    error.value = ''
  },
  { deep: true },
)
</script>

<template>
  <el-dialog
    v-model="visible"
    title="发布失物 / 招领"
    width="600px"
    align-center
    :close-on-click-modal="false"
  >
    <form class="publish-form" @submit.prevent="submit">
      <div class="publish-type" role="group" aria-label="信息类型">
        <button
          type="button"
          class="publish-type__option"
          :class="{ 'is-active': form.type === 'FOUND' }"
          @click="form.type = 'FOUND'"
        >
          我捡到了东西
          <small>发布招领</small>
        </button>
        <button
          type="button"
          class="publish-type__option"
          :class="{ 'is-active': form.type === 'LOST' }"
          @click="form.type = 'LOST'"
        >
          我丢了东西
          <small>发布寻物</small>
        </button>
      </div>

      <label class="form-field">
        <span>标题</span>
        <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="例如：图书馆三楼捡到一张校园卡" />
      </label>

      <div class="publish-row">
        <label class="form-field">
          <span>物品分类</span>
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </label>

        <label class="form-field">
          <span>发生时间</span>
          <el-date-picker
            v-model="form.eventTime"
            type="datetime"
            placeholder="选择时间"
            style="width: 100%"
            :clearable="false"
          />
        </label>
      </div>

      <label class="form-field">
        <span>描述</span>
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="写清颜色、品牌、外观特征和当时的情况，方便失主或拾取者核对"
        />
      </label>

      <div class="form-field">
        <span>图片（选填，最多 6 张）</span>
        <ImageUploader v-model="images" :max="6" hint="拍一张清晰的照片，更容易被认领" />
      </div>

      <div class="publish-row">
        <label class="form-field">
          <span>地点</span>
          <el-select v-model="form.locationId" placeholder="选择校园地点（选填）" clearable style="width: 100%">
            <el-option
              v-for="location in locations"
              :key="location.id"
              :label="location.alias || location.name"
              :value="location.id"
            />
          </el-select>
        </label>

        <label class="form-field">
          <span>具体位置</span>
          <el-input v-model="form.detail" maxlength="200" placeholder="例如：三楼靠窗第二排" />
        </label>
      </div>

      <div class="publish-row">
        <label class="form-field">
          <span>品牌</span>
          <el-input v-model="form.brand" maxlength="64" placeholder="选填" />
        </label>

        <label class="form-field">
          <span>颜色</span>
          <el-input v-model="form.color" maxlength="16" placeholder="选填，例如：黑色" />
        </label>
      </div>

      <label class="form-field">
        <span>联系方式</span>
        <el-select v-model="form.contactMode" style="width: 100%">
          <el-option label="仅站内沟通（推荐）" value="IN_APP_ONLY" />
          <el-option label="认领确认后可见" value="AFTER_CLAIM" />
          <el-option label="公开联系方式" value="PUBLIC" />
        </el-select>
      </label>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>

    <template #footer>
      <div class="publish-footer">
        <span class="publish-footer__note">发布后 30 天内有效，请勿填写身份证号等敏感信息。</span>
        <div class="publish-footer__actions">
          <el-button @click="visible = false">取消</el-button>
          <button type="button" class="btn btn--primary" :disabled="submitting" @click="submit">
            <LoaderCircle v-if="submitting" :size="16" class="spin" />
            <Send v-else :size="16" />
            {{ submitting ? '发布中' : '立即发布' }}
          </button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>
