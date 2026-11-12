<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { AlertCircle, ChevronDown, LoaderCircle, Search } from 'lucide-vue-next'
import ItemCard from '@/components/ItemCard.vue'
import ClaimDialog from '@/components/ClaimDialog.vue'
import ServiceStatusPanel from '@/components/ServiceStatusPanel.vue'
import { fetchCategories, fetchPostDetail, fetchPostMatches,
  fetchSimilarImages, fetchPosts, toRequestMessage, type Category, type PostMatch, type VisualMatch } from '@/api/posts'
import { useAuthStore } from '@/stores/auth'
import { useFeedStore } from '@/stores/feed'
import {
  contactModeLabels,
  formatEventTime,
  itemKindLabels,
  itemStatusLabels,
  itemStatusTone,
  type ItemDetail,
  type ItemKind,
  type ItemSummary,
} from '@/types/item'

const route = useRoute()
const feed = useFeedStore()
const auth = useAuthStore()

const keyword = ref('')
const categoryId = ref(0)
const categories = ref<Category[]>([])
const items = ref<ItemSummary[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const moreError = ref('')

const selectedSummary = ref<ItemSummary | null>(null)
const selectedDetail = ref<ItemDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

let listRequest = 0
let detailRequest = 0
let searchTimer: ReturnType<typeof setTimeout> | null = null

const activeKind = computed<ItemKind | undefined>(() => {
  if (route.query.kind === 'lost') {
    return 'LOST'
  }
  if (route.query.kind === 'found') {
    return 'FOUND'
  }
  return undefined
})

const headline = computed(() => {
  if (activeKind.value === 'LOST') {
    return '寻物启事'
  }
  if (activeKind.value === 'FOUND') {
    return '失物招领'
  }
  return '最新记录'
})

const waitingCount = computed(() => items.value.filter((item) => item.status === 'ACTIVE').length)
const lostCount = computed(() => items.value.filter((item) => item.type === 'LOST').length)

const detailOpen = computed({
  get: () => selectedSummary.value !== null,
  set: (value: boolean) => {
    if (!value) {
      selectedSummary.value = null
      selectedDetail.value = null
      detailError.value = ''
    }
  },
})

const detailLocation = computed(() => {
  if (selectedDetail.value) {
    return [selectedDetail.value.locationName, selectedDetail.value.locationDetail].filter(Boolean).join(' · ')
  }
  return selectedSummary.value?.locationName || '地点待补充'
})

const detailImages = computed(() => {
  const images = selectedDetail.value?.images ?? selectedSummary.value?.images ?? []
  return images.map((image) => image.url)
})

const matches = ref<PostMatch[]>([])
const matchesLoading = ref(false)
const visualMatches = ref<VisualMatch[]>([])
const visualLoading = ref(false)

// 已认领/已归还的记录不再允许重复发起认领
const canClaim = computed(() => {
  const status = selectedSummary.value?.status
  return status === 'ACTIVE' || status === 'CLAIMING'
})

const claimOpen = ref(false)
const claimTarget = computed(() => {
  if (!selectedSummary.value) {
    return null
  }
  return {
    publicId: selectedSummary.value.publicId,
    title: selectedSummary.value.title,
    publisherName: selectedSummary.value.publisherName,
  }
})

function startClaim(): void {
  if (!auth.user) {
    auth.openLogin()
    return
  }
  claimOpen.value = true
}

function onClaimSubmitted(): void {
  ElMessage.success('已提交认领申请，后续进展会出现在通知里')
  void reload()
}

async function loadCategories(): Promise<void> {
  try {
    categories.value = await fetchCategories()
  } catch {
    categories.value = []
  }
}

async function reload(): Promise<void> {
  const request = ++listRequest
  loading.value = true
  loadingMore.value = false
  error.value = ''
  moreError.value = ''
  items.value = []
  nextCursor.value = null
  hasMore.value = false

  try {
    const page = await fetchPosts({
      type: activeKind.value,
      categoryId: categoryId.value > 0 ? categoryId.value : undefined,
      keyword: keyword.value,
      size: 10,
    })
    if (request !== listRequest) {
      return
    }
    items.value = page.list
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (requestError) {
    if (request === listRequest) {
      error.value = toRequestMessage(requestError)
    }
  } finally {
    if (request === listRequest) {
      loading.value = false
    }
  }
}

async function loadMore(): Promise<void> {
  if (!hasMore.value || !nextCursor.value || loadingMore.value) {
    return
  }
  const request = listRequest
  loadingMore.value = true
  moreError.value = ''
  try {
    const page = await fetchPosts({
      type: activeKind.value,
      categoryId: categoryId.value > 0 ? categoryId.value : undefined,
      keyword: keyword.value,
      cursor: nextCursor.value,
      size: 10,
    })
    if (request !== listRequest) {
      return
    }
    items.value = [...items.value, ...page.list]
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (requestError) {
    if (request === listRequest) {
      moreError.value = toRequestMessage(requestError)
    }
  } finally {
    if (request === listRequest) {
      loadingMore.value = false
    }
  }
}

function openDetail(item: ItemSummary): void {
  selectedSummary.value = item
  selectedDetail.value = null
  detailError.value = ''
  void loadDetail(item.publicId)
}

async function loadDetail(publicId: string): Promise<void> {
  const request = ++detailRequest
  detailLoading.value = true
  detailError.value = ''
  matches.value = []
  matchesLoading.value = true
  visualMatches.value = []
  visualLoading.value = true
  try {
    const detail = await fetchPostDetail(publicId)
    if (request === detailRequest) {
      selectedDetail.value = detail
    }
  } catch (requestError) {
    if (request === detailRequest) {
      detailError.value = toRequestMessage(requestError)
    }
  } finally {
    if (request === detailRequest) {
      detailLoading.value = false
    }
  }

  try {
    // 候选匹配失败不影响详情展示，静默降级为“暂无候选”。
    const list = await fetchPostMatches(publicId)
    if (request === detailRequest) {
      matches.value = list
    }
  } catch {
    if (request === detailRequest) {
      matches.value = []
    }
  } finally {
    if (request === detailRequest) {
      matchesLoading.value = false
    }
  }

  try {
    // 以图搜图同样静默降级：没有图片或服务异常时列表为空。
    const visual = await fetchSimilarImages(publicId)
    if (request === detailRequest) {
      visualMatches.value = visual
    }
  } catch {
    if (request === detailRequest) {
      visualMatches.value = []
    }
  } finally {
    if (request === detailRequest) {
      visualLoading.value = false
    }
  }
}

watch([keyword, categoryId], () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    void reload()
  }, 320)
})

watch(
  () => route.query.kind,
  () => {
    void reload()
  },
)

watch(
  () => feed.version,
  () => {
    void reload()
  },
)

onMounted(() => {
  void loadCategories()
  void reload()
})

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})
</script>

<template>
  <div class="page">
    <section class="page-intro">
      <div>
        <h1>找回每一件重要的东西</h1>
        <p>校内失物统一登记、检索与认领，减少跑腿和重复询问。先看最新记录，再按类别缩小范围。</p>
      </div>
      <dl class="stat-strip">
        <div>
          <dt>本页记录</dt>
          <dd>{{ items.length }}</dd>
        </div>
        <div>
          <dt>本页待认领</dt>
          <dd>{{ waitingCount }}</dd>
        </div>
        <div>
          <dt>本页寻物</dt>
          <dd>{{ lostCount }}</dd>
        </div>
      </dl>
    </section>

    <div class="workspace">
      <main class="feed">
        <div class="toolbar">
          <el-input
            v-model="keyword"
            class="toolbar__search"
            placeholder="搜索物品或描述"
            clearable
            :prefix-icon="Search"
          />
          <el-select v-model="categoryId" class="toolbar__select" aria-label="按类别筛选">
            <el-option label="全部类别" :value="0" />
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </div>

        <div class="feed__head">
          <h2>{{ headline }}</h2>
          <span class="feed-note">{{ loading ? '正在同步' : `已加载 ${items.length} 条` }}</span>
        </div>

        <div v-if="error && !loading" class="feed-state" role="alert">
          <AlertCircle :size="24" />
          <p>{{ error }}</p>
          <el-button @click="reload">重新加载</el-button>
        </div>

        <el-skeleton v-else-if="loading" class="feed-skeleton" :rows="6" animated />

        <template v-else>
          <div v-if="items.length" class="item-list">
            <ItemCard v-for="item in items" :key="item.publicId" :item="item" @open="openDetail" />
          </div>
          <p v-else class="feed__empty">没有匹配的记录，换个关键词或类别试试。</p>

          <div v-if="hasMore || moreError" class="feed__footer">
            <p v-if="moreError" class="feed__footer-error">{{ moreError }}</p>
            <button
              v-if="hasMore"
              type="button"
              class="load-more"
              :disabled="loadingMore"
              @click="loadMore"
            >
              <LoaderCircle v-if="loadingMore" :size="16" class="spin" />
              <ChevronDown v-else :size="16" />
              {{ loadingMore ? '正在加载' : moreError ? '重试加载更多' : '加载更多' }}
            </button>
          </div>
        </template>
      </main>

      <aside class="rail">
        <ServiceStatusPanel />

        <section class="rail-card" aria-labelledby="claim-flow-title">
          <div class="rail-card__head">
            <h2 id="claim-flow-title">认领流程</h2>
          </div>
          <ol class="steps">
            <li><span class="steps__num">1</span><span>发布招领或寻物信息，补充物品特征</span></li>
            <li><span class="steps__num">2</span><span>系统匹配候选记录，通知双方</span></li>
            <li><span class="steps__num">3</span><span>线上核对描述与图片</span></li>
            <li><span class="steps__num">4</span><span>线下核销码交接，完成归还</span></li>
          </ol>
        </section>
      </aside>
    </div>

    <el-drawer v-model="detailOpen" :with-header="false" size="420px">
      <div v-if="selectedSummary" class="detail">
        <div class="detail__tags">
          <span class="kind-tag" :class="`kind-tag--${selectedSummary.type.toLowerCase()}`">
            {{ itemKindLabels[selectedSummary.type] }}
          </span>
          <span class="item-status" :class="`item-status--${itemStatusTone(selectedSummary.status)}`">
            {{ itemStatusLabels[selectedSummary.status] }}
          </span>
        </div>
        <h2 class="detail__title">{{ selectedSummary.title }}</h2>
        <p class="detail__desc">{{ selectedSummary.description }}</p>

        <div v-if="detailImages.length" class="detail__gallery">
          <img v-for="(url, index) in detailImages" :key="url" :src="url" :alt="`${selectedSummary.title} 图片 ${index + 1}`" />
        </div>

        <el-skeleton v-if="detailLoading" :rows="5" animated />

        <div v-else-if="detailError" class="detail-state" role="alert">
          <AlertCircle :size="20" />
          <p>{{ detailError }}</p>
          <el-button size="small" @click="loadDetail(selectedSummary.publicId)">重试</el-button>
        </div>

        <dl v-else class="detail__meta">
          <div><dt>类别</dt><dd>{{ selectedSummary.categoryName }}</dd></div>
          <div><dt>地点</dt><dd>{{ detailLocation }}</dd></div>
          <div><dt>时间</dt><dd>{{ formatEventTime(selectedSummary.eventTime) }}</dd></div>
          <div><dt>发布者</dt><dd>{{ selectedSummary.publisherName }}</dd></div>
          <div v-if="selectedDetail?.brand"><dt>品牌</dt><dd>{{ selectedDetail.brand }}</dd></div>
          <div v-if="selectedDetail?.color"><dt>颜色</dt><dd>{{ selectedDetail.color }}</dd></div>
          <div v-if="selectedDetail">
            <dt>联系</dt>
            <dd>{{ contactModeLabels[selectedDetail.contactMode] }}</dd>
          </div>
          <div><dt>记录编号</dt><dd>{{ selectedSummary.publicId }}</dd></div>
        </dl>

        <div v-if="!detailLoading" class="detail__matches">
          <div class="detail__matches-head">
            <h3>可能的匹配</h3>
            <el-tag v-if="!matchesLoading && matches.length" size="small" type="success">{{ matches.length }} 条候选</el-tag>
          </div>
          <p v-if="matchesLoading" class="detail__matches-hint">正在检索相反类型与同类别记录…</p>
          <p v-else-if="!matches.length" class="detail__matches-hint">
            暂时没有相似记录。发布后系统会在匹配到候选时通过站内通知提醒你。
          </p>
          <ul v-else class="detail__matches-list">
            <li v-for="match in matches" :key="match.publicId">
              <div class="detail__match-main">
                <el-tag size="small" :type="match.type === 'LOST' ? 'warning' : 'success'" effect="plain">
                  {{ match.type === 'LOST' ? '寻物' : '招领' }}
                </el-tag>
                <span class="detail__match-title">{{ match.title }}</span>
                <span class="detail__match-score">相似度 {{ match.score }}</span>
              </div>
              <p class="detail__match-reasons">{{ match.reasons.join(' · ') }}</p>
            </li>
          </ul>
        </div>

        <div v-if="!visualLoading && visualMatches.length" class="detail__visual">
          <div class="detail__matches-head">
            <h3>外观相似</h3>
            <el-tag size="small" type="info">图片比对</el-tag>
          </div>
          <ul class="detail__visual-list">
            <li v-for="item in visualMatches" :key="item.publicId">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" loading="lazy" />
              <div class="detail__visual-main">
                <span class="detail__match-title">{{ item.title }}</span>
                <span class="detail__match-score">相似度 {{ item.score.toFixed(2) }}</span>
              </div>
            </li>
          </ul>
        </div>

        <p v-if="!detailLoading && !detailError" class="detail__note">
          {{ canClaim ? '如果这是你丢失的物品，可以发起认领：写清只有失主知道的细节，发布者确认后线下交接。' : '该记录已完成认领或已关闭，如需协助请联系发布者所在服务点。' }}
        </p>
        <div class="detail__actions">
          <el-button v-if="canClaim" type="primary" class="detail__action" @click="startClaim">
            {{ auth.user ? '我要认领' : '登录后认领' }}
          </el-button>
          <el-button class="detail__action" @click="detailOpen = false">关闭</el-button>
        </div>
      </div>
    </el-drawer>

    <ClaimDialog v-model="claimOpen" :post="claimTarget" @submitted="onClaimSubmitted" />
  </div>
</template>
