<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from 'lucide-vue-next'
import ItemCard from '@/components/ItemCard.vue'
import ServiceStatusPanel from '@/components/ServiceStatusPanel.vue'
import { demoItems } from '@/data/demoItems'
import { itemKindLabels, itemStatusLabels, type ItemKind, type LostItem } from '@/types/item'

const route = useRoute()

const keyword = ref('')
const category = ref('all')
const selected = ref<LostItem | null>(null)

const categories = [
  { value: 'all', label: '全部类别' },
  { value: '证件', label: '证件' },
  { value: '电子产品', label: '电子产品' },
  { value: '钥匙', label: '钥匙' },
  { value: '书籍', label: '书籍' },
  { value: '衣物', label: '衣物' },
  { value: '其他', label: '其他' },
]

const activeKind = computed<ItemKind | 'all'>(() => {
  const kind = route.query.kind
  return kind === 'lost' || kind === 'found' ? kind : 'all'
})

const filtered = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return demoItems.filter((item) => {
    if (activeKind.value !== 'all' && item.kind !== activeKind.value) {
      return false
    }
    if (category.value !== 'all' && item.category !== category.value) {
      return false
    }
    if (!text) {
      return true
    }
    return [item.title, item.description, item.location, item.category, item.publisher]
      .join(' ')
      .toLowerCase()
      .includes(text)
  })
})

const headline = computed(() => {
  if (activeKind.value === 'lost') {
    return '寻物启事'
  }
  if (activeKind.value === 'found') {
    return '失物招领'
  }
  return '最新记录'
})

const openFound = computed(() => demoItems.filter((item) => item.kind === 'found' && item.status !== 'closed').length)
const openLost = computed(() => demoItems.filter((item) => item.kind === 'lost' && item.status !== 'closed').length)
const returned = computed(() => demoItems.filter((item) => item.status === 'closed').length)

const detailOpen = computed({
  get: () => selected.value !== null,
  set: (value: boolean) => {
    if (!value) {
      selected.value = null
    }
  },
})

function openDetail(item: LostItem): void {
  selected.value = item
}
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
          <dt>待认领</dt>
          <dd>{{ openFound }}</dd>
        </div>
        <div>
          <dt>寻物进行中</dt>
          <dd>{{ openLost }}</dd>
        </div>
        <div>
          <dt>已归还</dt>
          <dd>{{ returned }}</dd>
        </div>
      </dl>
    </section>

    <div class="workspace">
      <main class="feed">
        <div class="toolbar">
          <el-input
            v-model="keyword"
            class="toolbar__search"
            placeholder="搜索物品、地点或描述"
            clearable
            :prefix-icon="Search"
          />
          <el-select v-model="category" class="toolbar__select" aria-label="按类别筛选">
            <el-option v-for="option in categories" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>

        <div class="feed__head">
          <h2>{{ headline }}</h2>
          <span class="demo-note">演示数据 · 接口接入后替换</span>
        </div>

        <div v-if="filtered.length" class="item-list">
          <ItemCard v-for="item in filtered" :key="item.id" :item="item" @open="openDetail" />
        </div>
        <p v-else class="feed__empty">没有匹配的记录，换个关键词或类别试试。</p>
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
      <div v-if="selected" class="detail">
        <div class="detail__tags">
          <span class="kind-tag" :class="`kind-tag--${selected.kind}`">{{ itemKindLabels[selected.kind] }}</span>
          <span class="item-status" :class="`item-status--${selected.status}`">{{ itemStatusLabels[selected.status] }}</span>
        </div>
        <h2 class="detail__title">{{ selected.title }}</h2>
        <p class="detail__desc">{{ selected.description }}</p>

        <dl class="detail__meta">
          <div><dt>类别</dt><dd>{{ selected.category }}</dd></div>
          <div><dt>地点</dt><dd>{{ selected.location }}</dd></div>
          <div><dt>时间</dt><dd>{{ selected.happenedAt }}</dd></div>
          <div><dt>发布者</dt><dd>{{ selected.publisher }}</dd></div>
          <div><dt>核验方式</dt><dd>{{ selected.contactHint }}</dd></div>
          <div><dt>记录编号</dt><dd>{{ selected.id }}</dd></div>
        </dl>

        <p class="detail__note">线上认领与站内消息在迭代一开放。当前阶段请到对应值班点现场核验。</p>
        <el-button type="primary" class="detail__action" @click="detailOpen = false">关闭</el-button>
      </div>
    </el-drawer>
  </div>
</template>

